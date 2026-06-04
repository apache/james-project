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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@SuppressWarnings("checkstyle:membername")
class IMAPServerSequentialExecutionTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;
    private MailboxSession mailboxSession;
    private MessageManager inbox;
    private SocketChannel clientConnection;
    private int port;

    @BeforeEach
    void beforeEach() throws Exception {
        imapServer = createImapServer("imapServer.xml");
        port = imapServer.getListenAddresses().get(0).getPort();
        mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager()
            .createMailbox(MailboxPath.inbox(USER), mailboxSession);
        inbox = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession);
        setUpTestingData();

        clientConnection = SocketChannel.open();
        clientConnection.connect(new InetSocketAddress(LOCALHOST_IP, port));
        readBytes(clientConnection);
    }

    @AfterEach
    void tearDown() throws Exception {
        clientConnection.close();
        imapServer.destroy();
    }

    private void setUpTestingData() {
        IntStream.range(0, 37)
            .forEach(Throwing.intConsumer(i -> inbox.appendMessage(MessageManager.AppendCommand.builder()
                .build("MIME-Version: 1.0\r\n\r\nCONTENT\r\n"), mailboxSession)));
    }

    @Test
    void compressShouldFailWhenNotEnabled() throws Exception {
        String reply = testIMAPClient.connect("127.0.0.1", imapServer.getListenAddresses().get(0).getPort())
            .login(USER.asString(), USER_PASS)
            .sendCommand("COMPRESS DEFLATE");

        assertThat(reply).contains("AAAB BAD COMPRESS failed. Unknown command.");
    }

    @Test
    void linearizerShouldBeUsableConcurrently() throws Exception {
        ConcurrentTestRunner.builder()
            .operation((a, b) ->  {
                SocketChannel clientConnection = SocketChannel.open();
                clientConnection.connect(new InetSocketAddress(LOCALHOST_IP, port));
                readBytes(clientConnection);

                clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
                readBytes(clientConnection);

                for (int i = 0; i < 100; i++) {
                    clientConnection.write(ByteBuffer.wrap("a0 SELECT INBOX\r\na0 UNSELECT\r\n".getBytes()));
                }
                clientConnection.write(ByteBuffer.wrap("a1 NOOP\r\n".getBytes()));

                readStringUntil(clientConnection, s -> s.contains("a1 OK"));
            }).threadCount(32)
            .operationCount(1)
            .runSuccessfullyWithin(Duration.ofMinutes(10));
    }

    @Test
    void ensureSequentialExecutionOfImapRequests() throws Exception {
        IntStream.range(0, 100)
            .forEach(Throwing.intConsumer(i -> inbox.appendMessage(MessageManager.AppendCommand.builder()
                .build("MIME-Version: 1.0\r\n\r\nCONTENT\r\n"), mailboxSession)));

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        clientConnection.write(ByteBuffer.wrap(("A1 SELECT INBOX\r\nA2 UID FETCH 1:100 (FLAGS)\r\n").getBytes(StandardCharsets.UTF_8)));

        // Select completes first
        readStringUntil(clientConnection, s -> s.contains("A1 OK [READ-WRITE] SELECT completed."));
        // Then the FETCH
        readStringUntil(clientConnection, s -> s.contains("A2 OK FETCH completed."));
    }

    @Test
    void fetchShouldBackPressureWhenNoRead() throws Exception {
        String msgIn = "MIME-Version: 1.0\r\n\r\nCONTENT\r\n\r\n" + "0123456789\r\n0123456789\r\n0123456789\r\n".repeat(1024);
        IntStream.range(0, 500)
            .forEach(Throwing.intConsumer(i -> inbox.appendMessage(MessageManager.AppendCommand.builder()
                .build(msgIn), mailboxSession)));
        AtomicInteger loaded = new AtomicInteger(0);
        MessageManager inboxSpy = spy(inbox);
        doReturn(Mono.just(inboxSpy)).when(mailboxManager).getMailboxReactive(eq(MailboxPath.inbox(USER)), any());
        doReturn(Mono.just(inboxSpy)).when(mailboxManager).getMailboxReactive(eq(inbox.getMailboxEntity().getMailboxId()), any());
        doAnswer((Answer<Object>) invocationOnMock -> Flux.from(inbox.getMessagesReactive(invocationOnMock.getArgument(0), invocationOnMock.getArgument(1), invocationOnMock.getArgument(2)))
            .doOnNext(any -> loaded.incrementAndGet())).when(inboxSpy).getMessagesReactive(any(), any(), any());

        clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
        readBytes(clientConnection);

        clientConnection.write(ByteBuffer.wrap(("A1 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
        // Select completes first
        readStringUntil(clientConnection, s -> s.contains("A1 OK [READ-WRITE] SELECT completed."));
        clientConnection.write(ByteBuffer.wrap(("A2 UID FETCH 1:500 (BODY[])\r\n").getBytes(StandardCharsets.UTF_8)));


        assertThat(loaded.get()).isLessThan(500);
        readStringUntil(clientConnection, s -> s.contains("A2 OK FETCH completed."));
        assertThat(loaded.get()).isEqualTo(500);
    }
}
