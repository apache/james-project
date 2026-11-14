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

package org.apache.james.mpt;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.apache.james.mpt.api.Session;
import org.apache.james.mpt.protocol.FileProtocolSessionBuilder;
import org.apache.james.mpt.protocol.ProtocolSession;
import org.junit.jupiter.api.Test;

class ProtocolSessionSyntaxTest {

    static class ScriptedSession implements Session {
        final Deque<String> serverLines;
        final List<String> written = new ArrayList<>();

        ScriptedSession(String... serverLines) {
            this.serverLines = new ArrayDeque<>(List.of(serverLines));
        }

        @Override
        public String readLine() throws Exception {
            if (serverLines.isEmpty()) {
                throw new java.io.EOFException("closed");
            }
            return serverLines.poll();
        }

        @Override
        public void writeLine(String line) {
            written.add(line);
        }

        @Override
        public void start() {
        }

        @Override
        public void restart() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void await() {
        }
    }

    private ProtocolSession parse(String script) throws Exception {
        ProtocolSession session = new ProtocolSession();
        new FileProtocolSessionBuilder().addProtocolLinesFromStream(new ByteArrayInputStream(script.getBytes(UTF_8)), session, "test");
        return session;
    }

    @Test
    void namedGroupsShouldBeCapturedAndSubstituted() throws Exception {
        ProtocolSession protocolSession = parse("""
            S: \\* OK \\[UIDVALIDITY (?<uidvalidity>\\d+)\\].*
            C: A1 FETCH ${uidvalidity}
            S: A1 OK ${uidvalidity}
            """);
        ScriptedSession session = new ScriptedSession("* OK [UIDVALIDITY 42] ready", "A1 OK 42");

        protocolSession.runSessions(new Session[] {session});

        assertThat(session.written).containsExactly("A1 FETCH 42");
        assertThat(protocolSession.getVariables()).containsEntry("uidvalidity", "42");
    }

    @Test
    void variablesShouldBeQuotedInExpectedLines() throws Exception {
        ProtocolSession protocolSession = parse("S: hello ${name}\n");
        protocolSession.setVariable("name", "a.b");

        assertThatThrownBy(() -> protocolSession.runSessions(new Session[] {new ScriptedSession("hello aXb")}))
            .isInstanceOf(ProtocolSession.InvalidServerResponseException.class);
    }

    @Test
    void untilShouldSkipUnmatchedLines() throws Exception {
        ProtocolSession protocolSession = parse("""
            UNTIL: A1 OK.*
            S: done
            """);

        protocolSession.runSessions(new Session[] {new ScriptedSession("* 1 EXISTS", "* 0 RECENT", "A1 OK SELECT", "done")});
    }

    @Test
    void setAndSetBase64ShouldDefineVariables() throws Exception {
        ProtocolSession protocolSession = parse("""
            SET: user=bob
            SET-BASE64: plain=\\0${user}\\0secret
            C: AUTH PLAIN ${plain}
            """);
        ScriptedSession session = new ScriptedSession();

        protocolSession.runSessions(new Session[] {session});

        assertThat(session.written).containsExactly("AUTH PLAIN AGJvYgBzZWNyZXQ=");
    }

    @Test
    void failuresShouldBeCollectedWhenContinuingAfterFailure() throws Exception {
        ProtocolSession protocolSession = parse("""
            S: one
            S: two
            """);
        protocolSession.setContinueAfterFailure(true);

        protocolSession.runSessions(new Session[] {new ScriptedSession("bad", "two")});

        assertThat(protocolSession.getFailures()).hasSize(1);
        assertThat(protocolSession.getFailures().get(0)).contains("test:1").contains("Actual   : 'bad'");
    }

    @Test
    void parseErrorsShouldReportTheLineNumber() {
        assertThatThrownBy(() -> parse("C: a\n\nS: (\n"))
            .hasMessageContaining("test:3")
            .hasMessageContaining("Invalid regular expression");
    }
}
