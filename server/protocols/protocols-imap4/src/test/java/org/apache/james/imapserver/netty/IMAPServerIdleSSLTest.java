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
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;


@SuppressWarnings("checkstyle:membername")
class IMAPServerIdleSSLTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;
    private MailboxSession mailboxSession;
    private MessageManager inbox;
    private Socket clientConnection;

    @BeforeEach
    void beforeEach() throws Exception {
        imapServer = createImapServer("imapServerSSL.xml");
        int port = imapServer.getListenAddresses().get(0).getPort();
        mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.inbox(USER), mailboxSession);
        inbox = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{new BlindTrustManager()}, null);
        clientConnection = ctx.getSocketFactory().createSocket();
        clientConnection.connect(new InetSocketAddress(LOCALHOST_IP, port));
        byte[] buffer = new byte[8193];
        clientConnection.getInputStream().read(buffer);
    }

    @AfterEach
    void tearDown() throws Exception {
        clientConnection.close();
        imapServer.destroy();
    }

    @Test
    void idleShouldSendInitialContinuation() throws Exception {
        clientConnection.getOutputStream().write(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8));
        readBytes(clientConnection);

        clientConnection.getOutputStream().write("a2 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8));
        readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

        clientConnection.getOutputStream().write("a3 IDLE\r\n".getBytes(StandardCharsets.UTF_8));

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
            assertThat(readStringUntil(clientConnection, s -> s.contains("+ Idling")))
                .isNotNull());
    }

    @Test
    void idleShouldBeInterruptible() throws Exception {
        clientConnection.getOutputStream().write(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8));
        readBytes(clientConnection);

        clientConnection.getOutputStream().write("a2 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8));
        readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

        clientConnection.getOutputStream().write(("a3 IDLE\r\n".getBytes(StandardCharsets.UTF_8)));
        readStringUntil(clientConnection, s -> s.contains("+ Idling"));

        clientConnection.getOutputStream().write("DONE\r\n".getBytes(StandardCharsets.UTF_8));

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
            assertThat(readStringUntil(clientConnection, s -> s.contains("a3 OK IDLE completed.")))
                .isNotNull());
    }

    @Test
    void idleShouldBeInterruptibleWhenBatched() throws Exception {
        clientConnection.getOutputStream().write(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8));
        readBytes(clientConnection);

        clientConnection.getOutputStream().write("a2 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8));
        readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

        clientConnection.getOutputStream().write("a3 IDLE\r\nDONE\r\n".getBytes(StandardCharsets.UTF_8));

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
            assertThat(readStringUntil(clientConnection, s -> s.contains("a3 OK IDLE completed.")))
                .isNotNull());
    }

    @Test
    void idleResponsesShouldBeOrdered() throws Exception {
        clientConnection.getOutputStream().write(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8));
        readBytes(clientConnection);

        clientConnection.getOutputStream().write("a2 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8));
        readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

        clientConnection.getOutputStream().write("a3 IDLE\r\nDONE\r\n".getBytes(StandardCharsets.UTF_8));

        // Assert continuation is sent before IDLE completion result
        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
            assertThat(readStringUntil(clientConnection, s -> s.contains("a3 OK IDLE completed.")))
                .filteredOn(s -> s.contains("+ Idling"))
                .hasSize(1));
    }

    @Test
    void idleShouldReturnUnderstandableErrorMessageWhenBadDone() throws Exception {
        clientConnection.getOutputStream().write(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8));
        readBytes(clientConnection);

        clientConnection.getOutputStream().write("a2 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8));
        readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

        clientConnection.getOutputStream().write("a3 IDLE\r\nBAD\r\n".getBytes(StandardCharsets.UTF_8));

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
            assertThat(readStringUntil(clientConnection, s -> s.contains("a3 BAD IDLE failed. Continuation for IMAP IDLE was not understood. Expected 'DONE', got 'BAD'.")))
                .isNotNull());
    }

    // Repeated run to detect more reliably data races
    @RepeatedTest(50)
    void idleShouldReturnUpdates() throws Exception {
        clientConnection.getOutputStream().write(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8));
        readBytes(clientConnection);

        clientConnection.getOutputStream().write("a2 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8));
        readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

        clientConnection.getOutputStream().write("a3 IDLE\r\n".getBytes(StandardCharsets.UTF_8));
        readStringUntil(clientConnection, s -> s.contains("+ Idling"));

        inbox.appendMessage(MessageManager.AppendCommand.builder().build("h: value\r\n\r\nbody".getBytes()), mailboxSession);

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
            assertThat(readStringUntil(clientConnection, s -> s.contains("* 1 EXISTS")))
                .isNotNull());
    }

    private byte[] readBytes(Socket channel) throws IOException {
        byte[] buffer = new byte[8193];
        int read = channel.getInputStream().read(buffer);
        String s = new String(buffer, 0, read, StandardCharsets.US_ASCII);
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private List<String> readStringUntil(Socket channel, Predicate<String> condition) throws IOException {
        ImmutableList.Builder<String> result = ImmutableList.builder();
        while (true) {
            String line = new String(readBytes(channel), StandardCharsets.US_ASCII);
            result.add(line);
            if (condition.test(line)) {
                return result.build();
            }
        }
    }
}
