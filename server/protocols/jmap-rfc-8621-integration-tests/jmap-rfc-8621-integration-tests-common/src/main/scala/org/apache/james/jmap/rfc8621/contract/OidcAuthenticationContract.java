/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License. You may obtain a copy of the License at    *
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

package org.apache.james.jmap.rfc8621.contract;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.requestSpecification;
import static io.restassured.RestAssured.with;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.apache.james.jmap.rfc8621.contract.Fixture.AUTHORIZATION_HEADER;
import static org.apache.james.jmap.rfc8621.contract.Fixture.BOB;
import static org.apache.james.jmap.rfc8621.contract.Fixture.BOB_PASSWORD;
import static org.apache.james.jmap.rfc8621.contract.Fixture.DOMAIN;
import static org.apache.james.jmap.rfc8621.contract.Fixture.ECHO_REQUEST_OBJECT;
import static org.apache.james.jmap.rfc8621.contract.Fixture.baseRequestSpecBuilder;
import static org.apache.james.jmap.rfc8621.contract.Fixture.getHeadersWith;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.http.OidcAuthenticationStrategy;
import org.apache.james.jmap.oidc.JMAPOidcConfiguration;
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags;
import org.apache.james.jwt.introspection.IntrospectionEndpoint;
import org.apache.james.oidc.Aud;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;

import io.restassured.authentication.NoAuthScheme;
import io.restassured.http.Header;

public abstract class OidcAuthenticationContract {
    private static final String USERINFO_TOKEN_URI_PATH = "/oauth2/userinfo";
    private static final String INTROSPECT_TOKEN_URI_PATH = "/oauth2/introspect";
    private static final String EMAIL_CLAIM = "email";
    private static final String SID = "dT/8+UDx1lWp1bRZkdhbS1i6ZfYhf8+bWAZQs8p0T/c";
    private static final long TOKEN_EXPIRATION_TIME = Instant.now().plus(Duration.ofHours(1)).getEpochSecond();

    private static final ClientAndServer MOCK_SERVER = ClientAndServer.startClientAndServer(0);

    protected Header authHeader;

    protected static final Optional<List<String>> OIDC_AUTHENTICATION_STRATEGY =
        Optional.of(List.of(OidcAuthenticationStrategy.class.getSimpleName()));

    protected static JMAPOidcConfiguration oidcConfiguration() {
        return oidcConfiguration(List.of(new Aud("james"), new Aud("james-admin")));
    }

    protected static JMAPOidcConfiguration oidcConfiguration(List<Aud> audiences) {
        return JMAPOidcConfiguration.builder()
            .oidcEnabled(true)
            .oidcUserInfoUrl(Optional.of(getUserInfoTokenEndpoint()))
            .oidcIntrospectionEndpoint(Optional.of(new IntrospectionEndpoint(getIntrospectTokenEndpoint(), Optional.empty())))
            .oidcClaim(Optional.of(EMAIL_CLAIM))
            .oidcAudience(Optional.of(audiences))
            .build();
    }

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        authHeader = bearerHeader(UUID.randomUUID().toString());

