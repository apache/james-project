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
import static org.apache.james.jwt.introspection.IntrospectionClientDefault.TokenIntrospectionConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import reactor.core.publisher.Mono;

public class IntrospectionClientDefaultTest {
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

    private URL getIntrospectionTokenEndpointURL() {
        try {
            return new URL(String.format("http://127.0.0.1:%s%s", mockServer.getLocalPort(), INTROSPECTION_TOKEN_URI_PATH));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void introspectShouldSuccessWhenValid() {
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

        mockServer
            .when(HttpRequest.request().withPath(INTROSPECTION_TOKEN_URI_PATH))
            .respond(HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(activeResponse, StandardCharsets.UTF_8));

        IntrospectionClientDefault client = new IntrospectionClientDefault(new TokenIntrospectionConfiguration(getIntrospectionTokenEndpointURL()));

        TokenIntrospectionResponse introspectionResponse = Mono.from(client.introspect("abc"))
            .block();
        assertThat(introspectionResponse).isNotNull();

        assertSoftly(softly -> {
            softly.assertThat(introspectionResponse.active()).isTrue();
            softly.assertThat(introspectionResponse.scope()).isEqualTo(Optional.of("email"));
            assertThatJson(introspectionResponse.json().toString()).isEqualTo(activeResponse);
        });
    }

    // TODO

}
