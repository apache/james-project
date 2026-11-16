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

package org.apache.james.managesieve.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.jsieve.Parser;
import org.apache.james.managesieve.util.SettableSession;
import org.apache.james.sieverepository.api.SieveRepository;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class CoreProcessorTest {
    @Test
    void putScriptShouldNotStoreAnInvalidScript() throws Exception {
        List<String> calls = new ArrayList<>();
        SieveRepository repository = (SieveRepository) Proxy.newProxyInstance(SieveRepository.class.getClassLoader(),
            new Class<?>[] {SieveRepository.class},
            (proxy, method, args) -> {
                calls.add(method.getName());
                return null;
            });
        CoreProcessor processor = new CoreProcessor(repository, new Parser(), ImmutableList.of());
        SettableSession session = new SettableSession();
        session.setUser(Username.of("bob"));
        session.setState(Session.State.AUTHENTICATED);

        String response = processor.putScript(session, "script", "keep 2222222222;");

        assertThat(response).startsWith("NO");
        assertThat(calls).doesNotContain("putScript");
    }

    @Test
    void getScriptShouldAnnounceTheScriptSizeInOctets() throws Exception {
        SieveRepository repository = (SieveRepository) Proxy.newProxyInstance(SieveRepository.class.getClassLoader(),
            new Class<?>[] {SieveRepository.class},
            (proxy, method, args) -> new ByteArrayInputStream("# é\r\nkeep;".getBytes(StandardCharsets.UTF_8)));
        CoreProcessor processor = new CoreProcessor(repository, new Parser(), ImmutableList.of());
        SettableSession session = new SettableSession();
        session.setUser(Username.of("bob"));
        session.setState(Session.State.AUTHENTICATED);

        assertThat(processor.getScript(session, "script")).isEqualTo("{11}\r\n# é\r\nkeep;\r\nOK");
    }

    @Test
    void refusalsShouldBeAnsweredWithAQuotedText() throws Exception {
        CoreProcessor processor = new CoreProcessor(null, new Parser(), ImmutableList.of());
        SettableSession session = new SettableSession();

        assertThat(processor.unauthenticate(session)).isEqualTo("NO \"UNAUTHENTICATE command must be issued in authenticated state\"");
        assertThat(processor.startTLS(session)).isEqualTo("NO \"STARTTLS is not available\"");
    }

    @Test
    void syntaxErrorShouldBeAnsweredOnASingleLineWithAQuotedText() throws Exception {
        CoreProcessor processor = new CoreProcessor(null, new Parser(), ImmutableList.of());
        SettableSession session = new SettableSession();
        session.setUser(Username.of("bob"));
        session.setState(Session.State.AUTHENTICATED);

        assertThat(processor.checkScript(session, "if header :contains \"subject\" {\r\n"))
            .isEqualTo("NO \"Syntax Error: Encountered \\\"<EOF>\\\" at line 1, column 33.\"");
    }
}
