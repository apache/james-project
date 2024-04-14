/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap;

import static io.netty.handler.codec.http.HttpHeaderNames.ACCEPT;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE_UTF8;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.is;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerResponse;

class JMAPServerTest {
    private static final Version DRAFT = new Version("draft");
    private static final String ACCEPT_JMAP_VERSION_HEADER = "application/json; jmapVersion=";
    private static final String ACCEPT_DRAFT_VERSION_HEADER = ACCEPT_JMAP_VERSION_HEADER + DRAFT.asString();
    private static final String ACCEPT_RFC8621_VERSION_HEADER = ACCEPT_JMAP_VERSION_HEADER + Version.RFC8621.asString();

    private static final JMAPConfiguration DISABLED_CONFIGURATION = JMAPConfiguration.builder().disable().build();
    private static final JMAPConfiguration TEST_CONFIGURATION = JMAPConfiguration.builder()
        .enable()
        .randomPort()
        .build();
    private static final ImmutableSet<JMAPRoutesHandler> NO_ROUTES_HANDLERS = ImmutableSet.of();

    private static final ImmutableSet<Endpoint> AUTHENTICATION_ENDPOINTS = ImmutableSet.of(
        new Endpoint(HttpMethod.POST, JMAPUrls.AUTHENTICATION),
        new Endpoint(HttpMethod.GET, JMAPUrls.AUTHENTICATION)
    );
    private static final ImmutableSet<Endpoint> JMAP_ENDPOINTS = ImmutableSet.of(
        new Endpoint(HttpMethod.POST, JMAPUrls.JMAP),
        new Endpoint(HttpMethod.DELETE, JMAPUrls.JMAP)
    );

    private static final ImmutableSet<JMAPRoutesHandler> FAKE_ROUTES_HANDLERS = ImmutableSet.of(
        new JMAPRoutesHandler(
            DRAFT,
            new FakeJMAPRoutes(AUTHENTICATION_ENDPOINTS, DRAFT),
            new FakeJMAPRoutes(JMAP_ENDPOINTS, DRAFT)),
        new JMAPRoutesHandler(
            Version.RFC8621,
            new FakeJMAPRoutes(AUTHENTICATION_ENDPOINTS, Version.RFC8621))
    );

    private static final ImmutableSet<JMAPRoutesHandler> CORS_ROUTES = ImmutableSet.of(
        new JMAPRoutesHandler(
            DRAFT,
            new FakeJMAPRoutes(ImmutableSet.of(Endpoint.ofFixedPath(HttpMethod.OPTIONS, "/a")), DRAFT)),
        new JMAPRoutesHandler(
            Version.RFC8621,
            new FakeJMAPRoutes(ImmutableSet.of(new Endpoint(HttpMethod.OPTIONS, "/b")), Version.RFC8621)));

    private static final ImmutableSet<Version> SUPPORTED_VERSIONS = ImmutableSet.of(
        DRAFT,
        Version.RFC8621
    );

    @Test
    void serverShouldAnswerWhenStarted() {
        VersionParser versionParser = new VersionParser(SUPPORTED_VERSIONS, JMAPConfiguration.DEFAULT);
        JMAPServer jmapServer = new JMAPServer(TEST_CONFIGURATION, NO_ROUTES_HANDLERS, versionParser);
        jmapServer.start();

        try {
            given()
                .port(jmapServer.getPort().getValue())
                .basePath("http://localhost")
            .when()
                .get()
            .then()
                .statusCode(404);
        } finally {
            jmapServer.stop();
        }
    }

    @Test
    void startShouldNotThrowWhenConfigurationDisabled() {
        VersionParser versionParser = new VersionParser(SUPPORTED_VERSIONS, JMAPConfiguration.DEFAULT);
        JMAPServer jmapServer = new JMAPServer(DISABLED_CONFIGURATION, NO_ROUTES_HANDLERS, versionParser);

        assertThatCode(jmapServer::start).doesNotThrowAnyException();
    }

    @Test
    void stopShouldNotThrowWhenConfigurationDisabled() {
        VersionParser versionParser = new VersionParser(SUPPORTED_VERSIONS, JMAPConfiguration.DEFAULT);
        JMAPServer jmapServer = new JMAPServer(DISABLED_CONFIGURATION, NO_ROUTES_HANDLERS, versionParser);
        jmapServer.start();

        assertThatCode(jmapServer::stop).doesNotThrowAnyException();
    }

