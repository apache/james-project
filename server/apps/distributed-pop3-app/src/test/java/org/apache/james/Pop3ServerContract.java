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

import java.io.Reader;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.commons.net.pop3.POP3Client;
import org.apache.commons.net.pop3.POP3MessageInfo;
import org.apache.james.backends.cassandra.TestingSession;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.core.Username;
import org.apache.james.jmap.JMAPTestingConstants;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.Pop3GuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.pop3server.mailbox.CassandraPop3MetadataStore;
import org.apache.james.pop3server.mailbox.Pop3MetadataStore;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.SMTPMessageSender;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.consumers.ThrowingConsumer;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

import reactor.core.publisher.Mono;

public interface Pop3ServerContract {
    class POP3ViewProbe implements GuiceProbe {
        private final CassandraPop3MetadataStore pop3MetadataStore;
        private final MailboxManager mailboxManager;
        private final MessageId.Factory messageIdFactory;

        @Inject
        private POP3ViewProbe(CassandraPop3MetadataStore pop3MetadataStore, MailboxManager mailboxManager, MessageId.Factory messageIdFactory) {
            this.pop3MetadataStore = pop3MetadataStore;
            this.mailboxManager = mailboxManager;
            this.messageIdFactory = messageIdFactory;
        }

        public void insertDandlingMetadata(MailboxPath mailboxPath) throws Exception {
            MailboxSession session = mailboxManager.createSystemSession(mailboxPath.getUser());
            MessageManager mailbox = mailboxManager.getMailbox(mailboxPath, session);

            Mono.from(pop3MetadataStore.add(mailbox.getId(), new Pop3MetadataStore.StatMetadata(messageIdFactory.generate(), 128))).block();
        }
    }

    class POP3ViewProbeModule extends AbstractModule {
        @Override
        protected void configure() {
            Multibinder.newSetBinder(binder(), GuiceProbe.class)
                .addBinding()
                .to(POP3ViewProbe.class);
        }

        @Provides
        @Singleton
        TestingSession provideSession(SessionWithInitializedTablesFactory factory) {
            return new TestingSession(factory.get());
        }
    }

    String USER = "bob@examplebis.local";
    String PASSWORD = "123456";
    String DOMAIN = "examplebis.local";

    @Test
    default void mailsCanBeReadInPop3(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);

        // Given a message in INBOX
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender(JMAPTestingConstants.DOMAIN);
        smtpMessageSender.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage("bob@" + JMAPTestingConstants.DOMAIN, USER);