        server.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN().asString());

        try {
            server.getProbe(DataProbeImpl.class)
                .fluent()
                .addUser(BOB().asString(), BOB_PASSWORD());
        } catch (AlreadyExistInUsersRepositoryException e) {
            // James integration tests reuse the server between methods.
        }

        requestSpecification = baseRequestSpecBuilder(server)
            .setAuth(new NoAuthScheme())
            .build();
    }

    @AfterEach
    void tearDown() {
        MOCK_SERVER.reset();
    }

    private static URL getUserInfoTokenEndpoint() {
        return toUrl(USERINFO_TOKEN_URI_PATH);
    }

    private static URL getIntrospectTokenEndpoint() {
        return toUrl(INTROSPECT_TOKEN_URI_PATH);
    }

    private static URL toUrl(String path) {
        try {
            return URI.create(String.format("http://127.0.0.1:%d%s", MOCK_SERVER.getLocalPort(), path)).toURL();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Header bearerHeader(String token) {
        return new Header(AUTHORIZATION_HEADER(), "Bearer " + token);
    }

    protected String primaryAudience() {
        return "james";
    }

    protected String secondaryAudience() {
        return "james-admin";
    }

    private void mockJsonResponse(String path, String response, int statusCode) {
        MOCK_SERVER
            .when(HttpRequest.request().withPath(path))
            .respond(HttpResponse.response()
                .withStatusCode(statusCode)
                .withHeader("Content-Type", "application/json")
                .withBody(response, StandardCharsets.UTF_8));
    }

    private void mockUserInfo(String email) {
        mockJsonResponse(USERINFO_TOKEN_URI_PATH, """
            {
              "sub": "james-user",
              "email": "%s",
              "sid": "%s",
              "name": "James User"
            }""".formatted(email, SID), SC_OK);
    }

    private void mockUserInfoWithoutSid(String email) {
        mockJsonResponse(USERINFO_TOKEN_URI_PATH, """
            {
              "sub": "james-user",
              "email": "%s",
              "name": "James User"
            }""".formatted(email), SC_OK);
    }

    private void mockIntrospection(String audience) {
        mockJsonResponse(INTROSPECT_TOKEN_URI_PATH, """
            {
              "exp": %d,
              "scope": "openid email profile",
              "client_id": "james",
              "active": true,
              "aud": "%s",
              "sub": "james-user",
              "sid": "%s",
              "iss": "https://sso.example.com"
            }""".formatted(TOKEN_EXPIRATION_TIME, audience, SID), SC_OK);
    }

    protected void mockValidToken(String email) {
        mockUserInfo(email);
        mockIntrospection(primaryAudience());
    }

    @Tag(CategoryTags.BASIC_FEATURE)
    @Test
    void shouldAuthenticateWithOidc() {
        mockValidToken(BOB().asString());

        given()
            .headers(getHeadersWith(authHeader))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_OK);
    }

    @Test
    void shouldAcceptAudArray() {
        mockUserInfo(BOB().asString());
        mockJsonResponse(INTROSPECT_TOKEN_URI_PATH, """
            {
              "exp": %d,
              "scope": "openid email profile",
              "client_id": "james",
              "active": true,
              "aud": ["%s"],
              "sub": "james-user",
              "sid": "%s",
              "iss": "https://sso.example.com"
            }""".formatted(TOKEN_EXPIRATION_TIME, primaryAudience(), SID), SC_OK);

        given()
            .headers(getHeadersWith(authHeader))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_OK);
    }

    @Tag(CategoryTags.BASIC_FEATURE)
    @Test
    void shouldRejectOutdatedToken() {
        mockUserInfo(BOB().asString());
        mockJsonResponse(INTROSPECT_TOKEN_URI_PATH, """
            {
              "exp": %d,
              "scope": "openid email profile",
              "client_id": "james",
              "active": true,
              "aud": "%s",
              "sub": "james-user",
              "sid": "%s",
              "iss": "https://sso.example.com"
            }""".formatted(Instant.now().minus(Duration.ofHours(1)).getEpochSecond(), primaryAudience(), SID), SC_OK);

        given()
            .headers(getHeadersWith(authHeader))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_UNAUTHORIZED);
    }

    @Tag(CategoryTags.BASIC_FEATURE)
    @Test
    void shouldRejectBadAudience() {
        mockUserInfo(BOB().asString());
        mockIntrospection("bad");

        given()
            .headers(getHeadersWith(authHeader))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_UNAUTHORIZED);
    }

    @Test
    void shouldAcceptOtherConfiguredAudience() {
        mockUserInfo(BOB().asString());
        mockIntrospection(secondaryAudience());

        given()
            .headers(getHeadersWith(authHeader))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_OK);
    }

    @Test
    void shouldAcceptNoSidInUserInfo() {
        mockUserInfoWithoutSid(BOB().asString());
        mockIntrospection(primaryAudience());

        given()
            .headers(getHeadersWith(authHeader))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_OK);
    }

    @Test
    void shouldAcceptNoSidInIntrospection() {
        mockUserInfo(BOB().asString());
        mockJsonResponse(INTROSPECT_TOKEN_URI_PATH, """
            {
              "exp": %d,
              "scope": "openid email profile",
              "client_id": "james",
              "active": true,
              "aud": "%s",
              "sub": "james-user",
              "iss": "https://sso.example.com"
            }""".formatted(TOKEN_EXPIRATION_TIME, primaryAudience()), SC_OK);

        given()
            .headers(getHeadersWith(authHeader))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_OK);
    }

    @Test
    void shouldAcceptMissingAudInIntrospectionResponseAndCacheIt() {
        mockUserInfo(BOB().asString());
        mockJsonResponse(INTROSPECT_TOKEN_URI_PATH, """
            {
              "exp": %d,
              "scope": "openid email profile",
              "client_id": "james",
              "active": true,
              "sub": "james-user",
              "sid": "%s",
              "iss": "https://sso.example.com"
            }""".formatted(TOKEN_EXPIRATION_TIME, SID), SC_OK);

        given()
            .headers(getHeadersWith(authHeader))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_OK);

        given()
            .headers(getHeadersWith(authHeader))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_OK);
    }

    @Test
    void shouldRejectWhenUserInfoFails() {
        mockIntrospection(primaryAudience());
        mockJsonResponse(USERINFO_TOKEN_URI_PATH, "unauthorized", SC_UNAUTHORIZED);

        given()
            .headers(getHeadersWith(authHeader))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_UNAUTHORIZED);
    }

    @Test
    void shouldRejectWhenIntrospectionFails() {
        mockUserInfo(BOB().asString());
        mockJsonResponse(INTROSPECT_TOKEN_URI_PATH, "unauthorized", SC_UNAUTHORIZED);

        given()
            .headers(getHeadersWith(authHeader))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_UNAUTHORIZED);
    }

    @Test
    void shouldCacheResponse() {
        mockValidToken(BOB().asString());

        for (int i = 0; i < 3; i++) {
            with()
                .headers(getHeadersWith(authHeader))
                .body(ECHO_REQUEST_OBJECT())
            .when()
                .post()
            .then()
                .statusCode(SC_OK);
        }

        MOCK_SERVER.verify(HttpRequest.request().withPath(INTROSPECT_TOKEN_URI_PATH), VerificationTimes.exactly(1));
        MOCK_SERVER.verify(HttpRequest.request().withPath(USERINFO_TOKEN_URI_PATH), VerificationTimes.exactly(1));
    }

    @Test
    void shouldNotShareCacheAcrossDifferentOidcTokens() {
        mockIntrospection(primaryAudience());
        mockUserInfo(BOB().asString());

        Header token1 = bearerHeader("token1");
        given()
            .headers(getHeadersWith(token1))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_OK);

        Header token2 = bearerHeader("token2");
        given()
            .headers(getHeadersWith(token2))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(SC_OK);

        MOCK_SERVER.verify(HttpRequest.request()
            .withPath(USERINFO_TOKEN_URI_PATH)
            .withHeader(AUTHORIZATION_HEADER(), token1.getValue()), VerificationTimes.exactly(1));
        MOCK_SERVER.verify(HttpRequest.request()
            .withPath(USERINFO_TOKEN_URI_PATH)
            .withHeader(AUTHORIZATION_HEADER(), token2.getValue()), VerificationTimes.exactly(1));
    }
}
