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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Configuration of the MCP MPT server.
 *
 * <p>Read from environment variables, optionally completed by a JSON file whose path is given by
 * <code>MPT_MCP_CONFIG</code> (file values win). See README.md for the reference.</p>
 */
public record McpMptConfiguration(Map<String, Target> targets,
                                  Map<String, String> variables,
                                  boolean trustAllCertificates,
                                  Duration connectTimeout,
                                  Duration readTimeout,
                                  Duration defaultScriptTimeout,
                                  Duration maxScriptTimeout,
                                  int maxConcurrentRuns,
                                  int maxTranscriptLines,
                                  Transport transport,
                                  int httpPort,
                                  Optional<String> authToken) {

    public enum Transport {
        STDIO, HTTP
    }

    public static final String DEFAULT_HOST = "james";

    public static McpMptConfiguration fromEnvironment(Map<String, String> env) throws IOException {
        Map<String, Target> targets = new LinkedHashMap<>();
        Map<String, String> variables = new LinkedHashMap<>();

        String targetsValue = env.get("MPT_MCP_TARGETS");
        if (targetsValue != null && !targetsValue.isBlank()) {
            parseKeyValues(targetsValue).forEach((name, uri) -> targets.put(name, Target.parse(name, uri)));
        }
        String variablesValue = env.get("MPT_MCP_VARIABLES");
        if (variablesValue != null && !variablesValue.isBlank()) {
            variables.putAll(parseKeyValues(variablesValue));
        }

        boolean trustAll = Boolean.parseBoolean(env.getOrDefault("MPT_MCP_TRUST_ALL", "true"));
        Duration connectTimeout = seconds(env, "MPT_MCP_CONNECT_TIMEOUT", 10);
        Duration readTimeout = seconds(env, "MPT_MCP_READ_TIMEOUT", 10);
        Duration defaultScriptTimeout = seconds(env, "MPT_MCP_SCRIPT_TIMEOUT", 60);
        Duration maxScriptTimeout = seconds(env, "MPT_MCP_MAX_SCRIPT_TIMEOUT", 300);
        int maxConcurrentRuns = Integer.parseInt(env.getOrDefault("MPT_MCP_MAX_CONCURRENT_RUNS", "4"));
        int maxTranscriptLines = Integer.parseInt(env.getOrDefault("MPT_MCP_MAX_TRANSCRIPT_LINES", "2000"));
        Transport transport = Transport.valueOf(env.getOrDefault("MPT_MCP_TRANSPORT", "stdio").toUpperCase(Locale.US));
        int httpPort = Integer.parseInt(env.getOrDefault("MPT_MCP_HTTP_PORT", "8080"));
        Optional<String> authToken = Optional.ofNullable(env.get("MPT_MCP_AUTH_TOKEN")).filter(token -> !token.isBlank());

        String configFile = env.get("MPT_MCP_CONFIG");
        if (configFile != null && !configFile.isBlank()) {
            JsonNode json = new ObjectMapper().readTree(Files.readString(Path.of(configFile)));
            JsonNode targetsNode = json.path("targets");
            if (targetsNode.isArray()) {
                for (JsonNode node : targetsNode) {
                    Target target = parseTarget(node);
                    targets.put(target.name(), target);
                }
            }
            JsonNode variablesNode = json.path("variables");
            for (Iterator<Map.Entry<String, JsonNode>> it = variablesNode.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                variables.put(entry.getKey(), entry.getValue().asText());
            }
            trustAll = json.path("trustAllCertificates").asBoolean(trustAll);
            connectTimeout = Duration.ofSeconds(json.path("connectTimeoutSeconds").asLong(connectTimeout.toSeconds()));
            readTimeout = Duration.ofSeconds(json.path("readTimeoutSeconds").asLong(readTimeout.toSeconds()));
            defaultScriptTimeout = Duration.ofSeconds(json.path("scriptTimeoutSeconds").asLong(defaultScriptTimeout.toSeconds()));
            maxScriptTimeout = Duration.ofSeconds(json.path("maxScriptTimeoutSeconds").asLong(maxScriptTimeout.toSeconds()));
            maxConcurrentRuns = json.path("maxConcurrentRuns").asInt(maxConcurrentRuns);
            maxTranscriptLines = json.path("maxTranscriptLines").asInt(maxTranscriptLines);
        }

        if (targets.isEmpty()) {
            defaultTargets(env.getOrDefault("MPT_MCP_HOST", DEFAULT_HOST)).forEach(target -> targets.put(target.name(), target));
        }

        return new McpMptConfiguration(targets, variables, trustAll, connectTimeout, readTimeout,
            defaultScriptTimeout, maxScriptTimeout, maxConcurrentRuns, maxTranscriptLines, transport, httpPort, authToken);
    }

    public McpMptConfiguration {
        targets = Collections.unmodifiableMap(new LinkedHashMap<>(targets));
        variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        if (maxConcurrentRuns < 1) {
            throw new IllegalArgumentException("maxConcurrentRuns should be positive");
        }
    }

    static List<Target> defaultTargets(String host) {
        return List.of(
            new Target("smtps", Protocol.SMTP, host, 465, true, "SMTP submission, implicit TLS"),
            new Target("imaps", Protocol.IMAP, host, 993, true, "IMAP, implicit TLS"),
            new Target("pop3s", Protocol.POP3, host, 995, true, "POP3, implicit TLS"),
            new Target("managesieves", Protocol.MANAGESIEVE, host, 4190, true, "ManageSieve, implicit TLS"));
    }

    private static Target parseTarget(JsonNode node) {
        String name = node.path("name").asText(null);
        if (name == null) {
            throw new IllegalArgumentException("Target without name: " + node);
        }
        if (node.hasNonNull("uri")) {
            Target parsed = Target.parse(name, node.get("uri").asText());
            return new Target(name, parsed.protocol(), parsed.host(), parsed.port(), parsed.tls(), node.path("description").asText(""));
        }
        Protocol protocol = Protocol.fromName(node.path("protocol").asText(""))
            .orElseThrow(() -> new IllegalArgumentException("Unknown protocol for target " + name));
        boolean tls = node.path("tls").asBoolean(true);
        return new Target(name, protocol, node.path("host").asText(DEFAULT_HOST),
            node.path("port").asInt(protocol.defaultPort(tls)), tls, node.path("description").asText(""));
    }

    /**
     * Parses <code>a=b,c=d</code> (<code>;</code> is accepted as a separator too).
     */
    static Map<String, String> parseKeyValues(String value) {
        Map<String, String> result = new LinkedHashMap<>();
        Arrays.stream(value.split("[,;]"))
            .map(String::trim)
            .filter(entry -> !entry.isEmpty())
            .forEach(entry -> {
                int separator = entry.indexOf('=');
                if (separator <= 0) {
                    throw new IllegalArgumentException("Expecting key=value, got '" + entry + "'");
                }
                result.put(entry.substring(0, separator).trim(), entry.substring(separator + 1).trim());
            });
        return result;
    }

    private static Duration seconds(Map<String, String> env, String key, long defaultValue) {
        return Duration.ofSeconds(Long.parseLong(env.getOrDefault(key, String.valueOf(defaultValue))));
    }
}
