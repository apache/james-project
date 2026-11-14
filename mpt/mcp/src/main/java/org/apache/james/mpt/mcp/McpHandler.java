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

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Transport agnostic MCP (JSON-RPC 2.0) request handler exposing the MPT tools.
 */
public class McpHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(McpHandler.class);

    public static final List<String> SUPPORTED_PROTOCOL_VERSIONS = List.of("2025-06-18", "2025-03-26", "2024-11-05");
    public static final String SERVER_NAME = "james-mcp-mpt";

    static final int PARSE_ERROR = -32700;
    static final int INVALID_REQUEST = -32600;
    static final int METHOD_NOT_FOUND = -32601;
    static final int INVALID_PARAMS = -32602;
    static final int INTERNAL_ERROR = -32603;

    private final ObjectMapper mapper = new ObjectMapper();
    private final ScriptRunner runner;
    private final String syntax;
    private final Optional<AccountManager> accounts;

    public McpHandler(ScriptRunner runner) {
        this.runner = runner;
        this.syntax = loadSyntax();
        this.accounts = runner.configuration().webAdmin()
            .map(webAdmin -> new AccountManager(webAdmin, runner.configuration().variables()));
    }

    private static String loadSyntax() {
        try (InputStream stream = McpHandler.class.getResourceAsStream("/mpt-syntax.md")) {
            return new String(stream.readAllBytes(), UTF_8);
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("Missing mpt-syntax.md resource", e);
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    /**
     * Handles a raw JSON-RPC payload (single message or batch).
     *
     * @return the serialized response, or empty when nothing needs to be answered (notifications only)
     */
    public Optional<String> handle(String payload) {
        JsonNode message;
        try {
            message = mapper.readTree(payload);
        } catch (IOException e) {
            return Optional.of(write(error(null, PARSE_ERROR, "Parse error: " + e.getMessage())));
        }
        if (message == null) {
            return Optional.of(write(error(null, INVALID_REQUEST, "Empty request")));
        }
        if (message.isArray()) {
            ArrayNode responses = mapper.createArrayNode();
            message.forEach(item -> handle(item).ifPresent(responses::add));
            return responses.isEmpty() ? Optional.empty() : Optional.of(write(responses));
        }
        return handle(message).map(this::write);
    }

    private String write(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    Optional<JsonNode> handle(JsonNode message) {
        if (!message.isObject() || !message.hasNonNull("method")) {
            if (message.isObject() && (message.has("result") || message.has("error"))) {
                // response to a server request: we never send any
                return Optional.empty();
            }
            return Optional.of(error(message.get("id"), INVALID_REQUEST, "Invalid request"));
        }
        JsonNode id = message.get("id");
        boolean notification = id == null || id.isNull();
        String method = message.get("method").asText();
        JsonNode params = message.path("params");
        try {
            JsonNode result = switch (method) {
                case "initialize" -> initialize(params);
                case "ping" -> mapper.createObjectNode();
                case "tools/list" -> listTools();
                case "tools/call" -> callTool(params);
                case "resources/list" -> listResources();
                case "resources/read" -> readResource(params);
                case "prompts/list" -> mapper.createObjectNode().set("prompts", mapper.createArrayNode());
                default -> {
                    if (method.startsWith("notifications/")) {
                        yield null;
                    }
                    throw new JsonRpcException(METHOD_NOT_FOUND, "Method not found: " + method);
                }
            };
            if (notification) {
                return Optional.empty();
            }
            return Optional.of(response(id, result == null ? mapper.createObjectNode() : result));
        } catch (JsonRpcException e) {
            return notification ? Optional.empty() : Optional.of(error(id, e.code, e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("Error handling {}", method, e);
            return notification ? Optional.empty() : Optional.of(error(id, INTERNAL_ERROR, e.toString()));
        }
    }

    private JsonNode initialize(JsonNode params) {
        String requested = params.path("protocolVersion").asText("");
        String version = SUPPORTED_PROTOCOL_VERSIONS.contains(requested) ? requested : SUPPORTED_PROTOCOL_VERSIONS.get(0);
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", version);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools").put("listChanged", false);
        capabilities.putObject("resources").put("listChanged", false);
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", Optional.ofNullable(McpHandler.class.getPackage().getImplementationVersion()).orElse("dev"));
        result.put("instructions", "Run MPT (Mail Protocol Tester) scripts against the SMTP/IMAP/POP3/ManageSieve ports of an "
            + "Apache James server on a test bench. Call list_targets first, write a script, check it with validate_script, "
            + "then execute it with run_script and read the transcript.\n\n" + syntax);
        return result;
    }

    private JsonNode listResources() {
        ObjectNode result = mapper.createObjectNode();
        ObjectNode resource = result.putArray("resources").addObject();
        resource.put("uri", "mpt://syntax");
        resource.put("name", "MPT script syntax");
        resource.put("mimeType", "text/markdown");
        return result;
    }

    private JsonNode readResource(JsonNode params) throws JsonRpcException {
        if (!"mpt://syntax".equals(params.path("uri").asText())) {
            throw new JsonRpcException(INVALID_PARAMS, "Unknown resource " + params.path("uri").asText());
        }
        ObjectNode result = mapper.createObjectNode();
        ObjectNode content = result.putArray("contents").addObject();
        content.put("uri", "mpt://syntax");
        content.put("mimeType", "text/markdown");
        content.put("text", syntax);
        return result;
    }

    private JsonNode listTools() {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode tools = result.putArray("tools");

        ObjectNode listTargets = tool(tools, "list_targets",
            "List the test bench services (name, protocol, host, port, TLS) MPT scripts can run against, and the "
                + "predefined script variables (e.g. ${user}, ${password}).");
        listTargets.putObject("annotations").put("readOnlyHint", true);

        ObjectNode syntaxTool = tool(tools, "get_mpt_syntax", "Return the MPT script syntax reference with examples and tips.");
        syntaxTool.putObject("annotations").put("readOnlyHint", true);

        ObjectNode validate = tool(tools, "validate_script",
            "Parse an MPT script without connecting anywhere. Reports syntax errors (with line number) and the number of sessions used.");
        validate.putObject("annotations").put("readOnlyHint", true);
        ObjectNode validateProperties = (ObjectNode) validate.get("inputSchema").get("properties");
        validateProperties.putObject("script").put("type", "string").put("description", "The MPT script");
        ((ObjectNode) validate.get("inputSchema")).putArray("required").add("script");

        ObjectNode run = tool(tools, "run_script",
            "Run an MPT script against the test bench and check every server response. Returns PASSED/FAILED/ERROR/TIMEOUT, "
                + "the mismatches, captured variables and the full transcript. See get_mpt_syntax for the script format.");
        ObjectNode runProperties = (ObjectNode) run.get("inputSchema").get("properties");
        runProperties.putObject("script").put("type", "string").put("description", "The MPT script");
        runProperties.putObject("target").put("type", "string")
            .put("description", "Name of the target (see list_targets) used by every session not listed in 'sessions'. "
                + "Optional when a single target is configured.");
        ObjectNode sessions = runProperties.putObject("sessions");
        sessions.put("type", "object");
        sessions.put("description", "Per session target override, e.g. {\"0\": \"smtps\", \"1\": \"imaps\"}");
        sessions.putObject("additionalProperties").put("type", "string");
        ObjectNode variables = runProperties.putObject("variables");
        variables.put("type", "object");
        variables.put("description", "Extra ${name} variables, overriding the configured ones");
        variables.putObject("additionalProperties").put("type", "string");
        runProperties.putObject("continueAfterFailure").put("type", "boolean")
            .put("description", "Keep running after a mismatch and report all of them (default false)");
        runProperties.putObject("label").put("type", "string")
            .put("description", "Short label naming the run in the server side archive (when enabled)");
        runProperties.putObject("timeoutSeconds").put("type", "integer")
            .put("description", "Overall timeout (default " + runner.configuration().defaultScriptTimeout().toSeconds()
                + "s, max " + runner.configuration().maxScriptTimeout().toSeconds() + "s)");
        ((ObjectNode) run.get("inputSchema")).putArray("required").add("script");
        ObjectNode runAnnotations = run.putObject("annotations");
        runAnnotations.put("readOnlyHint", false);
        runAnnotations.put("destructiveHint", true);
        runAnnotations.put("openWorldHint", false);

        if (accounts.isPresent()) {
            ObjectNode create = tool(tools, "create_account",
                "Create an isolated account on the bench through WebAdmin (no Sieve script, subscription or mailbox attached). "
                    + "Returns username and password, to pass to run_script as variables {\"user\": ..., \"password\": ...}. "
                    + "Default: a random mpt-xxxx@" + accounts.get().defaultDomain().orElse("<domain>") + " account with a random password.");
            ObjectNode createProperties = (ObjectNode) create.get("inputSchema").get("properties");
            createProperties.putObject("username").put("type", "string").put("description", "Optional local part or full address");
            createProperties.putObject("password").put("type", "string").put("description", "Optional password");
            create.putObject("annotations").put("destructiveHint", false);

            ObjectNode delete = tool(tools, "delete_account", "Delete an account previously created by create_account.");
            ((ObjectNode) delete.get("inputSchema").get("properties")).putObject("username").put("type", "string");
            ((ObjectNode) delete.get("inputSchema")).putArray("required").add("username");
            delete.putObject("annotations").put("destructiveHint", true);
        }
        return result;
    }

    private ObjectNode tool(ArrayNode tools, String name, String description) {
        ObjectNode tool = tools.addObject();
        tool.put("name", name);
        tool.put("description", description);
        ObjectNode schema = tool.putObject("inputSchema");
        schema.put("type", "object");
        schema.putObject("properties");
        return tool;
    }

    private JsonNode callTool(JsonNode params) throws Exception {
        String name = params.path("name").asText("");
        JsonNode arguments = params.path("arguments");
        return switch (name) {
            case "list_targets" -> toolResult(listTargetsResult(), false);
            case "get_mpt_syntax" -> textResult(syntax, false);
            case "validate_script" -> validate(arguments);
            case "run_script" -> run(arguments);
            case "create_account" -> createAccount(arguments);
            case "delete_account" -> deleteAccount(arguments);
            default -> throw new JsonRpcException(INVALID_PARAMS, "Unknown tool: " + name);
        };
    }

    private ObjectNode listTargetsResult() {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode targets = result.putArray("targets");
        runner.configuration().targets().values().forEach(target -> {
            ObjectNode node = targets.addObject();
            node.put("name", target.name());
            node.put("protocol", target.protocol().name());
            node.put("host", target.host());
            node.put("port", target.port());
            node.put("tls", target.tls());
            node.put("uri", target.uri());
            node.put("description", target.description());
        });
        ObjectNode variables = result.putObject("variables");
        runner.configuration().variables().forEach(variables::put);
        variables.put("runId", "<random per run>");
        result.put("archive", runner.configuration().archiveDirectory().isPresent());
        result.put("accountManagement", accounts.isPresent());
        return result;
    }

    private JsonNode createAccount(JsonNode arguments) throws JsonRpcException {
        AccountManager manager = accounts.orElseThrow(() -> new JsonRpcException(INVALID_PARAMS, "Account management is not configured"));
        try {
            AccountManager.Account account = manager.create(optionalText(arguments, "username"), optionalText(arguments, "password"));
            ObjectNode result = mapper.createObjectNode();
            result.put("username", account.username());
            result.put("password", account.password());
            return toolResult(result, false);
        } catch (RuntimeException e) {
            return textResult(e.getMessage(), true);
        }
    }

    private JsonNode deleteAccount(JsonNode arguments) throws JsonRpcException {
        AccountManager manager = accounts.orElseThrow(() -> new JsonRpcException(INVALID_PARAMS, "Account management is not configured"));
        Optional<String> username = optionalText(arguments, "username");
        if (username.isEmpty()) {
            return textResult("Missing 'username' argument", true);
        }
        try {
            manager.delete(username.get());
            return textResult("Deleted " + username.get(), false);
        } catch (RuntimeException e) {
            return textResult(e.getMessage(), true);
        }
    }

    private Optional<String> optionalText(JsonNode arguments, String field) {
        return Optional.ofNullable(arguments.get(field))
            .filter(JsonNode::isTextual)
            .map(JsonNode::asText)
            .filter(value -> !value.isBlank());
    }

    private JsonNode validate(JsonNode arguments) {
        String script = requiredScript(arguments);
        if (script == null) {
            return textResult("Missing 'script' argument", true);
        }
        ScriptRunner.ValidationResult validation = runner.validate(script);
        ObjectNode result = mapper.createObjectNode();
        result.put("valid", validation.valid());
        result.put("sessionCount", validation.sessionCount());
        validation.error().ifPresent(error -> result.put("error", error));
        return toolResult(result, false);
    }

    private JsonNode run(JsonNode arguments) throws Exception {
        String script = requiredScript(arguments);
        if (script == null) {
            return textResult("Missing 'script' argument", true);
        }
        Map<Integer, String> sessionTargets = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = arguments.path("sessions").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            try {
                sessionTargets.put(Integer.parseInt(entry.getKey().trim()), entry.getValue().asText());
            } catch (NumberFormatException e) {
                return textResult("Invalid session number '" + entry.getKey() + "' in 'sessions'", true);
            }
        }
        Map<String, String> variables = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = arguments.path("variables").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            variables.put(entry.getKey(), entry.getValue().asText());
        }
        Optional<String> target = Optional.ofNullable(arguments.get("target"))
            .filter(JsonNode::isTextual)
            .map(JsonNode::asText)
            .filter(value -> !value.isBlank());
        Optional<Duration> timeout = Optional.ofNullable(arguments.get("timeoutSeconds"))
            .filter(JsonNode::canConvertToLong)
            .map(node -> Duration.ofSeconds(node.asLong()));
        ScriptRunner.RunRequest request = new ScriptRunner.RunRequest(script, target, sessionTargets, variables,
            arguments.path("continueAfterFailure").asBoolean(false), timeout, optionalText(arguments, "label"));

        ScriptRunner.RunReport report;
        try {
            report = runner.run(request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return textResult(e.getMessage(), true);
        }
        return reportResult(report);
    }

    private String requiredScript(JsonNode arguments) {
        JsonNode script = arguments.get("script");
        return script != null && script.isTextual() ? script.asText() : null;
    }

    JsonNode reportResult(ScriptRunner.RunReport report) {
        ObjectNode structured = mapper.createObjectNode();
        structured.put("status", report.status().name());
        structured.put("durationMs", report.durationMs());
        structured.put("runId", report.runId());
        ObjectNode sessions = structured.putObject("sessions");
        report.sessionTargets().forEach((index, name) -> sessions.put(String.valueOf(index), name));
        ArrayNode failures = structured.putArray("failures");
        report.failures().forEach(failure -> failures.add(failure.trim()));
        report.error().ifPresent(error -> structured.put("error", error));
        ObjectNode captured = structured.putObject("capturedVariables");
        report.capturedVariables().forEach(captured::put);
        ObjectNode unread = structured.putObject("unreadServerLines");
        report.unreadServerLines().forEach((index, lines) -> lines.forEach(unread.withArray(String.valueOf(index))::add));
        report.sessionsClosedByServer().forEach(structured.putArray("sessionsClosedByServer")::add);
        report.archive().ifPresent(path -> structured.put("archive", path));
        structured.put("transcript", report.transcript().format());

        StringBuilder text = new StringBuilder();
        text.append("Status: ").append(report.status()).append(" (").append(report.durationMs()).append(" ms)\n");
        text.append("Sessions: ").append(report.sessionTargets()).append('\n');
        report.error().ifPresent(error -> text.append("\nError:\n").append(error).append('\n'));
        if (!report.failures().isEmpty()) {
            text.append("\nFailures:\n");
            report.failures().forEach(failure -> text.append(failure.trim()).append("\n---\n"));
        }
        if (!report.unreadServerLines().isEmpty()) {
            text.append("\nUnread server lines (sent by the server, not consumed by the script):\n");
            report.unreadServerLines().forEach((index, lines) ->
                lines.forEach(line -> text.append("  [").append(index).append("] ").append(line).append('\n')));
        }
        if (!report.sessionsClosedByServer().isEmpty()) {
            text.append("Sessions closed by the server: ").append(report.sessionsClosedByServer()).append('\n');
        }
        report.archive().ifPresent(path -> text.append("Archived in: ").append(path).append('\n'));
        if (!report.capturedVariables().isEmpty()) {
            text.append("\nCaptured variables: ").append(report.capturedVariables()).append('\n');
        }
        text.append("\nTranscript:\n").append(report.transcript().format());

        ObjectNode result = textResult(text.toString(), false);
        result.set("structuredContent", structured);
        return result;
    }

    private ObjectNode toolResult(ObjectNode structured, boolean isError) {
        ObjectNode result = textResult(write(structured), isError);
        result.set("structuredContent", structured);
        return result;
    }

    private ObjectNode textResult(String text, boolean isError) {
        ObjectNode result = mapper.createObjectNode();
        ObjectNode content = result.putArray("content").addObject();
        content.put("type", "text");
        content.put("text", text);
        result.put("isError", isError);
        return result;
    }

    private ObjectNode response(JsonNode id, JsonNode result) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        return response;
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null ? mapper.nullNode() : id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return response;
    }

    static class JsonRpcException extends Exception {
        private final int code;

        JsonRpcException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
