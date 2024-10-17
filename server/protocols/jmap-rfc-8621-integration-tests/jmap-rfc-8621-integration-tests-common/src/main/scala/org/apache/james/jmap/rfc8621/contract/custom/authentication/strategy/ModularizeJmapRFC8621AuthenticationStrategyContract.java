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

package org.apache.james.jmap.rfc8621.contract.custom.authentication.strategy;

import static io.netty.handler.codec.http.HttpHeaders.Names.ACCEPT;
import static io.restassured.RestAssured.given;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.jmap.JMAPUrls.JMAP;
import static org.apache.james.jmap.rfc8621.contract.Fixture.ACCEPT_RFC8621_VERSION_HEADER;
import static org.apache.james.jmap.rfc8621.contract.Fixture.AUTHORIZATION_HEADER;
import static org.apache.james.jmap.rfc8621.contract.Fixture.BOB;
import static org.apache.james.jmap.rfc8621.contract.Fixture.BOB_BASIC_AUTH_HEADER;
import static org.apache.james.jmap.rfc8621.contract.Fixture.BOB_PASSWORD;
import static org.apache.james.jmap.rfc8621.contract.Fixture.DOMAIN;
import static org.apache.james.jmap.rfc8621.contract.Fixture.ECHO_REQUEST_OBJECT;
import static org.apache.james.jmap.rfc8621.contract.Fixture.ECHO_RESPONSE_OBJECT;
import static org.apache.james.jmap.rfc8621.contract.Fixture.INVALID_JWT_TOKEN;
import static org.apache.james.jmap.rfc8621.contract.Fixture.UNKNOWN_USER_TOKEN;
import static org.apache.james.jmap.rfc8621.contract.Fixture.USER_TOKEN;
import static org.apache.james.jmap.rfc8621.contract.Fixture.getHeadersWith;
import static org.apache.james.jmap.rfc8621.contract.Fixture.toBase64;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.jmap.core.JmapRfc8621Configuration;
import org.apache.james.jmap.http.XUserAuthenticationStrategy;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import io.restassured.RestAssured;
import io.restassured.http.Header;

public abstract class ModularizeJmapRFC8621AuthenticationStrategyContract {

    public static Optional<List<String>> ALLOW_AUTHENTICATION_STRATEGY = Optional.of(List.of(AllowAuthenticationStrategy.class.getCanonicalName()));
    public static Optional<List<String>> DENY_AUTHENTICATION_STRATEGY = Optional.of(List.of(DenyAuthenticationStrategy.class.getCanonicalName()));
    public static Optional<List<String>> DEFAULT_STRATEGIES = Optional.empty();

    private GuiceJamesServer jmapServer;

    public void setupJamesServerWithCustomAuthenticationStrategy(GuiceJamesServer basedServer, Optional<List<String>> authOverride) throws Throwable {
        jmapServer = createJmapServer(basedServer, authOverride);

        jmapServer.start();

        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort().getValue())
            .setBasePath(JMAP)
            .build();

        jmapServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN().asString())
            .addUser(BOB().asString(), BOB_PASSWORD());
    }

    private GuiceJamesServer createJmapServer(GuiceJamesServer basedServer, Optional<List<String>> authOverride) {
        return basedServer
            .overrideWith(binder -> binder.bind(JmapRfc8621Configuration.class)
                .toInstance(JmapRfc8621Configuration.LOCALHOST_CONFIGURATION()
                    .withAuthenticationStrategies(authOverride)));
    }

    @AfterEach
    public void teardown() {
        jmapServer.stop();
    }

    @Test
    public void givenAllowAuthenticationStrategyWhenEchoMethodShouldSucceedWithoutAuthentication(GuiceJamesServer server) throws Throwable {
        setupJamesServerWithCustomAuthenticationStrategy(server, ALLOW_AUTHENTICATION_STRATEGY);

        String response = given()
            .header(ACCEPT, ACCEPT_RFC8621_VERSION_HEADER())
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_OK)
            .extract()
            .body()
            .asString();

        assertThatJson(response).isEqualTo(ECHO_RESPONSE_OBJECT());
    }

    @Test
    public void givenDenyAuthenticationStrategyWhenEchoMethodShouldReturnUnauthorizedCode(GuiceJamesServer server) throws Throwable {
        setupJamesServerWithCustomAuthenticationStrategy(server, DENY_AUTHENTICATION_STRATEGY);

        given()
            .header(ACCEPT, ACCEPT_RFC8621_VERSION_HEADER())
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_UNAUTHORIZED)
            .body("status", equalTo(401))
            .body("type", equalTo("about:blank"))
            .body("detail", equalTo("No valid authentication methods provided"));
    }

    @Test
    public void givenDenyAuthenticationStrategyWhenEchoMethodWithValidJWTShouldReturnUnauthorizedCode(GuiceJamesServer server) throws Throwable {
        setupJamesServerWithCustomAuthenticationStrategy(server, DENY_AUTHENTICATION_STRATEGY);

        given()
            .headers(getHeadersWith(new Header(AUTHORIZATION_HEADER(),"Bearer " + USER_TOKEN())))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_UNAUTHORIZED)
            .body("status", equalTo(401))
            .body("type", equalTo("about:blank"))
            .body("detail", equalTo("No valid authentication methods provided"));
    }

    @Test
    public void givenDefaultStrategiesWhenEchoMethodWithoutAuthenticationShouldFail(GuiceJamesServer server) throws Throwable {
        setupJamesServerWithCustomAuthenticationStrategy(server, DEFAULT_STRATEGIES);

        given()
            .header(ACCEPT, ACCEPT_RFC8621_VERSION_HEADER())
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_UNAUTHORIZED)
            .body("status", equalTo(401))
            .body("type", equalTo("about:blank"))
            .body("detail", equalTo("No valid authentication methods provided"));
    }

    @Test
    public void givenDefaultStrategiesWhenEchoMethodWithValidBasicAuthenticationShouldSucceed(GuiceJamesServer server) throws Throwable {
        setupJamesServerWithCustomAuthenticationStrategy(server, DEFAULT_STRATEGIES);

        given()
            .headers(getHeadersWith(BOB_BASIC_AUTH_HEADER()))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_OK);
    }

    @Test
    public void givenDefaultStrategiesWhenEchoMethodWithInvalidBasicAuthenticationShouldFail(GuiceJamesServer server) throws Throwable {
        setupJamesServerWithCustomAuthenticationStrategy(server, DEFAULT_STRATEGIES);
        Header authHeader = new Header(AUTHORIZATION_HEADER(), "Basic " + toBase64(BOB().asString() + ":WRONG_PASSWORD"));

        given()
            .headers(getHeadersWith(authHeader))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_UNAUTHORIZED)
            .body("status", equalTo(401))
            .body("type", equalTo("about:blank"))
            .body("detail", equalTo("Wrong credentials provided"));
    }

    @Test
    public void givenDefaultStrategiesWhenEchoMethodWithValidJWTAuthenticationShouldSucceed(GuiceJamesServer server) throws Throwable {
        setupJamesServerWithCustomAuthenticationStrategy(server, DEFAULT_STRATEGIES);

        given()
            .headers(getHeadersWith(new Header(AUTHORIZATION_HEADER(),"Bearer " + USER_TOKEN())))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_OK);
    }

    @Test
    public void givenDefaultStrategiesWhenEchoMethodWithValidUnknownUserJWTAuthenticationShouldFail(GuiceJamesServer server) throws Throwable {
        setupJamesServerWithCustomAuthenticationStrategy(server, DEFAULT_STRATEGIES);

        given()
            .headers(getHeadersWith(new Header(AUTHORIZATION_HEADER(),"Bearer " + UNKNOWN_USER_TOKEN())))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_UNAUTHORIZED)
            .body("status", equalTo(401))
            .body("type", equalTo("about:blank"))
            .body("detail", equalTo("Failed Jwt verification"));
    }

    @Test
    public void givenDefaultStrategiesWhenEchoMethodWithInvalidJWTAuthenticationShouldFail(GuiceJamesServer server) throws Throwable {
        setupJamesServerWithCustomAuthenticationStrategy(server, DEFAULT_STRATEGIES);

        given()
            .headers(getHeadersWith(new Header(AUTHORIZATION_HEADER(),"Bearer " + INVALID_JWT_TOKEN())))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_UNAUTHORIZED)
            .body("status", equalTo(401))
            .body("type", equalTo("about:blank"))
            .body("detail", equalTo("Failed Jwt verification"));
    }


    @Test
    public void givenXUserStrategyWhenMissingXUserSecretHeaderShouldFail(GuiceJamesServer server) throws Throwable {
        // given a server with XUserAuthenticationStrategy
        // The XUserAuthenticationStrategy is configured with a secret: "secret1"
        setupJamesServerWithXUserStrategy(server, Optional.of(List.of("secret1")));

        // when a request is made without the X-User-Secret header
        // then the request should fail with a 401 status code
        given()
            .headers(getHeadersWith(new Header("X-User", BOB().asString())))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_UNAUTHORIZED)
            .body("status", equalTo(401))
            .body("type", equalTo("about:blank"));
    }

    @Test
    public void givenXUserStrategyWhenInvalidateXUserSecretHeaderShouldFail(GuiceJamesServer server) throws Throwable {
        // given a server with XUserAuthenticationStrategy
        // The XUserAuthenticationStrategy is configured with a secret: "secret1"
        setupJamesServerWithXUserStrategy(server, Optional.of(List.of("secret1")));

        // when a request is made with an invalid X-User-Secret header
        // then the request should fail with a 401 status code
        given()
            .header(new Header("X-User", BOB().asString()))
            .header(new Header("X-User-Secret", "invalid"))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_UNAUTHORIZED)
            .body("status", equalTo(401))
            .body("type", equalTo("about:blank"));
    }

    @Test
    public void givenXUserStrategyWhenValidateXUserSecretHeaderShouldSuccess(GuiceJamesServer server) throws Throwable {
        // given a server with XUserAuthenticationStrategy
        // The XUserAuthenticationStrategy is configured with a secret: "secret1"
        String secret = "secret1";
        setupJamesServerWithXUserStrategy(server, Optional.of(List.of(secret)));

        // when a request is made with an invalid X-User-Secret header
        // then the request should fail with a 401 status code
        given()
            .header(new Header("X-User", BOB().asString()))
            .header(new Header("X-User-Secret", secret))
        .when()
            .get("/session")
        .then()
            .statusCode(SC_OK)
            .body("username", equalTo(BOB().asString()));
    }

    @Test
    public void givenXUserStrategyWithAbsentXUserSecretWhenValidRequestShouldSuccess(GuiceJamesServer server) throws Throwable {
        // given a server with XUserAuthenticationStrategy
        // The XUserAuthenticationStrategy is configured with absent secret
        setupJamesServerWithXUserStrategy(server, Optional.empty());

        // when a request is made with an invalid X-User-Secret header
        // then the request should fail with a 401 status code
        given()
            .header(new Header("X-User", BOB().asString()))
            .when()
        .get("/session")
            .then()
            .statusCode(SC_OK)
            .body("username", equalTo(BOB().asString()));
    }

    @Test
    public void givenXUserStrategySupportListSecret(GuiceJamesServer server) throws Throwable {
        List<String> validatedSecretList = List.of("secret1", "secret2", "secret3");
        setupJamesServerWithXUserStrategy(server, Optional.of(validatedSecretList));

        // when a request is made with an invalid X-User-Secret header
        // then the request should fail with a 401 status code
        given()
            .header(new Header("X-User", BOB().asString()))
            .header(new Header("X-User-Secret", validatedSecretList.get(ThreadLocalRandom.current().nextInt(3))))
        .when()
            .get("/session")
        .then()
            .statusCode(SC_OK)
            .body("username", equalTo(BOB().asString()));
    }

    private void setupJamesServerWithXUserStrategy(GuiceJamesServer server, Optional<List<String>> xUserSecret) throws Exception {
        jmapServer = server
            .overrideWith(new AbstractModule() {
                @Provides
                @Singleton
                public XUserAuthenticationStrategy provideXUserAuthenticationStrategy(UsersRepository usersRepository,
                                                                                      MailboxManager mailboxManager) {
                    return new XUserAuthenticationStrategy(usersRepository, mailboxManager, xUserSecret);
                }
            })
            .overrideWith(binder -> binder.bind(JmapRfc8621Configuration.class)
                .toInstance(JmapRfc8621Configuration.LOCALHOST_CONFIGURATION()
                    .withAuthenticationStrategies(Optional.of(List.of(XUserAuthenticationStrategy.class.getCanonicalName())))));

        jmapServer.start();

        RestAssured.requestSpecification = jmapRequestSpecBuilder
            .setPort(jmapServer.getProbe(JmapGuiceProbe.class).getJmapPort().getValue())
            .addHeader("Accept", "application/json; jmapVersion=rfc-8621")
            .setBasePath(JMAP)
            .build();

        jmapServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN().asString())
            .addUser(BOB().asString(), BOB_PASSWORD());
    }
}
