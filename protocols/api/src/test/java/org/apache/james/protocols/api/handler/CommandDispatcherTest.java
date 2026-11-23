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

package org.apache.james.protocols.api.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

import org.apache.james.protocols.api.ProtocolSession;
import org.junit.jupiter.api.Test;

class CommandDispatcherTest {
    private static String commandName(String line) throws Exception {
        ProtocolSession session = (ProtocolSession) Proxy.newProxyInstance(CommandDispatcherTest.class.getClassLoader(),
            new Class<?>[] {ProtocolSession.class},
            (proxy, method, args) -> method.getName().equals("getCharset") ? StandardCharsets.UTF_8 : null);
        return new CommandDispatcher<>().parseRequest(session, line.getBytes(StandardCharsets.UTF_8)).getCommand();
    }

    @Test
    void commandNameShouldBeUpperCased() throws Exception {
        assertThat(commandName("starttls\r\n")).isEqualTo("STARTTLS");
    }

    @Test
    void commandNameShouldNotFoldNonAsciiCharacters() throws Exception {
        assertThat(commandName("ſTARTTLS\r\n")).isNotEqualTo("STARTTLS");
        assertThat(commandName("ﬆARTTLS\r\n")).isNotEqualTo("STARTTLS");
    }
}
