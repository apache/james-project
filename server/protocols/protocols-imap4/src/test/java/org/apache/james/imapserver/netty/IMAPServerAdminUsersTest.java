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

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


@SuppressWarnings("checkstyle:membername")
class IMAPServerAdminUsersTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;
    private int port;
    private SocketChannel clientConnection;

    @BeforeEach
    void beforeEach() throws Exception {
        imapServer = createImapServer("imapServerAdminUsers.xml");
        port = imapServer.getListenAddresses().get(0).getPort();


        clientConnection = SocketChannel.open();
        clientConnection.connect(new InetSocketAddress(LOCALHOST_IP, port));
        readBytes(clientConnection);
    }

    @AfterEach
    void tearDown() throws Exception {
        clientConnection.close();
        imapServer.destroy();
    }

    @Test
    void shouldSupportPerPortAdminUsers() throws Exception {
        clientConnection.write(ByteBuffer.wrap("a0 AUTHENTICATE PLAIN\r\n".getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.startsWith("+"));
        clientConnection.write(ByteBuffer.wrap((Base64.getEncoder().encodeToString((USER2.asString() + "\0" + USER.asString() + "\0" + USER_PASS).getBytes(StandardCharsets.US_ASCII)) + "\r\n").getBytes(StandardCharsets.US_ASCII)));

        String reply = readStringUntil(clientConnection, s -> s.startsWith("a0")).getLast();

        assertThat(reply).startsWith("a0 OK");
    }
}