    @Test
    void getPortShouldThrowWhenServerIsNotStarted() {
        VersionParser versionParser = new VersionParser(SUPPORTED_VERSIONS, JMAPConfiguration.DEFAULT);
        JMAPServer jmapServer = new JMAPServer(TEST_CONFIGURATION, NO_ROUTES_HANDLERS, versionParser);

        assertThatThrownBy(jmapServer::getPort)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getPortShouldThrowWhenDisabledConfiguration() {
        VersionParser versionParser = new VersionParser(SUPPORTED_VERSIONS, JMAPConfiguration.DEFAULT);
        JMAPServer jmapServer = new JMAPServer(DISABLED_CONFIGURATION, NO_ROUTES_HANDLERS, versionParser);
        jmapServer.start();

        assertThatThrownBy(jmapServer::getPort)
            .isInstanceOf(IllegalStateException.class);
    }

    @Nested
    class RouteVersioningTest {
        JMAPServer server;

        @BeforeEach
        void setUp() {
            VersionParser versionParser = new VersionParser(SUPPORTED_VERSIONS, JMAPConfiguration.DEFAULT);
            server = new JMAPServer(TEST_CONFIGURATION, FAKE_ROUTES_HANDLERS, versionParser);
            server.start();

            RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .setPort(server.getPort().getValue())
                .build();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void serverShouldReturnDefaultVersionRouteWhenNoVersionHeader() {
            given()
                .basePath(JMAPUrls.AUTHENTICATION)
            .when()
                .get()
            .then()
                .statusCode(HttpResponseStatus.OK.code())
                .body("Version", is(Version.RFC8621.asString()));
        }

        @Test
        void serverShouldReturnCorrectRouteWhenTwoVersionRoutes() {
            given()
                .basePath(JMAPUrls.AUTHENTICATION)
                .header(ACCEPT.toString(), ACCEPT_RFC8621_VERSION_HEADER)
            .when()
                .get()
            .then()
                .statusCode(HttpResponseStatus.OK.code())
                .body("Version", is(Version.RFC8621.asString()));
        }

        @Test
        void serverShouldReturnCorrectRouteWhenOneVersionRoute() {
            given()
                .basePath(JMAPUrls.JMAP)
                .header(ACCEPT.toString(), ACCEPT_DRAFT_VERSION_HEADER)
            .when()
                .post()
            .then()
                .statusCode(HttpResponseStatus.OK.code())
                .body("Version", is(DRAFT.asString()));
        }

        @Test
        void serverShouldReturnNotFoundWhenRouteVersionDoesNotExist() {
            given()
                .basePath(JMAPUrls.JMAP)
                .header(ACCEPT.toString(), ACCEPT_RFC8621_VERSION_HEADER)
            .when()
                .post()
            .then()
                .statusCode(HttpResponseStatus.NOT_FOUND.code());
        }

        @Test
        void serverShouldReturnBadRequestWhenVersionIsUnknown() {
            given()
                .basePath(JMAPUrls.AUTHENTICATION)
                .header(ACCEPT.toString(), ACCEPT_JMAP_VERSION_HEADER + "unknown")
            .when()
                .get()
            .then()
                .statusCode(HttpResponseStatus.BAD_REQUEST.code());
        }
    }

    @Nested
    class CorsRouteVersioningTest {
        JMAPServer server;

        @BeforeEach
        void setUp() {
            VersionParser versionParser = new VersionParser(SUPPORTED_VERSIONS, JMAPConfiguration.DEFAULT);
            server = new JMAPServer(TEST_CONFIGURATION, CORS_ROUTES, versionParser);
            server.start();

            RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .setPort(server.getPort().getValue())
                .build();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void corsRoutesOfAllVersionsShouldBeExposed() {
            when()
                .options("/b")
            .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT")
                .header("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept")
                .header("Access-Control-Max-Age", "86400");

            when()
                .options("/a")
            .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT")
                .header("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept")
                .header("Access-Control-Max-Age", "86400");
        }
    }

    private static class FakeJMAPRoutes implements JMAPRoutes {
        private final Set<Endpoint> endpoints;
        private final Version version;

        private FakeJMAPRoutes(Set<Endpoint> endpoints, Version version) {
            this.endpoints = endpoints;
            this.version = version;
        }

        @Override
        public Stream<JMAPRoute> routes() {
            return endpoints.stream()
                .map(endpoint -> JMAPRoute.builder()
                    .endpoint(endpoint)
                    .action((request, response) -> {
                        if (endpoint.getMethod().equals(HttpMethod.OPTIONS)) {
                            return JMAPRoutes.CORS_CONTROL.handleRequest(request, response);
                        }
                        return sendVersionResponse(response);
                    })
                    .noCorsHeaders());
        }

        private Mono<Void> sendVersionResponse(HttpServerResponse response) {
            return response.status(HttpResponseStatus.OK)
                .header(CONTENT_TYPE, JSON_CONTENT_TYPE_UTF8)
                .sendString(Mono.just(String.format("{\"Version\":\"%s\"}", version.asString())))
                .then();
        }
    }
}
