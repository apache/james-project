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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

class OidcJwtTokenVerifierTest {

    private static final String JWKS_URI_PATH = "/auth/realms/realm1/protocol/openid-connect/certs";

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

    @Test
    void verifyAndClaimShouldReturnClaimValueWhenValidTokenHasKid() {
        Optional<String> email_address = OidcJwtTokenVerifier.verifySignatureAndExtractClaim(OidcTokenFixture.VALID_TOKEN, getJwksURL(), "email_address");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(email_address.isPresent()).isTrue();
            softly.assertThat(email_address.get()).isEqualTo("user@domain.org");
        });
    }

    @Test
    void verifyAndClaimShouldReturnClaimValueWhenValidTokenHasNotKid() {
        Optional<String> email_address = OidcJwtTokenVerifier.verifySignatureAndExtractClaim(OidcTokenFixture.VALID_TOKEN_HAS_NOT_KID, getJwksURL(), "email_address");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(email_address.isPresent()).isTrue();
            softly.assertThat(email_address.get()).isEqualTo("user@domain.org");
        });
    }

    @Test
    void verifyAndClaimShouldReturnEmptyWhenValidTokenHasNotFoundKid() {
        assertThat(OidcJwtTokenVerifier.verifySignatureAndExtractClaim(OidcTokenFixture.VALID_TOKEN_HAS_NOT_FOUND_KID, getJwksURL(), "email_address"))
            .isEmpty();
    }

    @Test
    void verifyAndClaimShouldReturnEmptyWhenClaimNameNotFound() {
        assertThat(OidcJwtTokenVerifier.verifySignatureAndExtractClaim(OidcTokenFixture.VALID_TOKEN, getJwksURL(), "not_found"))
            .isEmpty();
    }


    @Test
    void verifyAndClaimShouldReturnEmptyWhenInvalidToken() {
        assertThat(OidcJwtTokenVerifier.verifySignatureAndExtractClaim(OidcTokenFixture.INVALID_TOKEN, getJwksURL(), "email_address"))
            .isEmpty();
    }

    private URL getJwksURL() {
        try {
            return new URL(String.format("http://127.0.0.1:%s%s", mockServer.getLocalPort(), JWKS_URI_PATH));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
