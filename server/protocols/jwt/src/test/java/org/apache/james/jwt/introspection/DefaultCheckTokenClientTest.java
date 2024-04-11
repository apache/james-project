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

package org.apache.james.jwt.introspection;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.jwt.DefaultCheckTokenClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;

import reactor.core.publisher.Mono;

public class DefaultCheckTokenClientTest {
    private static final String INTROSPECTION_TOKEN_URI_PATH = "/token/introspect";

    private ClientAndServer mockServer;

    @BeforeEach
    public void setUp() {
        mockServer = ClientAndServer.startClientAndServer(0);
    }

    @AfterEach
    void tearDown() {
        mockServer.stop();
    }

    private IntrospectionEndpoint getIntrospectionTokenEndpoint() {
        try {
            return new IntrospectionEndpoint(new URI(String.format("http://127.0.0.1:%s%s", mockServer.getLocalPort(), INTROSPECTION_TOKEN_URI_PATH)).toURL(),
                Optional.empty());
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private DefaultCheckTokenClient testee() {
        return new DefaultCheckTokenClient();
    }

    private void updateMockerServerSpecifications(String response, int statusResponse) {
        mockServer
            .when(HttpRequest.request().withPath(INTROSPECTION_TOKEN_URI_PATH))
            .respond(HttpResponse.response().withStatusCode(statusResponse)
                .withHeader("Content-Type", "application/json")
                .withBody(response, StandardCharsets.UTF_8));
    }

    @Test
    void introspectShouldSuccessWhenValidRequest() {
        String activeResponse = "{" +
            "    \"exp\": 1652868271," +
            "    \"nbf\": 0," +
            "    \"iat\": 1652867971," +
            "    \"jti\": \"41ee3cc3-b908-4870-bff2-34b895b9fadf\"," +
            "    \"aud\": \"account\"," +
            "    \"typ\": \"Bearer\"," +
            "    \"acr\": \"1\"," +
            "    \"scope\": \"email\"," +
            "    \"active\": true" +
            "}";

        updateMockerServerSpecifications(activeResponse, 200);

        TokenIntrospectionResponse introspectionResponse = Mono.from(testee().introspect(getIntrospectionTokenEndpoint(), "abc"))
            .block();
        assertThat(introspectionResponse).isNotNull();

        assertSoftly(softly -> {
            softly.assertThat(introspectionResponse.active()).isTrue();
            softly.assertThat(introspectionResponse.scope()).isEqualTo(Optional.of("email"));
            assertThatJson(introspectionResponse.json().toString()).isEqualTo(activeResponse);
            softly.assertThat(introspectionResponse.exp()).isEqualTo(Optional.of(1652868271));
            softly.assertThat(introspectionResponse.nbf()).isEqualTo(Optional.of(0));
            softly.assertThat(introspectionResponse.iat()).isEqualTo(Optional.of(1652867971));
            softly.assertThat(introspectionResponse.jti()).isEqualTo(Optional.of("41ee3cc3-b908-4870-bff2-34b895b9fadf"));
            softly.assertThat(introspectionResponse.aud()).isEqualTo(Optional.of("account"));
            softly.assertThat(introspectionResponse.iss()).isEmpty();
            softly.assertThat(introspectionResponse.sub()).isEmpty();
        });
    }

    @Test
    void introspectShouldPostValidRequest() {
        String activeResponse = "{" +
            "    \"exp\": 1652868271," +
            "    \"nbf\": 0," +
            "    \"iat\": 1652867971," +
            "    \"jti\": \"41ee3cc3-b908-4870-bff2-34b895b9fadf\"," +
            "    \"aud\": \"account\"," +
            "    \"typ\": \"Bearer\"," +
            "    \"acr\": \"1\"," +
            "    \"scope\": \"email\"," +
            "    \"active\": true" +
            "}";

        updateMockerServerSpecifications(activeResponse, 200);

        Mono.from(testee().introspect(getIntrospectionTokenEndpoint(), "abc"))
            .block();
        mockServer.verify(HttpRequest.request()
                .withPath(INTROSPECTION_TOKEN_URI_PATH)
                .withMethod("POST")
                .withHeader("Accept", "application/json")
                .withHeader("Content-Type", "application/x-www-form-urlencoded")
                .withBody("token=abc"),
            VerificationTimes.atLeast(1));
    }

    @Test
    void introspectShouldFailWhenNotAuthorized() {
        String serverResponse = "{" +
            "    \"error\": \"invalid_request\"," +
            "    \"error_description\": \"Authentication failed.\"" +
            "}";

        updateMockerServerSpecifications(serverResponse, 401);

        assertThatThrownBy(() -> Mono.from(testee().introspect(getIntrospectionTokenEndpoint(), "abc"))
            .block())
            .isInstanceOf(TokenIntrospectionException.class)
            .hasMessageContaining("Authentication failed")
            .hasMessageContaining("401");
    }

    @Test
    void introspectShouldFailWhenCanNotDeserializeResponse() {
        String serverResponse = "invalid";

        updateMockerServerSpecifications(serverResponse, 200);

        assertThatThrownBy(() -> Mono.from(testee().introspect(getIntrospectionTokenEndpoint(), "abc"))
            .block())
            .isInstanceOf(TokenIntrospectionException.class)
            .hasMessageContaining("Error when introspecting token");
    }

    @Test
    void introspectShouldFailWhenResponseMissingActiveProperty() {
        String serverResponse = "{" +
            "    \"exp\": 1652868271," +
            "    \"nbf\": 0," +
            "    \"iat\": 1652867971," +
            "    \"jti\": \"41ee3cc3-b908-4870-bff2-34b895b9fadf\"," +
            "    \"aud\": \"account\"," +
            "    \"typ\": \"Bearer\"," +
            "    \"acr\": \"1\"," +
            "    \"scope\": \"email\"" +
            "}";

        updateMockerServerSpecifications(serverResponse, 200);

        assertThatThrownBy(() -> Mono.from(testee().introspect(getIntrospectionTokenEndpoint(), "abc"))
            .block())
            .isInstanceOf(TokenIntrospectionException.class)
            .hasMessageContaining("Error when introspecting token");
    }

    @Test
    void introspectShouldReturnUpdatedResponse() {
        String activeResponse = "{" +
            "    \"active\": true" +
            "}";

        updateMockerServerSpecifications(activeResponse, 200);
        DefaultCheckTokenClient testee = testee();
        String token = "token1bc";

        assertThat(Mono.from(testee.introspect(getIntrospectionTokenEndpoint(), token))
            .block()).isNotNull()
            .satisfies(x -> assertThat(x.active()).isTrue());

        String updatedResponse = "{" +
            "    \"active\": false" +
            "}";
        mockServer.reset();
        updateMockerServerSpecifications(updatedResponse, 200);

        assertThat(Mono.from(testee.introspect(getIntrospectionTokenEndpoint(), token))
            .block()).isNotNull()
            .satisfies(x -> assertThat(x.active()).isFalse());
    }

}
