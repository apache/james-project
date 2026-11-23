/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.managesieve.transcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.api.commands.CoreCommands;
import org.apache.james.managesieve.util.SettableSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class ManageSieveProcessorTest {
    record Call(String method, List<Object> arguments) {
    }

    private List<Call> calls;
    private ManageSieveProcessor processor;
    private Session session;

    @BeforeEach
    void setUp() {
        calls = new ArrayList<>();
        CoreCommands core = (CoreCommands) Proxy.newProxyInstance(CoreCommands.class.getClassLoader(),
            new Class<?>[] {CoreCommands.class},
            (proxy, method, args) -> {
                calls.add(new Call(method.getName(), args == null ? ImmutableList.of() : List.of(args)));
                return "OK";
            });
        processor = new ManageSieveProcessor(new ArgumentParser(core), ImmutableList.of(), null);
        session = new SettableSession();
    }

    @Test
    void nonAsciiCommandShouldNotMatchStartTls() throws Exception {
        assertThat(processor.handleRequest(session, "ſTARTTLS\r\n")).startsWith("NO");
    }

    @Test
    void putScriptShouldStoreTheLiteralOctetsWhenNoFinalLineBreak() throws Exception {
        String response = processor.handleRequest(session, "PUTSCRIPT \"a\" {11+}\r\n# é\r\nkeep;\r\n");

        assertThat(response).isEqualTo("OK\r\n");
        assertThat(calls).containsExactly(new Call("putScript", List.of(session, "a", "# é\r\nkeep;")));
    }

    @Test
    void putScriptShouldWaitForTheLiteralOctetsWhenNonAscii() {
        assertThatThrownBy(() -> processor.handleRequest(session, "PUTSCRIPT \"e\" {15+}\r\n# éé\r\nkeep;\r\n"))
            .isInstanceOf(NotEnoughDataException.class);
    }

    @Test
    void putScriptShouldStoreTheLiteralOctetsWhenNonAsciiAndFinalLineBreak() throws Exception {
        String response = processor.handleRequest(session, "PUTSCRIPT \"e\" {15+}\r\n# éé\r\nkeep;\r\n\r\n");

        assertThat(response).isEqualTo("OK\r\n");
        assertThat(calls).containsExactly(new Call("putScript", List.of(session, "e", "# éé\r\nkeep;\r\n")));
    }

    @Test
    void putScriptShouldRejectDataBeyondTheLiteral() throws Exception {
        String response = processor.handleRequest(session, "PUTSCRIPT \"a\" {3+}\r\nkeep;\r\n");

        assertThat(response).isEqualTo("NO \"Unexpected data after the script literal\"\r\n");
        assertThat(calls).isEmpty();
    }

    @Test
    void checkScriptShouldCheckTheLiteralOctets() throws Exception {
        String response = processor.handleRequest(session, "CHECKSCRIPT {15+}\r\n# éé\r\nkeep;\r\n\r\n");

        assertThat(response).isEqualTo("OK\r\n");
        assertThat(calls).containsExactly(new Call("checkScript", List.of(session, "# éé\r\nkeep;\r\n")));
    }

    @Test
    void rejectedPutScriptShouldWaitForItsLiteral() {
        assertThatThrownBy(() -> processor.handleRequest(session, "PUTSCRIPT \"\" {13+}\r\n"))
            .isInstanceOf(NotEnoughDataException.class);
    }

    @Test
    void rejectedPutScriptShouldConsumeItsLiteral() throws Exception {
        String response = processor.handleRequest(session, "PUTSCRIPT \"\" {13+}\r\nLISTSCRIPTS\r\n\r\n");

        assertThat(response).isEqualTo("NO \"Missing argument: script name\"\r\n");
        assertThat(calls).isEmpty();
    }

    @Test
    void putScriptWithExtraArgumentsShouldConsumeItsLiteral() {
        assertThatThrownBy(() -> processor.handleRequest(session, "PUTSCRIPT \"a\" extra {13+}\r\n"))
            .isInstanceOf(NotEnoughDataException.class);
    }

    @Test
    void checkScriptWithExtraArgumentsShouldConsumeItsLiteral() throws Exception {
        assertThatThrownBy(() -> processor.handleRequest(session, "CHECKSCRIPT extra {13+}\r\n"))
            .isInstanceOf(NotEnoughDataException.class);

        String response = processor.handleRequest(session, "CHECKSCRIPT extra {13+}\r\nLISTSCRIPTS\r\n\r\n");

        assertThat(response).startsWith("NO ");
        assertThat(calls).isEmpty();
    }

    @Test
    void putScriptShouldRejectInvalidEscapesInTheScriptName() throws Exception {
        String response = processor.handleRequest(session, "PUTSCRIPT \"a\\x01b\" {5+}\r\nkeep;\r\n");

        assertThat(response).startsWith("NO \"Invalid escape sequence");
        assertThat(calls).isEmpty();
    }

    @Test
    void putScriptWithAnInvalidNameShouldConsumeItsLiteral() {
        assertThatThrownBy(() -> processor.handleRequest(session, "PUTSCRIPT \"a\\x01b\" {5+}\r\n"))
            .isInstanceOf(NotEnoughDataException.class);
    }

    @Test
    void unknownCommandShouldBeAnsweredWithAQuotedText() throws Exception {
        assertThat(processor.handleRequest(session, "FO\"O{3}"))
            .isEqualTo("NO \"unknown FO\\\"O{3} command\"\r\n");
    }

    @Test
    void unauthenticateWithArgumentsShouldBeAnsweredWithAQuotedText() throws Exception {
        assertThat(processor.handleRequest(session, "UNAUTHENTICATE foo"))
            .isEqualTo("NO \"UNAUTHENTICATE do not take arguments\"\r\n");
    }

    @Test
    void haveSpaceShouldRejectAnEmptyScriptNameAsPutScriptDoes() throws Exception {
        assertThat(processor.handleRequest(session, "HAVESPACE \"\" 10\r\n"))
            .isEqualTo("NO \"Missing argument: script name\"\r\n");
        assertThat(calls).isEmpty();
    }
}
