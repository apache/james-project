/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.FIVE_MINUTES;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;

import org.apache.james.core.Domain;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SpoolerProbe;
import org.apache.james.utils.TestIMAPClient;
import org.apache.mailet.base.test.FakeMail;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.consumers.ThrowingConsumer;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public interface MailsShouldBeWellReceived {
    int imapPort(GuiceJamesServer server);

    int smtpPort(GuiceJamesServer server);

    String JAMES_SERVER_HOST = "127.0.0.1";
    String DOMAIN = "apache.org";
    String JAMES_USER = "james-user@" + DOMAIN;
    String OTHER_USER = "other-user@" + DOMAIN;
    String PASSWORD = "secret";
    String PASSWORD_OTHER = "other-secret";
    ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    ConditionFactory CALMLY_AWAIT_FIVE_MINUTE = CALMLY_AWAIT.timeout(FIVE_MINUTES);
    String SENDER = "bob@apache.org";
    String UNICODE_BODY = "Unicode €uro symbol.";
    ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    static Message[] searchForAll(Folder inbox) throws MessagingException {
        return inbox.search(new FlagTerm(new Flags(), false));
    }

    @Test
    default void mailsContentWithUnicodeCharactersShouldBeKeptUnChanged(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD)
            .addUser(SENDER, PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER, DefaultMailboxes.INBOX);

        Port smtpPort = Port.of(smtpPort(server));
        try (SMTPMessageSender sender = new SMTPMessageSender(Domain.LOCALHOST.asString())) {
            sender.connect(JAMES_SERVER_HOST, smtpPort);
            MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream("eml/mail-containing-unicode-characters.eml"));

            FakeMail.Builder mail = FakeMail.builder()
                .name("test-unicode-body")
                .sender(SENDER)
                .recipient(JAMES_USER)
                .mimeMessage(mimeMessage);

            sender.authenticate(SENDER, PASSWORD).sendMessage(mail);
        }

        CALMLY_AWAIT.until(() -> server.getProbe(SpoolerProbe.class).processingFinished());

        try (TestIMAPClient reader = new TestIMAPClient()) {
            int imapPort = imapPort(server);
            reader.connect(JAMES_SERVER_HOST, imapPort)
                .login(JAMES_USER, PASSWORD)
                .select(TestIMAPClient.INBOX)
                .awaitMessageCount(CALMLY_AWAIT, 1);

            assertThat(reader.readFirstMessage())
                .contains(UNICODE_BODY);
        }
    }

    @Test
    default void mailsShouldBeWellReceived(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD)
            .addUser(SENDER, PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER, DefaultMailboxes.INBOX);

        Port smtpPort = Port.of(smtpPort(server));
        String message = Resources.toString(Resources.getResource("eml/htmlMail.eml"), StandardCharsets.UTF_8);

        try (SMTPMessageSender sender = new SMTPMessageSender(Domain.LOCALHOST.asString())) {
            sender.connect(JAMES_SERVER_HOST, smtpPort).authenticate(SENDER, PASSWORD);
            sendUniqueMessage(sender, message);
        }

        CALMLY_AWAIT.until(() -> server.getProbe(SpoolerProbe.class).processingFinished());

        try (TestIMAPClient reader = new TestIMAPClient()) {
            reader.connect(JAMES_SERVER_HOST, imapPort(server))
                .login(JAMES_USER, PASSWORD)
                .select(TestIMAPClient.INBOX)
                .awaitMessageCount(CALMLY_AWAIT, 1);
        }

    }

    @Test
    default void mailsShouldBeWellReceivedByBothRecipient(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD)
            .addUser(OTHER_USER, PASSWORD_OTHER)
            .addUser(SENDER, PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER, DefaultMailboxes.INBOX);
        mailboxProbe.createMailbox("#private", OTHER_USER, DefaultMailboxes.INBOX);

        Port smtpPort = Port.of(smtpPort(server));
        String message = Resources.toString(Resources.getResource("eml/htmlMail.eml"), StandardCharsets.UTF_8);

        try (SMTPMessageSender sender = new SMTPMessageSender(Domain.LOCALHOST.asString())) {
            sender.connect(JAMES_SERVER_HOST, smtpPort).authenticate(SENDER, PASSWORD);
            sendUniqueMessageToTwoUsers(sender, message);
        }

        CALMLY_AWAIT.untilAsserted(() -> assertThat(server.getProbe(SpoolerProbe.class).processingFinished()).isTrue());

        try (TestIMAPClient reader = new TestIMAPClient()) {
            reader.connect(JAMES_SERVER_HOST, imapPort(server))
                .login(JAMES_USER, PASSWORD)
                .select(TestIMAPClient.INBOX)
                .awaitMessageCount(CALMLY_AWAIT, 1);
            reader.connect(JAMES_SERVER_HOST, imapPort(server))
                .login(OTHER_USER, PASSWORD_OTHER)
                .select(TestIMAPClient.INBOX)
                .awaitMessageCount(CALMLY_AWAIT, 1);
        }

    }

    @Test
    default void mailsShouldBeWellReceivedByTenRecipient(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD)
            .addUser(SENDER, PASSWORD);

        ImmutableList<String> users = generateNUsers(10);

        users.forEach((ThrowingConsumer<String>) user -> server.getProbe(DataProbeImpl.class)
                        .fluent()
                        .addUser(user, PASSWORD));

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER, DefaultMailboxes.INBOX);
        users.forEach((ThrowingConsumer<String>) user ->
                        mailboxProbe.createMailbox("#private", user, DefaultMailboxes.INBOX));


        Port smtpPort = Port.of(smtpPort(server));
        String message = Resources.toString(Resources.getResource("eml/htmlMail.eml"), StandardCharsets.UTF_8);

        try (SMTPMessageSender sender = new SMTPMessageSender(Domain.LOCALHOST.asString())) {
            sender.connect(JAMES_SERVER_HOST, smtpPort).authenticate(SENDER, PASSWORD);
            sendUniqueMessageToUsers(sender, message, users);
        }

        CALMLY_AWAIT_FIVE_MINUTE.untilAsserted(() ->
            assertThat(
                server
                    .getProbe(SpoolerProbe.class)
                    .processingFinished()
            ).isTrue());

        try (TestIMAPClient reader = new TestIMAPClient()) {
            users.forEach((ThrowingConsumer<String>) user -> reader
                .connect(JAMES_SERVER_HOST, imapPort(server))
                .login(user, PASSWORD)
                .select(TestIMAPClient.INBOX)
                .awaitMessageCount(CALMLY_AWAIT, 1));
        }
    }

    @Test
    default void oneHundredMailsShouldBeWellReceived(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD)
            .addUser(SENDER, PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER, DefaultMailboxes.INBOX);

        int messageCount = 100;

        Port smtpPort = Port.of(smtpPort(server));
        String message = Resources.toString(Resources.getResource("eml/htmlMail.eml"), StandardCharsets.UTF_8);

        try (SMTPMessageSender sender = new SMTPMessageSender(Domain.LOCALHOST.asString())) {
            Mono.fromRunnable(
                Throwing.runnable(() -> {
                    sender.connect(JAMES_SERVER_HOST, smtpPort).authenticate(SENDER, PASSWORD);
                    sendUniqueMessage(sender, message);
            }))
                .repeat(messageCount - 1)
                .subscribeOn(Schedulers.fromExecutor(EXECUTOR))
                .blockLast();
        }

        CALMLY_AWAIT_FIVE_MINUTE.until(() -> server.getProbe(SpoolerProbe.class).processingFinished());

        try (TestIMAPClient reader = new TestIMAPClient()) {
            reader.connect(JAMES_SERVER_HOST, imapPort(server))
                .login(JAMES_USER, PASSWORD)
                .select(TestIMAPClient.INBOX)
                .awaitMessageCount(CALMLY_AWAIT, messageCount);
        }
    }

    default void sendUniqueMessage(SMTPMessageSender sender, String message) throws IOException {
        String uniqueMessage = message.replace("banana", "UUID " + UUID.randomUUID().toString());
        sender.sendMessageWithHeaders("bob@apache.org", JAMES_USER, uniqueMessage);
    }

    default void sendUniqueMessageToTwoUsers(SMTPMessageSender sender, String message) throws IOException {
        sendUniqueMessageToUsers(sender, message, ImmutableList.of(JAMES_USER, OTHER_USER));
    }

    default void sendUniqueMessageToUsers(SMTPMessageSender sender, String message, ImmutableList<String> users) throws IOException {
        String uniqueMessage = message.replace("banana", "UUID " + UUID.randomUUID().toString());
        sender.sendMessageWithHeaders("bob@apache.org", users, uniqueMessage);
    }

    default ImmutableList<String> generateNUsers(int nbUsers) {
        return IntStream.range(0, nbUsers)
                .boxed()
                .map(index -> "user" + index + "@" + DOMAIN)
                .collect(ImmutableList.toImmutableList());
    }

}
