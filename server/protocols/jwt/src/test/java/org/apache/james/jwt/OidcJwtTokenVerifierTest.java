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

package org.apache.james.jwt;

import static org.apache.james.jwt.OidcTokenFixture.INTROSPECTION_RESPONSE;
import static org.apache.james.jwt.OidcTokenFixture.USERINFO_RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.jwt.introspection.IntrospectionEndpoint;
import org.apache.james.jwt.introspection.TokenIntrospectionException;
import org.apache.james.jwt.userinfo.UserInfoCheckException;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import reactor.core.publisher.Mono;

class OidcJwtTokenVerifierTest {

    private static final String JWKS_URI_PATH = "/auth/realms/realm1/protocol/openid-connect/certs";
    private static final String USERINFO_PATH = "/auth/realms/oidc/protocol/openid-connect/userinfo";

    private static final String INTROSPECTION_PATH = "/auth/realms/oidc/protocol/openid-connect/token/introspect";

    ClientAndServer mockServer;

    @BeforeEach
    public void setUp() {
        mockServer = ClientAndServer.startClientAndServer(0);
        mockServer
            .when(HttpRequest.request().withPath(JWKS_URI_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(OidcTokenFixture.JWKS_RESPONSE, StandardCharsets.UTF_8));
    }

    @AfterEach
    public void afterEach() {
        if (mockServer != null) {
            mockServer.close();
        }
    }

    @Test
    void verifyAndClaimShouldReturnClaimValueWhenValidTokenHasKid() {
        Optional<String> emailAddress = new OidcJwtTokenVerifier(configForClaim("email_address")).verifySignatureAndExtractClaim(OidcTokenFixture.VALID_TOKEN);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(emailAddress.isPresent()).isTrue();
            softly.assertThat(emailAddress.get()).isEqualTo("user@domain.org");
        });
    }

    @Test
    void verifyAndClaimShouldReturnClaimValueWhenValidTokenHasNotKid() {
        Optional<String> emailAddress = new OidcJwtTokenVerifier(configForClaim("email_address")).verifySignatureAndExtractClaim(OidcTokenFixture.VALID_TOKEN_HAS_NOT_KID);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(emailAddress.isPresent()).isTrue();
            softly.assertThat(emailAddress.get()).isEqualTo("user@domain.org");
        });
    }

    @Test
    void verifyAndClaimShouldReturnEmptyWhenValidTokenHasNotFoundKid() {
        assertThat(new OidcJwtTokenVerifier(configForClaim("email_address"))
            .verifySignatureAndExtractClaim(OidcTokenFixture.VALID_TOKEN_HAS_NOT_FOUND_KID))
            .isEmpty();
    }

    @Test
    void verifyAndClaimShouldReturnEmptyWhenClaimNameNotFound() {
        assertThat(new OidcJwtTokenVerifier(configForClaim("not_found"))
            .verifySignatureAndExtractClaim(OidcTokenFixture.VALID_TOKEN))
            .isEmpty();
    }


    @Test
    void verifyAndClaimShouldReturnEmptyWhenInvalidToken() {
        assertThat(new OidcJwtTokenVerifier(configForClaim("email_address"))
            .verifySignatureAndExtractClaim(OidcTokenFixture.INVALID_TOKEN))
            .isEmpty();
    }

    @Test
    void verifyWithUserinfoShouldFailWhenUserInfoEndpointNotReturnOKHttpStatus() {
        mockServer
            .when(HttpRequest.request().withPath(USERINFO_PATH))
            .respond(HttpResponse.response().withStatusCode(201));

        assertThatThrownBy(() -> Mono.from(new OidcJwtTokenVerifier(configForClaim("email_address"))
                .verifyWithUserinfo(OidcTokenFixture.VALID_TOKEN, getUserInfoEndpoint()))
            .block())
            .isInstanceOf(UserInfoCheckException.class)
            .hasMessageContaining("Error when check token by userInfo");
    }

    @Test
    void verifyWithUserinfoShouldFailWhenUserInfoEndpointReturnBadResponse() {
        mockServer
            .when(HttpRequest.request().withPath(USERINFO_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody("badResponse1", StandardCharsets.UTF_8));

        assertThatThrownBy(() -> Mono.from(new OidcJwtTokenVerifier(configForClaim("email_address"))
                .verifyWithUserinfo(OidcTokenFixture.VALID_TOKEN, getUserInfoEndpoint()))
            .block())
            .isInstanceOf(UserInfoCheckException.class)
            .hasMessageContaining("Error when check token by userInfo");
    }

    @Test
    void verifyWithUserinfoShouldReturnEmptyWhenClaimValueIsEmpty() {
        String userInfoResponse = "{" +
            "    \"sub\": \"a0d03864-12f7-4f0b-b732-699c27eff3e7\"," +
            "    \"email_verified\": false," +
            "    \"name\": \"User name 1\"," +
            "    \"email\": \"user1@example.com\"" +
            "}";

        mockServer
            .when(HttpRequest.request().withPath(USERINFO_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(userInfoResponse, StandardCharsets.UTF_8));

        assertThat(Mono.from(new OidcJwtTokenVerifier(configForClaim("preferred_username"))
                .verifyWithUserinfo(OidcTokenFixture.VALID_TOKEN, getUserInfoEndpoint()))
            .block())
            .isNull();
    }

    private OidcSASLConfiguration configForClaim(String claim) {
        try {
            return OidcSASLConfiguration.builder()
                .jwksURL(getJwksURL())
                .scope("email")
                .oidcConfigurationURL(new URL("https://whatever.nte"))
                .claim(claim)
                .build();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void verifyWithUserinfoShouldReturnClaimValueWhenPassCheckToken() {
        mockServer
            .when(HttpRequest.request().withPath(USERINFO_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(USERINFO_RESPONSE, StandardCharsets.UTF_8));

        assertThat(Mono.from(new OidcJwtTokenVerifier(configForClaim("email_address"))
                .verifyWithUserinfo(OidcTokenFixture.VALID_TOKEN, getUserInfoEndpoint()))
            .block())
            .isEqualTo("user@domain.org");
    }

    @Test
    void verifyWithUserinfoShouldReturnEmptyWhenINVALIDToken() {
        mockServer
            .when(HttpRequest.request().withPath(USERINFO_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(USERINFO_RESPONSE, StandardCharsets.UTF_8));

        assertThat(Mono.from(new OidcJwtTokenVerifier(configForClaim("email_address"))
                .verifyWithUserinfo(OidcTokenFixture.INVALID_TOKEN, getUserInfoEndpoint()))
            .block())
            .isNull();
    }

    @Test
    void verifyWithUserinfoShouldReturnEmptyWhenClaimValueIsNotMatch() {
        String userInfoResponse = "{" +
            "    \"sub\": \"a0d03864-12f7-4f0b-b732-699c27eff3e7\"," +
            "    \"email_verified\": false," +
            "    \"name\": \"User name 1\"," +
            "    \"preferred_username\": \"different1\"," +
            "    \"email\": \"user1@example.com\"" +
            "}";

        mockServer
            .when(HttpRequest.request().withPath(USERINFO_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(userInfoResponse, StandardCharsets.UTF_8));

        assertThat(Mono.from(new OidcJwtTokenVerifier(configForClaim("preferred_username")).verifyWithUserinfo(OidcTokenFixture.INVALID_TOKEN, getUserInfoEndpoint()))
            .block())
            .isNull();
    }

    @Test
    void verifyWithIntrospectionShouldFailWhenEndpointNotReturnOKHttpStatus() {
        mockServer
            .when(HttpRequest.request().withPath(INTROSPECTION_PATH))
            .respond(HttpResponse.response().withStatusCode(201));

        assertThatThrownBy(() -> Mono.from(new OidcJwtTokenVerifier(configForClaim("email_address"))
                    .verifyWithIntrospection(OidcTokenFixture.VALID_TOKEN, new IntrospectionEndpoint(getIntrospectionEndpoint(), Optional.empty())))
            .block())
            .isInstanceOf(TokenIntrospectionException.class)
            .hasMessageContaining("Error when introspecting token");
    }

    @Test
    void verifyWithIntrospectionShouldFailWhenEndpointReturnBadResponse() {
        mockServer
            .when(HttpRequest.request().withPath(INTROSPECTION_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody("badResponse1", StandardCharsets.UTF_8));

        assertThatThrownBy(() -> Mono.from(new OidcJwtTokenVerifier(configForClaim("email_address"))
                .verifyWithIntrospection(OidcTokenFixture.VALID_TOKEN, new IntrospectionEndpoint(getIntrospectionEndpoint(), Optional.empty())))
            .block())
            .isInstanceOf(TokenIntrospectionException.class)
            .hasMessageContaining("Error when introspecting token");
    }

    @Test
    void verifyWithIntrospectionInfoShouldFailWhenActivePropertyIsAbsent() {
        String introspectionResponse = "{" +
            "    \"exp\": 1669719841," +
            "    \"iat\": 1669719541," +
            "    \"aud\": \"account\"," +
            "    \"sub\": \"a0d03864-12f7-4f0b-b732-699c27eff3e7\"," +
            "    \"typ\": \"Bearer\"," +
            "    \"session_state\": \"42799d76-be33-4f24-bcec-fc0dbb5d126d\"," +
            "    \"preferred_username\": \"user1\"," +
            "    \"email\": \"user1@example.com\"," +
            "    \"scope\": \"profile email\"," +
            "    \"sid\": \"42799d76-be33-4f24-bcec-fc0dbb5d126d\"," +
            "    \"client_id\": \"james-thunderbird\"," +
            "    \"username\": \"user1\"" +
            "}";

        mockServer
            .when(HttpRequest.request().withPath(INTROSPECTION_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(introspectionResponse, StandardCharsets.UTF_8));

        assertThatThrownBy(() -> Mono.from(new OidcJwtTokenVerifier(configForClaim("email_address"))
                .verifyWithIntrospection(OidcTokenFixture.VALID_TOKEN, new IntrospectionEndpoint(getIntrospectionEndpoint(), Optional.empty())))
            .block())
            .isInstanceOf(TokenIntrospectionException.class)
            .hasMessageContaining("Error when introspecting token");
    }

    @Test
    void verifyWithIntrospectionInfoShouldFailWhenActiveIsFalse() {
        String introspectionResponse = "{" +
            "    \"exp\": 1669719841," +
            "    \"iat\": 1669719541," +
            "    \"aud\": \"account\"," +
            "    \"sub\": \"a0d03864-12f7-4f0b-b732-699c27eff3e7\"," +
            "    \"typ\": \"Bearer\"," +
            "    \"session_state\": \"42799d76-be33-4f24-bcec-fc0dbb5d126d\"," +
            "    \"preferred_username\": \"user1\"," +
            "    \"email\": \"user1@example.com\"," +
            "    \"scope\": \"profile email\"," +
            "    \"sid\": \"42799d76-be33-4f24-bcec-fc0dbb5d126d\"," +
            "    \"client_id\": \"james-thunderbird\"," +
            "    \"username\": \"user1\"," +
            "    \"active\": false," +
            "}";

        mockServer
            .when(HttpRequest.request().withPath(INTROSPECTION_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(introspectionResponse, StandardCharsets.UTF_8));

        assertThatThrownBy(() -> Mono.from(new OidcJwtTokenVerifier(configForClaim("email_address"))
                .verifyWithIntrospection(OidcTokenFixture.VALID_TOKEN, new IntrospectionEndpoint(getIntrospectionEndpoint(), Optional.empty())))
            .block())
            .isInstanceOf(TokenIntrospectionException.class)
            .hasMessageContaining("Error when introspecting token");
    }

    @Test
    void verifyWithIntrospectionShouldReturnClaimValueWhenPassCheckToken() {
        mockServer
            .when(HttpRequest.request().withPath(INTROSPECTION_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(INTROSPECTION_RESPONSE, StandardCharsets.UTF_8));

        assertThat(Mono.from(new OidcJwtTokenVerifier(configForClaim("email_address"))
                .verifyWithIntrospection(OidcTokenFixture.VALID_TOKEN, new IntrospectionEndpoint(getIntrospectionEndpoint(), Optional.empty())))
            .block())
            .isEqualTo("user@domain.org");
    }

    @Test
    void verifyWithIntrospectionShouldReturnEmptyWhenClaimValueIsNotMatch() {
        String introspectionResponse = "{" +
            "    \"exp\": 1669719841," +
            "    \"iat\": 1669719541," +
            "    \"aud\": \"account\"," +
            "    \"sub\": \"a0d03864-12f7-4f0b-b732-699c27eff3e7\"," +
            "    \"preferred_username\": \"difference1\"," +
            "    \"active\": true" +
            "}";
        mockServer
            .when(HttpRequest.request().withPath(INTROSPECTION_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(introspectionResponse, StandardCharsets.UTF_8));

        assertThat(Mono.from(new OidcJwtTokenVerifier(configForClaim("preferred_username"))
                .verifyWithIntrospection(OidcTokenFixture.VALID_TOKEN, new IntrospectionEndpoint(getIntrospectionEndpoint(), Optional.empty())))
            .block())
            .isNull();
    }

    @Test
    void verifyWithIntrospectionShouldReturnEmptyWhenClaimValueIsAbsent() {
        String introspectionResponse = "{" +
            "    \"exp\": 1669719841," +
            "    \"iat\": 1669719541," +
            "    \"aud\": \"account\"," +
            "    \"sub\": \"a0d03864-12f7-4f0b-b732-699c27eff3e7\"," +
            "    \"typ\": \"Bearer\"," +
            "    \"active\": true" +
            "}";
        mockServer
            .when(HttpRequest.request().withPath(INTROSPECTION_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(introspectionResponse, StandardCharsets.UTF_8));

        assertThat(Mono.from(new OidcJwtTokenVerifier(configForClaim("preferred_username"))
                .verifyWithIntrospection(OidcTokenFixture.VALID_TOKEN, new IntrospectionEndpoint(getIntrospectionEndpoint(), Optional.empty())))
            .block())
            .isNull();
    }

    @Test
    void verifyWithIntrospectionShouldReturnEmptyWhenINVALIDToken() {
        mockServer
            .when(HttpRequest.request().withPath(INTROSPECTION_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(INTROSPECTION_RESPONSE, StandardCharsets.UTF_8));

        assertThat(Mono.from(new OidcJwtTokenVerifier(configForClaim("email_address"))
                .verifyWithIntrospection(OidcTokenFixture.INVALID_TOKEN, new IntrospectionEndpoint(getIntrospectionEndpoint(), Optional.empty())))
            .block())
            .isNull();
    }

    private URL getJwksURL() {
        try {
            return new URL(String.format("http://127.0.0.1:%s%s", mockServer.getLocalPort(), JWKS_URI_PATH));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private URL getUserInfoEndpoint() {
        try {
            return new URL(String.format("http://127.0.0.1:%s%s", mockServer.getLocalPort(), USERINFO_PATH));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private URL getIntrospectionEndpoint() {
        try {
            return new URL(String.format("http://127.0.0.1:%s%s", mockServer.getLocalPort(), INTROSPECTION_PATH));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
