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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class IMAPServerUtf8AcceptTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;

    @AfterEach
    void tearDown() {
        if (imapServer != null) {
            imapServer.destroy();
        }
    }

    @Test
    void capabilityShouldAdvertiseUtf8Accept() throws Exception {
        imapServer = createImapServer("imapServer.xml");
        assertThat(
            testIMAPClient.connect("127.0.0.1", imapServer.getListenAddresses().getFirst().getPort())
                .sendCommand("CAPABILITY"))
            .contains("UTF8=ACCEPT");
    }

    @Test
    void enableUtf8AcceptShouldSucceed() throws Exception {
        imapServer = createImapServer("imapServer.xml");
        assertThat(
            testIMAPClient.connect("127.0.0.1", imapServer.getListenAddresses().getFirst().getPort())
                .login(USER.asString(), USER_PASS)
                .sendCommand("ENABLE UTF8=ACCEPT"))
            .contains("* ENABLED UTF8=ACCEPT")
            .contains("OK ENABLE completed.");
    }

    @Test
    void enableUtf8AcceptShouldNotEchoUnsupportedCapability() throws Exception {
        imapServer = createImapServer("imapServer.xml");
        assertThat(
            testIMAPClient.connect("127.0.0.1", imapServer.getListenAddresses().getFirst().getPort())
                .login(USER.asString(), USER_PASS)
                .sendCommand("ENABLE BOGUS-CAPABILITY UTF8=ACCEPT"))
            .contains("* ENABLED UTF8=ACCEPT")
            .doesNotContain("BOGUS-CAPABILITY")
            .contains("OK ENABLE completed.");
    }

    @Test
    void listShouldEncodeMailboxNameAsModifiedUtf7WhenUtf8AcceptNotEnabled() throws Exception {
        imapServer = createImapServer("imapServer.xml");
        MailboxSession session = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.forUser(USER, "grå"), session);

        try (SocketChannel c = SocketChannel.open(new InetSocketAddress(LOCALHOST_IP,
                imapServer.getListenAddresses().getFirst().getPort()))) {
            readUtf8Bytes(c);
            c.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readUtf8Until(c, s -> s.contains("a0 OK"));
            c.write(ByteBuffer.wrap("a1 LIST \"\" \"*\"\r\n".getBytes(StandardCharsets.UTF_8)));
            List<String> replies = readUtf8Until(c, s -> s.contains("a1 OK"));

            assertThat(String.join("", replies))
                .contains("gr&AOU-")
                .doesNotContain("grå");
        }
    }

    @Test
    void createWithUnicodeMailboxNameShouldSucceedAfterEnableUtf8Accept() throws Exception {
        imapServer = createImapServer("imapServer.xml");

        try (SocketChannel c = SocketChannel.open(new InetSocketAddress(LOCALHOST_IP,
                imapServer.getListenAddresses().getFirst().getPort()))) {
            readUtf8Bytes(c);
            c.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readUtf8Until(c, s -> s.contains("a0 OK"));
            c.write(ByteBuffer.wrap("a1 ENABLE UTF8=ACCEPT\r\n".getBytes(StandardCharsets.UTF_8)));
            readUtf8Until(c, s -> s.contains("a1 OK"));
            c.write(ByteBuffer.wrap("a2 CREATE \"grå\"\r\n".getBytes(StandardCharsets.UTF_8)));
            readUtf8Until(c, s -> s.contains("a2 OK"));
            c.write(ByteBuffer.wrap("a3 LIST \"\" \"*\"\r\n".getBytes(StandardCharsets.UTF_8)));
            List<String> replies = readUtf8Until(c, s -> s.contains("a3 OK"));

            assertThat(String.join("", replies)).contains("grå");
        }
    }

    @Test
    void listShouldEncodeMailboxNameAsRawUtf8WhenUtf8AcceptEnabled() throws Exception {
        imapServer = createImapServer("imapServer.xml");
        MailboxSession session = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.forUser(USER, "grå"), session);

        try (SocketChannel c = SocketChannel.open(new InetSocketAddress(LOCALHOST_IP,
                imapServer.getListenAddresses().getFirst().getPort()))) {
            readUtf8Bytes(c);
            c.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readUtf8Until(c, s -> s.contains("a0 OK"));
            c.write(ByteBuffer.wrap("a1 ENABLE UTF8=ACCEPT\r\n".getBytes(StandardCharsets.UTF_8)));
            readUtf8Until(c, s -> s.contains("a1 OK"));
            c.write(ByteBuffer.wrap("a2 LIST \"\" \"*\"\r\n".getBytes(StandardCharsets.UTF_8)));
            List<String> replies = readUtf8Until(c, s -> s.contains("a2 OK"));

            assertThat(String.join("", replies))
                .contains("grå")
                .doesNotContain("gr&AOU-");
        }
    }

    private byte[] readUtf8Bytes(SocketChannel channel) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(8192);
        channel.read(buf);
        buf.flip();
        byte[] out = new byte[buf.remaining()];
        buf.get(out);
        return out;
    }

    private List<String> readUtf8Until(SocketChannel channel, Predicate<String> condition) throws IOException {
        ImmutableList.Builder<String> result = ImmutableList.builder();
        while (true) {
            String line = new String(readUtf8Bytes(channel), StandardCharsets.UTF_8);
            result.add(line);
            if (condition.test(line)) {
                return result.build();
            }
        }
    }
}
