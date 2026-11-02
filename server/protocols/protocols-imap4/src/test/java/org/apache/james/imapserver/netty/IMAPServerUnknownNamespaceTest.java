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

package org.apache.james.imapserver.netty;

import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IMAPServerUnknownNamespaceTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;

    @AfterEach
    void tearDown() {
        if (imapServer != null) {
            imapServer.destroy();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT \"#foo\"",
        "EXAMINE \"#foo\"",
        "STATUS \"#foo\" (MESSAGES)",
        "DELETE \"#foo\"",
        "RENAME \"#foo\" \"bar\"",
        "LIST \"\" \"#e.%\""})
    void commandInAnUnknownNamespaceShouldAnswerNoAndKeepTheConnection(String command) throws Exception {
        imapServer = createImapServer("imapServer.xml");

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            try (SocketChannel c = SocketChannel.open(new InetSocketAddress(LOCALHOST_IP,
                    imapServer.getListenAddresses().getFirst().getPort()))) {
                readUntil(c, s -> s.contains("OK"));
                c.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
                readUntil(c, s -> s.contains("a0 OK"));

                c.write(ByteBuffer.wrap(("a1 " + command + "\r\n").getBytes(StandardCharsets.UTF_8)));
                assertThat(readUntil(c, s -> s.contains("a1 ")))
                    .contains("a1 NO");

                c.write(ByteBuffer.wrap("a2 NOOP\r\n".getBytes(StandardCharsets.UTF_8)));
                assertThat(readUntil(c, s -> s.contains("a2 ")))
                    .contains("a2 OK");
            }
        });
    }

    private String readUntil(SocketChannel channel, Predicate<String> condition) throws IOException {
        StringBuilder result = new StringBuilder();
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        while (!condition.test(result.toString())) {
            buffer.clear();
            if (channel.read(buffer) < 0) {
                throw new EOFException("Connection closed by the server, received: " + result);
            }
            buffer.flip();
            result.append(StandardCharsets.UTF_8.decode(buffer));
        }
        return result.toString();
    }
}
