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

package org.apache.james.mpt.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpTransportTest {
    private HttpTransport transport;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws Exception {
        McpMptConfiguration configuration = new McpMptConfiguration(
            Map.of("fake", Target.parse("fake", "imap://127.0.0.1:1")), Map.of(), true,
            Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(5), 1, 100,
            McpMptConfiguration.Transport.HTTP, 0, Optional.of("secret"));
        transport = new HttpTransport(new McpHandler(new ScriptRunner(configuration)), 0, Optional.of("secret")).start();
    }

    @AfterEach
    void tearDown() {
        transport.stop();
    }

    private HttpResponse<String> post(String body, Optional<String> token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + transport.port() + "/mcp"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        token.ifPresent(value -> request.header("Authorization", "Bearer " + value));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void shouldAnswerJsonRpcRequests() throws Exception {
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}", Optional.of("secret"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Mcp-Session-Id")).isPresent();
        assertThat(response.body()).contains("\"serverInfo\"").contains("james-mcp-mpt");
    }

    @Test
    void notificationsShouldBeAccepted() throws Exception {
        assertThat(post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", Optional.of("secret")).statusCode())
            .isEqualTo(202);
    }

    @Test
    void shouldRejectMissingToken() throws Exception {
        assertThat(post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}", Optional.empty()).statusCode())
            .isEqualTo(401);
    }

    @Test
    void healthShouldBeServed() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + transport.port() + "/health")).build(),
            HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("OK");
    }
}
