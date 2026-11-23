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
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@SuppressWarnings("checkstyle:membername")
class IMAPServerLiteralCleanupTest extends AbstractIMAPServerTest {
    // imapServerNoLimits.xml sets inMemorySizeLimit to 64 KB and no APPEND limit.
    private static final int OVERSIZED = 70 * 1024;
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(30);

    IMAPServer imapServer;
    private int port;
    private Set<String> literalsBefore;

    @BeforeEach
    void beforeEach() throws Exception {
        imapServer = createImapServer("imapServerNoLimits.xml");
        port = imapServer.getListenAddresses().get(0).getPort();
        MailboxSession session = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        memoryIntegrationResources.getMailboxManager().createMailbox(MailboxPath.inbox(USER), session);
        literalsBefore = bufferedLiterals();
    }

    @AfterEach
    void tearDown() {
        imapServer.destroy();
    }

    private static Set<String> bufferedLiterals() {
        File[] files = Paths.get(System.getProperty("java.io.tmpdir")).toFile()
            .listFiles((dir, name) -> name.startsWith("imap-literal"));
        if (files == null) {
            return ImmutableSet.of();
        }
        return Arrays.stream(files)
            .map(File::getName)
            .collect(ImmutableSet.toImmutableSet());
    }

    private void awaitNoLeftoverLiteral() {
        await().atMost(AWAIT_TIMEOUT)
            .untilAsserted(() -> assertThat(Sets.difference(bufferedLiterals(), literalsBefore)).isEmpty());
    }