        Awaitility.await().until(() ->
            server.getProbe(MailboxProbeImpl.class)
                .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.builder().build()).build(), USER, 1).size() == 1);

        // Them I can retrieve it in POP3
        POP3Client pop3Client = new POP3Client();
        pop3Client.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client.login(USER, PASSWORD);
        List<POP3MessageInfo> pop3MessageInfos = ImmutableList.copyOf(pop3Client.listUniqueIdentifiers());
        assertThat(pop3MessageInfos).hasSize(1);
        Reader message = pop3Client.retrieveMessage(pop3MessageInfos.get(0).number);
        assertThat(CharStreams.toString(message)).contains("subject: test\r\n\r\ncontent");
        pop3Client.disconnect();
    }

    @Test
    default void dandlingMetadataShouldBeCleanedUpForFollowingSessions(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);
        MailboxPath inbox = MailboxPath.inbox(Username.of(Pop3ServerContract.USER));
        server.getProbe(MailboxProbeImpl.class)
            .createMailbox(inbox);

        // Given a dandling view entry with no backing message
        server.getProbe(POP3ViewProbe.class)
            .insertDandlingMetadata(inbox);

        // When I retrieve it a first time it fails
        POP3Client pop3Client = new POP3Client();
        pop3Client.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client.login(USER, PASSWORD);
        List<POP3MessageInfo> pop3MessageInfos = ImmutableList.copyOf(pop3Client.listUniqueIdentifiers());
        assertThat(pop3MessageInfos).hasSize(1);
        assertThat(pop3Client.retrieveMessage(pop3MessageInfos.get(0).number)).isNull();

        // Then subsequent POP3 session do not encounter the inconsistency
        POP3Client pop3Client2 = new POP3Client();
        pop3Client2.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client2.login(USER, PASSWORD);
        List<POP3MessageInfo> pop3MessageInfos2 = ImmutableList.copyOf(pop3Client2.listUniqueIdentifiers());
        assertThat(pop3MessageInfos2).isEmpty();
        pop3Client.disconnect();
        pop3Client2.disconnect();
    }

    @Test
    default void manyMessagesCanBeRetrievedWithPOP3(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);

        // Given 50 messages in INBOX
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender(JMAPTestingConstants.DOMAIN);
        smtpMessageSender.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort());

        IntStream.range(0, 50)
            .forEach(Throwing.intConsumer(i -> smtpMessageSender.sendMessage("bob@" + JMAPTestingConstants.DOMAIN, USER)));

        Awaitility.await().until(() ->
            server.getProbe(MailboxProbeImpl.class)
                .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.builder().build()).build(), USER, 50).size() == 50);

        // Them I can retrieve them in POP3
        POP3Client pop3Client = new POP3Client();
        pop3Client.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client.login(USER, PASSWORD);
        List<POP3MessageInfo> pop3MessageInfos = ImmutableList.copyOf(pop3Client.listUniqueIdentifiers());
        assertThat(pop3MessageInfos).hasSize(50);
        // And all messages should be retrievable
        IntStream.range(0, 50).forEach(Throwing.intConsumer(i -> {
            Reader message = pop3Client.retrieveMessage(pop3MessageInfos.get(i).number);
            assertThat(CharStreams.toString(message)).contains("subject: test\r\n\r\ncontent");
        }));

        pop3Client.disconnect();
    }

    @Test
    default void aMailWithAnAttachmentCanBeRetrieved(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);

        // Given 50 messages in INBOX
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender(JMAPTestingConstants.DOMAIN);
        smtpMessageSender.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort());
        smtpMessageSender.sendMessageWithHeaders("bob@" + JMAPTestingConstants.DOMAIN, USER,
            ClassLoaderUtils.getSystemResourceAsString("attachment.eml"));

        Awaitility.await().until(() ->
            server.getProbe(MailboxProbeImpl.class)
                .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.builder().build()).build(), USER, 1).size() == 1);

        // Them I can retrieve them in POP3
        POP3Client pop3Client = new POP3Client();
        pop3Client.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client.login(USER, PASSWORD);
        List<POP3MessageInfo> pop3MessageInfos = ImmutableList.copyOf(pop3Client.listUniqueIdentifiers());
        assertThat(pop3MessageInfos).hasSize(1);
        Reader message = pop3Client.retrieveMessage(pop3MessageInfos.get(0).number);
        assertThat(CharStreams.toString(message)).endsWith(ClassLoaderUtils.getSystemResourceAsString("attachment.eml"));
        pop3Client.disconnect();
    }

    @Test
    default void pop3SizeShouldMatch(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);

        // Given one message
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender(JMAPTestingConstants.DOMAIN);
        smtpMessageSender.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort());
        smtpMessageSender.sendMessageWithHeaders("bob@" + JMAPTestingConstants.DOMAIN, USER,
            ClassLoaderUtils.getSystemResourceAsString("attachment.eml"));

        Awaitility.await().until(() ->
            server.getProbe(MailboxProbeImpl.class)
                .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.builder().build()).build(), USER, 1).size() == 1);

        // Then the advertized size is the downloadable size
        POP3Client pop3Client = new POP3Client();
        pop3Client.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client.login(USER, PASSWORD);
        List<POP3MessageInfo> pop3MessageInfos = ImmutableList.copyOf(pop3Client.listMessages());
        Reader message = pop3Client.retrieveMessage(pop3MessageInfos.get(0).number);
        assertThat(pop3MessageInfos).hasSize(1);
        assertThat(pop3MessageInfos.get(0).size).isEqualTo(CharStreams.toString(message).length());
        pop3Client.disconnect();
    }

    @Test
    default void mailReceivedDuringPOP3TransactionShouldBeIgnored(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);

        // Given a TRANSACTION POP3 session
        POP3Client pop3Client = new POP3Client();
        pop3Client.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client.login(USER, PASSWORD);

        // When a message is received
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender(JMAPTestingConstants.DOMAIN);
        smtpMessageSender.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort());
        smtpMessageSender.sendMessageWithHeaders("bob@" + JMAPTestingConstants.DOMAIN, USER,
            ClassLoaderUtils.getSystemResourceAsString("attachment.eml"));

        Awaitility.await().until(() ->
            server.getProbe(MailboxProbeImpl.class)
                .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.builder().build()).build(), USER, 1).size() == 1);

        // Then the message is not shown for existing POP3 sessions
        List<POP3MessageInfo> pop3MessageInfos = ImmutableList.copyOf(pop3Client.listMessages());
        assertThat(pop3MessageInfos).isEmpty();
        pop3Client.disconnect();
    }

    @Test
    default void rsetShouldRefreshPOP3Session(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);

        // Given a TRANSACTION POP3 session
        POP3Client pop3Client = new POP3Client();
        pop3Client.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client.login(USER, PASSWORD);

        // When a message is received
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender(JMAPTestingConstants.DOMAIN);
        smtpMessageSender.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort());
        smtpMessageSender.sendMessageWithHeaders("bob@" + JMAPTestingConstants.DOMAIN, USER,
            ClassLoaderUtils.getSystemResourceAsString("attachment.eml"));

        Awaitility.await().until(() ->
            server.getProbe(MailboxProbeImpl.class)
                .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.builder().build()).build(), USER, 1).size() == 1);

        // Then the message is not shown for existing POP3 sessions
        pop3Client.reset();
        List<POP3MessageInfo> pop3MessageInfos = ImmutableList.copyOf(pop3Client.listMessages());
        assertThat(pop3MessageInfos).hasSize(1);
        pop3Client.disconnect();
    }

    @Test
    default void rsetShouldCancelDeletes(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);

        // Given a message is received
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender(JMAPTestingConstants.DOMAIN);
        smtpMessageSender.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort());
        smtpMessageSender.sendMessageWithHeaders("bob@" + JMAPTestingConstants.DOMAIN, USER,
            ClassLoaderUtils.getSystemResourceAsString("attachment.eml"));

        Awaitility.await().until(() ->
            server.getProbe(MailboxProbeImpl.class)
                .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.builder().build()).build(), USER, 1).size() == 1);

        // When I delete it in POP3
        POP3Client pop3Client = new POP3Client();
        pop3Client.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client.login(USER, PASSWORD);
        List<POP3MessageInfo> pop3MessageInfos = ImmutableList.copyOf(pop3Client.listMessages());
        assertThat(pop3MessageInfos).hasSize(1);
        pop3Client.deleteMessage(pop3MessageInfos.get(0).number);

        // Then RSET should cancen the delete
        pop3Client.reset();
        assertThat(ImmutableList.copyOf(pop3Client.listMessages())).hasSize(1);
        pop3Client.disconnect();
        pop3Client.disconnect();
    }

    @Test
    default void deletesAreImmediateWithinASession(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);

        // Given a message is received
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender(JMAPTestingConstants.DOMAIN);
        smtpMessageSender.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort());
        smtpMessageSender.sendMessageWithHeaders("bob@" + JMAPTestingConstants.DOMAIN, USER,
            ClassLoaderUtils.getSystemResourceAsString("attachment.eml"));

        Awaitility.await().until(() ->
            server.getProbe(MailboxProbeImpl.class)
                .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.builder().build()).build(), USER, 1).size() == 1);

        // When I delete it in POP3
        POP3Client pop3Client = new POP3Client();
        pop3Client.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client.login(USER, PASSWORD);
        List<POP3MessageInfo> pop3MessageInfos = ImmutableList.copyOf(pop3Client.listMessages());
        assertThat(pop3MessageInfos).hasSize(1);
        pop3Client.deleteMessage(pop3MessageInfos.get(0).number);

        // Then RSET should cancen the delete
        assertThat(ImmutableList.copyOf(pop3Client.listMessages())).isEmpty();
        pop3Client.disconnect();
    }

    @Test
    default void pendingDeletesAreNotSeenByOtherSessions(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);

        // Given a message is received
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender(JMAPTestingConstants.DOMAIN);
        smtpMessageSender.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort());
        smtpMessageSender.sendMessageWithHeaders("bob@" + JMAPTestingConstants.DOMAIN, USER,
            ClassLoaderUtils.getSystemResourceAsString("attachment.eml"));

        Awaitility.await().until(() ->
            server.getProbe(MailboxProbeImpl.class)
                .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.builder().build()).build(), USER, 1).size() == 1);

        // When I delete it in POP3
        POP3Client pop3Client = new POP3Client();
        pop3Client.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client.login(USER, PASSWORD);
        List<POP3MessageInfo> pop3MessageInfos = ImmutableList.copyOf(pop3Client.listMessages());
        assertThat(pop3MessageInfos).hasSize(1);
        pop3Client.deleteMessage(pop3MessageInfos.get(0).number);

        // Then other sessions can still retrieve them
        POP3Client pop3Client2 = new POP3Client();
        pop3Client2.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client2.login(USER, PASSWORD);
        List<POP3MessageInfo> pop3MessageInfos2 = ImmutableList.copyOf(pop3Client2.listUniqueIdentifiers());
        assertThat(pop3MessageInfos2).hasSize(1);
        pop3Client.disconnect();
        pop3Client2.disconnect();
    }

    @Test
    default void disconnectsShouldNotPerformPendingDeletes(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);

        // Given a message is received
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender(JMAPTestingConstants.DOMAIN);
        smtpMessageSender.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort());
        smtpMessageSender.sendMessageWithHeaders("bob@" + JMAPTestingConstants.DOMAIN, USER,
            ClassLoaderUtils.getSystemResourceAsString("attachment.eml"));

        Awaitility.await().until(() ->
            server.getProbe(MailboxProbeImpl.class)
                .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.builder().build()).build(), USER, 1).size() == 1);

        // When I delete it in POP3
        POP3Client pop3Client = new POP3Client();
        pop3Client.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client.login(USER, PASSWORD);
        List<POP3MessageInfo> pop3MessageInfos = ImmutableList.copyOf(pop3Client.listMessages());
        assertThat(pop3MessageInfos).hasSize(1);
        pop3Client.deleteMessage(pop3MessageInfos.get(0).number);
        pop3Client.disconnect();

        // Then other sessions can still retrieve them
        POP3Client pop3Client2 = new POP3Client();
        pop3Client2.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client2.login(USER, PASSWORD);
        List<POP3MessageInfo> pop3MessageInfos2 = ImmutableList.copyOf(pop3Client2.listUniqueIdentifiers());
        assertThat(pop3MessageInfos2).hasSize(1);
        pop3Client.disconnect();
    }

    @Test
    default void linesStartingWithDotShouldBeWellHandled(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);

        // Given one message
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender(JMAPTestingConstants.DOMAIN);
        smtpMessageSender.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort());
        String content = "subject: test\r\n" +
            "\r\n" +
            ".content\r\n";
        smtpMessageSender.sendMessageWithHeaders("bob@" + JMAPTestingConstants.DOMAIN, USER,
            content);

        Awaitility.await().until(() ->
            server.getProbe(MailboxProbeImpl.class)
                .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.builder().build()).build(), USER, 1).size() == 1);

        // Then the advertized size is the downloadable size
        POP3Client pop3Client = new POP3Client();
        pop3Client.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client.login(USER, PASSWORD);
        List<POP3MessageInfo> pop3MessageInfos = ImmutableList.copyOf(pop3Client.listMessages());
        Reader message = pop3Client.retrieveMessage(pop3MessageInfos.get(0).number);
        assertThat(CharStreams.toString(message)).endsWith(content);
        pop3Client.disconnect();
    }

    @Test
    default void aBigMailCanBeRetrieved(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);

        // Given 50 messages in INBOX
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender(JMAPTestingConstants.DOMAIN);
        smtpMessageSender.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort());
        smtpMessageSender.sendMessageWithHeaders("bob@" + JMAPTestingConstants.DOMAIN, USER,
            ClassLoaderUtils.getSystemResourceAsString("big.eml"));

        Awaitility.await().until(() ->
            server.getProbe(MailboxProbeImpl.class)
                .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.builder().build()).build(), USER, 1).size() == 1);

        // Them I can retrieve them in POP3
        POP3Client pop3Client = new POP3Client();
        pop3Client.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client.login(USER, PASSWORD);
        List<POP3MessageInfo> pop3MessageInfos = ImmutableList.copyOf(pop3Client.listUniqueIdentifiers());
        assertThat(pop3MessageInfos).hasSize(1);
        Reader message = pop3Client.retrieveMessage(pop3MessageInfos.get(0).number);
        assertThat(CharStreams.toString(message))
            .endsWith(ClassLoaderUtils.getSystemResourceAsString("big.eml"));
        pop3Client.disconnect();
    }

    @Test
    default void aMailCanBeSentToManyRecipients(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);
        List<String> users = generateNUsers(10);
        users.forEach((ThrowingConsumer<String>) user -> server.getProbe(DataProbeImpl.class)
            .fluent()
            .addUser(user, PASSWORD));


        // Given 50 messages in INBOX
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender(JMAPTestingConstants.DOMAIN);
        smtpMessageSender.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort());
        smtpMessageSender.sendMessageWithHeaders("bob@" + JMAPTestingConstants.DOMAIN, users,
            ClassLoaderUtils.getSystemResourceAsString("attachment.eml"));

        users.forEach((ThrowingConsumer<String>) user -> {
            Awaitility.await().until(() ->
                server.getProbe(MailboxProbeImpl.class)
                    .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.builder().build()).build(), user, 1).size() == 1);

            // Them I can retrieve them in POP3
            POP3Client pop3Client = new POP3Client();
            pop3Client.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
            pop3Client.login(user, PASSWORD);
            List<POP3MessageInfo> pop3MessageInfos = ImmutableList.copyOf(pop3Client.listUniqueIdentifiers());
            assertThat(pop3MessageInfos).hasSize(1);
            pop3Client.disconnect();
        });
    }

    private List<String> generateNUsers(int nbUsers) {
        return IntStream.range(0, nbUsers)
            .boxed()
            .map(index -> "user" + index + "@" + DOMAIN)
            .collect(ImmutableList.toImmutableList());
    }

    @Test
    default void messageCanBeDeleted(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);

        // Given a message in INBOX
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender(JMAPTestingConstants.DOMAIN);
        smtpMessageSender.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage("bob@" + JMAPTestingConstants.DOMAIN, USER);

        Awaitility.await().until(() ->
            server.getProbe(MailboxProbeImpl.class)
                .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.builder().build()).build(), USER, 1).size() == 1);

        // When I connect in POP3 and delete it
        POP3Client pop3Client = new POP3Client();
        pop3Client.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client.login(USER, PASSWORD);
        pop3Client.deleteMessage(pop3Client.listUniqueIdentifiers()[0].number);
        pop3Client.logout();

        // Then subsequent POP3 sessions do not read it
        POP3Client pop3Client2 = new POP3Client();
        pop3Client2.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client2.login(USER, PASSWORD);
        assertThat(pop3Client2.listUniqueIdentifiers()).isEmpty();
        pop3Client2.disconnect();
    }

    @Test
    default void manyMessagesCanBeDeletedWithPOP3(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);

        // Given 50 messages in INBOX
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender(JMAPTestingConstants.DOMAIN);
        smtpMessageSender.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort());

        IntStream.range(0, 50)
            .forEach(Throwing.intConsumer(i -> smtpMessageSender.sendMessage("bob@" + JMAPTestingConstants.DOMAIN, USER)));

        Awaitility.await().until(() ->
            server.getProbe(MailboxProbeImpl.class)
                .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.builder().build()).build(), USER, 50).size() == 50);

        // When I delete all of them
        POP3Client pop3Client = new POP3Client();
        pop3Client.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client.login(USER, PASSWORD);
        List<POP3MessageInfo> pop3MessageInfos = ImmutableList.copyOf(pop3Client.listUniqueIdentifiers());
        assertThat(pop3MessageInfos).hasSize(50);
        pop3MessageInfos.forEach(Throwing.consumer(info -> pop3Client.deleteMessage(info.number)));
        pop3Client.logout();

        // Then subsequent POP3 sessions do not read it
        POP3Client pop3Client2 = new POP3Client();
        pop3Client2.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client2.login(USER, PASSWORD);
        assertThat(pop3Client2.listUniqueIdentifiers()).isEmpty();
        pop3Client2.disconnect();
    }

    @Test
    default void deletingAMessageDeletesOnlyOne(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);

        // Given 50 messages in INBOX
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender(JMAPTestingConstants.DOMAIN);
        smtpMessageSender.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort());

        var mailCount = 50;
        IntStream.range(0, mailCount)
            .forEach(Throwing.intConsumer(i -> smtpMessageSender.sendMessage("bob@" + JMAPTestingConstants.DOMAIN, USER)));

        Awaitility.await().until(() ->
            server.getProbe(MailboxProbeImpl.class)
                .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.builder().build()).build(), USER, mailCount).size() == mailCount);

        // When I delete all of them
        POP3Client pop3Client = new POP3Client();
        pop3Client.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client.login(USER, PASSWORD);
        List<POP3MessageInfo> pop3MessageInfos = ImmutableList.copyOf(pop3Client.listUniqueIdentifiers());
        assertThat(pop3MessageInfos).hasSize(mailCount);
        pop3Client.deleteMessage(pop3MessageInfos.get(45).number);
        pop3Client.logout();

        // Then subsequent POP3 sessions do not read it
        POP3Client pop3Client2 = new POP3Client();
        pop3Client2.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client2.login(USER, PASSWORD);
        assertThat(pop3Client2.listUniqueIdentifiers()).hasSize(49);
        pop3Client2.disconnect();
    }

    @Test
    default void messagesShouldBeOrderedByReceivedDate(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);

        // Given two messages received
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender(JMAPTestingConstants.DOMAIN);
        SMTPMessageSender sender = smtpMessageSender.connect("127.0.0.1", server.getProbe(SmtpGuiceProbe.class).getSmtpPort());
        sender.sendMessageWithHeaders("bob@" + JMAPTestingConstants.DOMAIN, USER,
            "Subject: Message 1\r\n\r\nBody 1");
        Awaitility.await().until(() ->
            server.getProbe(MailboxProbeImpl.class)
                .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.builder().build()).build(), USER, 1).size() == 1);

        sender.sendMessageWithHeaders("bob@" + JMAPTestingConstants.DOMAIN, USER,
            "Subject: Message 2\r\n\r\nBody 2");
        Awaitility.await().until(() ->
            server.getProbe(MailboxProbeImpl.class)
                .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.builder().build()).build(), USER, 2).size() == 2);

        // When I connect in POP3 and read them
        POP3Client pop3Client = new POP3Client();
        pop3Client.connect("127.0.0.1", server.getProbe(Pop3GuiceProbe.class).getPop3Port());
        pop3Client.login(USER, PASSWORD);
        POP3MessageInfo[] pop3MessageInfos = pop3Client.listUniqueIdentifiers();

        // then they are ordered by received at
        assertThat(pop3MessageInfos).hasSize(2);
        Reader message1 = pop3Client.retrieveMessage(pop3MessageInfos[0].number);
        assertThat(CharStreams.toString(message1)).contains("Subject: Message 1");
        Reader message2 = pop3Client.retrieveMessage(pop3MessageInfos[1].number);
        assertThat(CharStreams.toString(message2)).contains("Subject: Message 2");
        pop3Client.disconnect();
    }
}
