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

import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


@SuppressWarnings("checkstyle:membername")
class IMAPServerAppendNonSynchronizedLitteralsTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;
    private SocketChannel clientConnection;

    @BeforeEach
    void beforeEach() throws Exception {
        imapServer = createImapServer("imapServerNoLimits.xml");
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

    @Test
    void appendShouldSucceedWhenNonSynchronized() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        String msg = "Date: Mon, 7 Feb 1994 21:52:25 -0800 (PST)\r\n" +
            "From: Fred Foobar <foobar@Blurdybloop.COM>\r\n" +
            "Subject: afternoon meeting 2\r\n" +
            "To: mooch@owatagu.siam.edu\r\n" +
            "Message-Id: <B27397-0100000@Blurdybloop.COM>\r\n" +
            "MIME-Version: 1.0\r\n" +
            "Content-Type: TEXT/PLAIN; CHARSET=US-ASCII\r\n" +
            "\r\n" +
            "Hello Joe, could we change that to 4:00pm tomorrow?\r\n";
        clientConnection.write(ByteBuffer.wrap(("A004 APPEND INBOX {" + msg.length() + "+}\r\n" +
            msg + "\r\n").getBytes(StandardCharsets.UTF_8)));

        assertThat(new String(readBytes(clientConnection), StandardCharsets.US_ASCII)).contains("APPEND completed.");

    }

    @Test
    void fetchShouldNotFailWhenMixedWithUnselect() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        String msg = "Date: Mon, 7 Feb 1994 21:52:25 -0800 (PST)\r\n" +
            "From: Fred Foobar <foobar@Blurdybloop.COM>\r\n" +
            "Subject: afternoon meeting 2\r\n" +
            "To: mooch@owatagu.siam.edu\r\n" +
            "Message-Id: <B27397-0100000@Blurdybloop.COM>\r\n" +
            "MIME-Version: 1.0\r\n" +
            "Content-Type: TEXT/PLAIN; CHARSET=US-ASCII\r\n" +
            "\r\n" +
            "Hello Joe, could we change that to 4:00pm tomorrow?\r\n";
        clientConnection.write(ByteBuffer.wrap(("A004 APPEND INBOX {" + msg.length() + "+}\r\n" +
            msg + "\r\n").getBytes(StandardCharsets.UTF_8)));

        assertThat(new String(readBytes(clientConnection), StandardCharsets.US_ASCII)).contains("APPEND completed.");


        for (int i = 0; i < 1000; i++) {
            clientConnection.write(ByteBuffer.wrap(("A005 SELECT INBOX\r\n")
                .getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("A005 OK"));
            clientConnection.write(ByteBuffer.wrap(("A006 UID FETCH 1:1 FLAGS\r\nA007 UNSELECT\r\n")
                .getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("FETCH completed."));
        }

    }

    @Test
    void partialCommandAfterNonSynchronizedLiteralShouldNotFail() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        String msg = "Date: Mon, 7 Feb 1994 21:52:25 -0800 (PST)\r\n" +
            "From: Fred Foobar <foobar@Blurdybloop.COM>\r\n" +
            "Subject: afternoon meeting 2\r\n" +
            "To: mooch@owatagu.siam.edu\r\n" +
            "Message-Id: <B27397-0100000@Blurdybloop.COM>\r\n" +
            "MIME-Version: 1.0\r\n" +
            "Content-Type: TEXT/PLAIN; CHARSET=US-ASCII\r\n" +
            "\r\n" +
            "Hello Joe, could we change that to 4:00pm tomorrow?\r\n";
        clientConnection.write(ByteBuffer.wrap(("A004 APPEND INBOX {" + msg.length() + "+}\r\n" +
            msg + "\r\nA005 NOOP").getBytes(StandardCharsets.UTF_8)));

        assertThat(new String(readBytes(clientConnection), StandardCharsets.US_ASCII)).contains("APPEND completed.");
    }

    @Test
    void extraDataAfterFirstLineShouldNotBeLost() throws Exception {
        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        String msg = " Mon, 7 Feb 1994 21:52:25 -0800 (PST)\r\n" +
            "From: Fred Foobar <foobar@Blurdybloop.COM>\r\n" +
            "Subject: afternoon meeting 2\r\n" +
            "To: mooch@owatagu.siam.edu\r\n" +
            "Message-Id: <B27397-0100000@Blurdybloop.COM>\r\n" +
            "MIME-Version: 1.0\r\n" +
            "Content-Type: TEXT/PLAIN; CHARSET=US-ASCII\r\n" +
            "\r\n" +
            "Hello Joe, could we change that to 4:00pm tomorrow?\r\n";
        clientConnection.write(ByteBuffer.wrap(("A004 APPEND INBOX {" + (msg.length() + 4) + "+}\r\nDATE").getBytes(StandardCharsets.UTF_8)));

        Thread.sleep(100); // Forces separate TCP messages

        clientConnection.write(ByteBuffer.wrap((msg).getBytes(StandardCharsets.UTF_8)));

        Thread.sleep(100); // Forces separate TCP messages

        clientConnection.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));

        assertThat(new String(readBytes(clientConnection), StandardCharsets.US_ASCII)).contains("APPEND completed.");
    }
}
