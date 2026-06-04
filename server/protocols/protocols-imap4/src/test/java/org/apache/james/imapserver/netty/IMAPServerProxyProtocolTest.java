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
import java.util.Set;

import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


@SuppressWarnings("checkstyle:membername")
class IMAPServerProxyProtocolTest extends AbstractIMAPServerTest {
    private static final String CLIENT_IP = "255.255.255.254";
    private static final String PROXY_IP = "255.255.255.255";
    private static final String RANDOM_IP = "127.0.0.2";

    IMAPServer imapServer;
    private SocketChannel clientConnection;

    @BeforeEach
    void beforeEach() throws Exception {
        imapServer = createImapServer("imapServerProxy.xml");
        int port = imapServer.getListenAddresses().get(0).getPort();
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.inbox(USER), memoryIntegrationResources.getMailboxManager().createSystemSession(USER));
        clientConnection = SocketChannel.open();
        clientConnection.connect(new InetSocketAddress(LOCALHOST_IP, port));
        readBytes(clientConnection);
    }

    @AfterEach
    void tearDown() throws Exception {
        clientConnection.close();
        imapServer.destroy();
    }

    private void addBannedIps(String clientIp) {
        imapServer.getConnectionChecks().stream()
            .filter(check -> check instanceof IpConnectionCheck)
            .map(check -> (IpConnectionCheck) check)
            .forEach(ipCheck -> ipCheck.setBannedIps(Set.of(clientIp)));
    }

    @Test
    void shouldNotFailOnProxyInformation() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("PROXY %s %s %s %d %d\r\na0 LOGIN %s %s\r\n",
            "TCP4", CLIENT_IP, PROXY_IP, 65535, 65535,
            USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));

        assertThat(new String(readBytes(clientConnection), StandardCharsets.US_ASCII))
            .startsWith("a0 OK");
    }

    @Test
    void shouldDetectAndBanByClientIP() throws IOException {
        addBannedIps(CLIENT_IP);

        // WHEN connect as CLIENT_IP to PROXY_DESTINATION via PROXY_IP
        clientConnection.write(ByteBuffer.wrap(String.format("PROXY %s %s %s %d %d\r\na0 LOGIN %s %s\r\n",
            "TCP4", CLIENT_IP, PROXY_IP, 65535, 65535,
            USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));

        // THEN LOGIN should be rejected
        assertThat(new String(readBytes(clientConnection), StandardCharsets.US_ASCII))
            .doesNotStartWith("a0 OK");
    }

    @Test
    void shouldNotBanByProxyIP() throws IOException {
        // GIVEN somehow PROXY_IP has been banned by mistake
        addBannedIps(PROXY_IP);

        clientConnection.write(ByteBuffer.wrap(String.format("PROXY %s %s %s %d %d\r\na0 LOGIN %s %s\r\n",
            "TCP4", CLIENT_IP, PROXY_IP, 65535, 65535,
            USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));

        // THEN CLIENT_IP still can connect
        assertThat(new String(readBytes(clientConnection), StandardCharsets.US_ASCII))
            .startsWith("a0 OK");
    }

    @Test
    void clientUsageShouldBeNormalWhenClientIPIsNotBanned() throws IOException {
        addBannedIps(RANDOM_IP);

        clientConnection.write(ByteBuffer.wrap(String.format("PROXY %s %s %s %d %d\r\na0 LOGIN %s %s\r\n",
            "TCP4", CLIENT_IP, PROXY_IP, 65535, 65535,
            USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));

        assertThat(new String(readBytes(clientConnection), StandardCharsets.US_ASCII))
            .startsWith("a0 OK");
    }
}
