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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ScriptRunnerTest {
    /**
     * Fake IMAP-like server: greeting, then answers "TAG anything" with "TAG OK anything".
     */
    private ServerSocket serverSocket;
    private ScriptRunner runner;

    @BeforeEach
    void setUp() throws Exception {
        serverSocket = new ServerSocket(0);
        Thread.ofVirtual().start(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    Thread.ofVirtual().start(() -> serve(socket));
                } catch (Exception e) {
                    return;
                }
            }
        });
        McpMptConfiguration configuration = new McpMptConfiguration(
            Map.of("fake", Target.parse("fake", "imap://127.0.0.1:" + serverSocket.getLocalPort())),
            Map.of("user", "bob"), true, Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(10),
            Duration.ofSeconds(20), 2, 100, McpMptConfiguration.Transport.STDIO, 0, Optional.empty());
        runner = new ScriptRunner(configuration);
    }

    private void serve(Socket socket) {
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, UTF_8)) {
            writer.print("* OK [UIDVALIDITY 7] ready\r\n");
            writer.flush();
            String line;
            while ((line = reader.readLine()) != null) {
                writer.print("* noise\r\n");
                writer.print(line.replaceFirst(" ", " OK ") + "\r\n");
                writer.flush();
            }
        } catch (Exception e) {
            // ignore
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        serverSocket.close();
    }

    private ScriptRunner.RunRequest request(String script) {
        return new ScriptRunner.RunRequest(script, Optional.empty(), Map.of(), Map.of(), false, Optional.empty());
    }

    @Test
    void passingScriptShouldBeReported() throws Exception {
        ScriptRunner.RunReport report = runner.run(request("""
            S: \\* OK \\[UIDVALIDITY (?<validity>\\d+)\\] ready
            C: A1 LOGIN ${user} ${validity}
            UNTIL: A1 OK LOGIN bob 7
            """));

        assertThat(report.status()).isEqualTo(ScriptRunner.Status.PASSED);
        assertThat(report.capturedVariables()).containsEntry("validity", "7").containsKey("runId");
        assertThat(report.transcript().format()).contains("[0] C: A1 LOGIN bob 7");
    }

    @Test
    void mismatchShouldFailWithLocation() throws Exception {
        ScriptRunner.RunReport report = runner.run(request("""
            S: \\* OK .*
            C: A1 NOOP
            S: A1 OK NOOP
            """));

        assertThat(report.status()).isEqualTo(ScriptRunner.Status.FAILED);
        assertThat(report.error()).hasValueSatisfying(error -> assertThat(error).contains("script:3").contains("* noise"));
    }

    @Test
    void multipleSessionsShouldBeSupported() throws Exception {
        ScriptRunner.RunReport report = runner.run(request("""
            SESSION: 0
            S: \\* OK .*
            SESSION: 1
            S: \\* OK .*
            C: B1 NOOP
            UNTIL: B1 OK NOOP
            """));

        assertThat(report.status()).isEqualTo(ScriptRunner.Status.PASSED);
        assertThat(report.sessionTargets()).containsOnlyKeys(0, 1);
    }

    @Test
    void unknownTargetShouldBeRejected() {
        assertThatThrownBy(() -> runner.run(new ScriptRunner.RunRequest("S: .*", Optional.of("nope"), Map.of(), Map.of(),
            false, Optional.empty())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown target 'nope'");
    }

    @Test
    void mcpHandlerShouldExposeToolsAndRunScripts() throws Exception {
        McpHandler handler = new McpHandler(runner);
        ObjectMapper mapper = new ObjectMapper();

        JsonNode tools = mapper.readTree(handler.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}").orElseThrow());
        assertThat(tools.at("/result/tools").findValuesAsText("name"))
            .containsExactly("list_targets", "get_mpt_syntax", "validate_script", "run_script");

        assertThat(handler.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")).isEmpty();

        String call = mapper.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/call",
            "params", Map.of("name", "run_script", "arguments", Map.of("script", "S: \\* OK .*\nC: A1 NOOP\nUNTIL: A1 OK NOOP\n"))));
        JsonNode result = mapper.readTree(handler.handle(call).orElseThrow());
        assertThat(result.at("/result/structuredContent/status").asText()).isEqualTo("PASSED");
        assertThat(result.at("/result/isError").asBoolean()).isFalse();

        String invalid = mapper.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 3, "method", "tools/call",
            "params", Map.of("name", "validate_script", "arguments", Map.of("script", "X: nope"))));
        JsonNode validation = mapper.readTree(handler.handle(invalid).orElseThrow());
        assertThat(validation.at("/result/structuredContent/valid").asBoolean()).isFalse();
        assertThat(validation.at("/result/structuredContent/error").asText()).contains("script:1");
    }
}
