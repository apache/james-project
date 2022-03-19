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

package org.apache.james.smtp;

import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.calmlyAwait;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.TEN_SECONDS;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import jakarta.mail.MessagingException;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.server.core.MailImpl;
import org.apache.james.transport.mailets.RandomStoring;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.mailet.Mail;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

class SmtpRandomStoringTest {
    private static final String FROM = "from@" + DEFAULT_DOMAIN;
    private static final String TO = "to@any.com";
    private static final Long USERS_NUMBERS = 10L;
    private static final ConditionFactory awaitAtMostTenSeconds = calmlyAwait
        .atMost(TEN_SECONDS);

    private static final ImmutableList<String> USERS = LongStream.range(0L, USERS_NUMBERS)
        .boxed()
        .map(index -> "user" + index + "@" + DEFAULT_DOMAIN)
        .collect(ImmutableList.toImmutableList());

    private static final ImmutableList<String> MAILBOXES = ImmutableList.of(MailboxConstants.INBOX, "arbitrary");
    private static final int NUMBER_OF_MAILS = 100;
    private static final MailetConfiguration RANDOM_STORING = MailetConfiguration.builder()
            .matcher(All.class)
            .mailet(RandomStoring.class)
            .build();

    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;
    private ImapGuiceProbe imapProbe;
    private Collection<TestIMAPClient> connections;

    @BeforeEach
    void setUp(@TempDir File temporaryFolder) throws Exception {
        createJamesServer(temporaryFolder);

        createUsersAndMailboxes();

        imapProbe = jamesServer.getProbe(ImapGuiceProbe.class);
        connections = ImmutableList.of();
    }

    private void createUsersAndMailboxes() throws Exception {
        MailboxProbeImpl mailboxes = jamesServer.getProbe(MailboxProbeImpl.class);
        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        USERS.forEach(user -> populateUser(mailboxes, dataProbe, user));
        awaitAtMostTenSeconds
            .until(() -> USERS
                .stream()
                .map(mailboxes::listUserMailboxes)
                .allMatch(userMailboxes -> userMailboxes.size() == MAILBOXES.size()));

        Thread.sleep(500);

        sendMails();
    }

    private static void populateUser(MailboxProbeImpl mailboxProbe, DataProbe dataProbe, String user) {
        try {
            dataProbe.addUser(user, PASSWORD);
            MAILBOXES.forEach(mailbox -> mailboxProbe.createMailbox(MailboxPath.forUser(Username.of(user), mailbox)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createJamesServer(File temporaryFolder) throws Exception {
        MailetContainer.Builder mailetContainer = TemporaryJamesServer.simpleMailetContainerConfiguration()
            .putProcessor(ProcessorConfiguration.transport()
                .addMailet(RANDOM_STORING)
                .addMailetsFrom(CommonProcessors.deliverOnlyTransport()));

        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder);
        jamesServer.start();
    }

    private void sendMails() throws Exception {
        try (SMTPMessageSender authenticatedSmtpConnection = messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())) {

            authenticatedSmtpConnection.authenticate(FROM, PASSWORD);

            IntStream.range(0, NUMBER_OF_MAILS)
                .forEach(Throwing.intConsumer(index ->
                    authenticatedSmtpConnection
                        .sendMessage(buildMail("Message " + index))).sneakyThrow());
        }
    }

    @AfterEach
    void tearDown() {
        connections.forEach(Throwing.consumer(TestIMAPClient::close).sneakyThrow());
        jamesServer.shutdown();
    }

    @Test
    void oneHundredMailsShouldHaveBeenStoredBetweenFourAndEightTimes() {
        connections = USERS
            .stream()
            .map(this::createIMAPConnection)
            .collect(ImmutableList.toImmutableList());

        awaitAtMostTenSeconds
            .untilAsserted(() -> checkNumberOfMessages(connections));
    }

    @Test
    void messagesShouldBeRandomlyAssignedToEveryMailboxesOfEveryUsers() {
        connections = USERS
            .stream()
            .map(this::createIMAPConnection)
            .collect(ImmutableList.toImmutableList());

        awaitAtMostTenSeconds
            .untilAsserted(() -> checkMailboxesHaveBeenFilled(connections));
    }

    private TestIMAPClient createIMAPConnection(String username) {
        try {
            TestIMAPClient reader = new TestIMAPClient();
            reader
                .connect(LOCALHOST_IP, imapProbe.getImapPort())
                .login(username, PASSWORD);
            return reader;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkNumberOfMessages(Collection<TestIMAPClient> connections) {
        assertThat(connections
            .stream()
            .flatMapToLong(this::numberOfAUserMessages)
            .sum())
            .isBetween(NUMBER_OF_MAILS * 4L, NUMBER_OF_MAILS * 8L);
    }

    private void checkMailboxesHaveBeenFilled(Collection<TestIMAPClient> connections) {
        connections
            .stream()
            .forEach(this::checkUserMailboxes);
    }

    private LongStream numberOfAUserMessages(TestIMAPClient testIMAPClient) {
        return MAILBOXES
            .stream()
            .mapToLong(mailbox -> numberOfMessagesInMailbox(testIMAPClient, mailbox));
    }

    private void checkUserMailboxes(TestIMAPClient testIMAPClient) {
        assertThat(MAILBOXES
            .stream()
            .map(mailbox -> numberOfMessagesInMailbox(testIMAPClient, mailbox)))
            .allMatch(numberOfMessages -> numberOfMessages > 0, "Some mailboxes are empty");
    }

    private Long numberOfMessagesInMailbox(TestIMAPClient testIMAPClient, String mailbox) {
        try {
            return testIMAPClient
                .getMessageCount(mailbox);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Mail buildMail(String subject) throws MessagingException {
        return MailImpl.builder()
                .name(subject)
                .sender(FROM)
                .addRecipient(TO)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject(subject)
                    .setText("content"))
                .build();
    }
}

