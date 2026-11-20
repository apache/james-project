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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Stores, server side, the script, transcript and verdict of every run: replayable evidence independent of the agent.
 */
public class RunArchive {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'");
    private static final int MAX_LABEL_LENGTH = 64;

    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public RunArchive(Path directory) {
        this.directory = directory;
    }

    static String sanitize(String label) {
        String sanitized = label.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^[._]+", "");
        return sanitized.length() > MAX_LABEL_LENGTH ? sanitized.substring(0, MAX_LABEL_LENGTH) : sanitized;
    }

    public String store(ScriptRunner.RunRequest request, ScriptRunner.RunReport report) {
        String label = request.label().map(RunArchive::sanitize).filter(value -> !value.isEmpty()).orElse("run");
        String name = TIMESTAMP.format(ZonedDateTime.now(ZoneOffset.UTC)) + "_" + label + "_" + report.runId();
        Path runDirectory = directory.resolve(name);
        try {
            Files.createDirectories(runDirectory);
            Files.writeString(runDirectory.resolve("script.mpt"), request.script(), UTF_8);
            Files.writeString(runDirectory.resolve("transcript.txt"), report.transcript().format(), UTF_8);

            ObjectNode json = mapper.createObjectNode();
            json.put("label", request.label().orElse(""));
            json.put("runId", report.runId());
            json.put("status", report.status().name());
            json.put("durationMs", report.durationMs());
            ObjectNode sessions = json.putObject("sessions");
            report.sessionTargets().forEach((index, target) -> sessions.put(String.valueOf(index), target));
            report.failures().forEach(json.putArray("failures")::add);
            report.error().ifPresent(error -> json.put("error", error));
            ObjectNode captured = json.putObject("capturedVariables");
            report.capturedVariables().forEach(captured::put);
            ObjectNode requestVariables = json.putObject("requestVariables");
            request.variables().forEach(requestVariables::put);
            ObjectNode unread = json.putObject("unreadServerLines");
            report.unreadServerLines().forEach((index, lines) -> lines.forEach(unread.withArray(String.valueOf(index))::add));
            json.put("continueAfterFailure", request.continueAfterFailure());
            Files.writeString(runDirectory.resolve("report.json"), mapper.writeValueAsString(json), UTF_8);
            return runDirectory.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not archive run in " + runDirectory, e);
        }
    }
}