    private SocketChannel connectAndLogin() throws Exception {
        SocketChannel channel = SocketChannel.open(new InetSocketAddress(LOCALHOST_IP, port));
        readBytes(channel);
        channel.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS)
            .getBytes(StandardCharsets.US_ASCII)));
        readStringUntil(channel, s -> s.contains("a0 OK"));
        return channel;
    }

    private static String message(int size) {
        StringBuilder message = new StringBuilder(size);
        message.append("From: ").append(USER.asString()).append("\r\nSubject: oversized\r\n\r\n");
        while (message.length() < size) {
            message.append("0123456789012345678901234567890123456789012345678901234567890123456789\r\n");
        }
        message.setLength(size);
        return message.toString();
    }

    private static void write(SocketChannel channel, String payload) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(payload.getBytes(StandardCharsets.US_ASCII));
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private static void writeAppend(SocketChannel channel, String tag, String message) throws IOException {
        write(channel, tag + " APPEND INBOX {" + message.length() + "+}\r\n" + message + "\r\n");
    }

    @Test
    void oversizedAppendShouldNotLeaveItsLiteralOnDisk() throws Exception {
        SocketChannel channel = connectAndLogin();

        writeAppend(channel, "a1", message(OVERSIZED));

        assertThat(readStringUntil(channel, s -> s.contains("a1 ")))
            .anyMatch(s -> s.contains("APPEND completed"));
        channel.close();

        awaitNoLeftoverLiteral();
    }

    @Test
    void appendInterruptedMidUploadShouldNotLeaveItsLiteralOnDisk() throws Exception {
        SocketChannel channel = connectAndLogin();
        String message = message(OVERSIZED);

        write(channel, "a1 APPEND INBOX {" + message.length() + "+}\r\n");
        write(channel, message.substring(0, message.length() / 2));
        // Wait for the server to actually start spilling to disk before pulling the plug.
        await().atMost(AWAIT_TIMEOUT)
            .until(() -> !Sets.difference(bufferedLiterals(), literalsBefore).isEmpty());

        channel.close();

        awaitNoLeftoverLiteral();
    }

    @Test
    void appendQueuedBehindAnotherOneShouldNotLeaveItsLiteralOnDiskWhenTheConnectionDrops() throws Exception {
        CountDownLatch firstAppendReached = new CountDownLatch(1);
        CountDownLatch releaseFirstAppend = new CountDownLatch(1);
        doAnswer(invocation -> {
            Publisher<MessageManager> mailbox = (Publisher<MessageManager>) invocation.callRealMethod();
            return Mono.fromRunnable(firstAppendReached::countDown)
                .then(Mono.fromCallable(() -> releaseFirstAppend.await(30, TimeUnit.SECONDS))
                    .subscribeOn(Schedulers.boundedElastic()))
                .then(Mono.from(mailbox));
        }).when(mailboxManager).getMailboxReactive(any(MailboxPath.class), any(MailboxSession.class));

        SocketChannel channel = connectAndLogin();
        String message = message(OVERSIZED);

        writeAppend(channel, "a1", message);
        assertThat(firstAppendReached.await(30, TimeUnit.SECONDS)).isTrue();

        writeAppend(channel, "a2", message);
        // a2 is decoded while a1 is still running
        await().atMost(AWAIT_TIMEOUT).until(() -> metricFactory.countFor("imapCommands") >= 3);

        // a2 will never run: we close the channel before it gets the chance to.
        channel.close();
        await().atMost(AWAIT_TIMEOUT).until(() -> metricFactory.countFor("imapConnections") == 0);
        releaseFirstAppend.countDown();

        awaitNoLeftoverLiteral();
    }

    private byte[] storedInboxMessage() throws Exception {
        MailboxSession session = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
        MessageManager inbox = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), session);
        ImmutableList<MessageResult> messages = ImmutableList.copyOf(inbox.getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session));
        assertThat(messages).hasSize(1);
        try (InputStream content = messages.get(0).getFullContent().getInputStream()) {
            return content.readAllBytes();
        }
    }

    @Test
    void oversizedAppendWithAMailboxNameLiteralShouldStoreTheMessage() throws Exception {
        SocketChannel channel = connectAndLogin();
        String message = message(OVERSIZED);

        write(channel, "a1 APPEND {5+}\r\nINBOX {" + message.length() + "+}\r\n" + message + "\r\n");

        assertThat(readStringUntil(channel, s -> s.contains("a1 ")))
            .anyMatch(s -> s.contains("a1 OK") && s.contains("APPEND completed"));
        assertThat(storedInboxMessage()).isEqualTo(message.getBytes(StandardCharsets.US_ASCII));
        channel.close();

        awaitNoLeftoverLiteral();
    }

    @Test
    void oversizedAppendWithSynchronizingLiteralsShouldStoreTheMessage() throws Exception {
        SocketChannel channel = connectAndLogin();
        String message = message(OVERSIZED);

        write(channel, "a1 APPEND {5}\r\n");
        readStringUntil(channel, s -> s.startsWith("+"));
        write(channel, "INBOX {" + message.length() + "}\r\n");
        readStringUntil(channel, s -> s.startsWith("+"));
        write(channel, message + "\r\n");

        assertThat(readStringUntil(channel, s -> s.contains("a1 ")))
            .anyMatch(s -> s.contains("a1 OK") && s.contains("APPEND completed"));
        assertThat(storedInboxMessage()).isEqualTo(message.getBytes(StandardCharsets.US_ASCII));
        channel.close();

        awaitNoLeftoverLiteral();
    }

    @Test
    void failingOversizedAppendWithAMailboxNameLiteralShouldNotLeaveItsLiteralOnDisk() throws Exception {
        SocketChannel channel = connectAndLogin();
        String message = message(OVERSIZED);

        write(channel, "a1 APPEND {7+}\r\nmissing {" + message.length() + "+}\r\n" + message + "\r\n");

        assertThat(readStringUntil(channel, s -> s.contains("a1 ")))
            .anyMatch(s -> s.contains("a1 NO"));
        channel.close();

        awaitNoLeftoverLiteral();
    }

    private static Set<String> openBufferedLiterals() throws IOException {
        try (var descriptors = Files.list(Paths.get("/proc/self/fd"))) {
            return descriptors
                .flatMap(descriptor -> {
                    try {
                        return Stream.of(Files.readSymbolicLink(descriptor).toString());
                    } catch (IOException e) {
                        // closed in the meantime
                        return Stream.empty();
                    }
                })
                .filter(target -> target.contains("imap-literal"))
                .collect(ImmutableSet.toImmutableSet());
        }
    }

    private SocketChannel connectLoginAndSelect() throws Exception {
        SocketChannel channel = connectAndLogin();
        write(channel, "s SELECT INBOX\r\n");
        readStringUntil(channel, s -> s.contains("s OK"));
        return channel;
    }

    @Test
    void spooledSearchShouldNotKeepItsLiteralOpen() throws Exception {
        assumeThat(Files.isDirectory(Path.of("/proc/self/fd"))).isTrue();
        Set<String> openBefore = openBufferedLiterals();
        SocketChannel channel = connectLoginAndSelect();
        String text = "a".repeat(OVERSIZED);

        write(channel, "a1 SEARCH TEXT {" + text.length() + "+}\r\n" + text + "\r\n");

        assertThat(readStringUntil(channel, s -> s.contains("a1 ")))
            .anyMatch(s -> s.contains("a1 OK"));
        awaitNoLeftoverLiteral();
        await().atMost(AWAIT_TIMEOUT)
            .untilAsserted(() -> assertThat(Sets.difference(openBufferedLiterals(), openBefore)).isEmpty());
        channel.close();
    }
}
