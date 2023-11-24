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

package org.apache.james.lmtpserver;

import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.apache.mailet.DsnParameters.Notify.DELAY;
import static org.apache.mailet.DsnParameters.Notify.FAILURE;
import static org.apache.mailet.DsnParameters.Notify.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.james.UserEntityValidator;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.lmtpserver.netty.LMTPServerFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.lib.mock.ConfigLoader;
import org.apache.james.protocols.lib.mock.MockProtocolHandlerLoader;
import org.apache.james.rrt.api.AliasReverseResolver;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.lib.AliasReverseResolverImpl;
import org.apache.james.rrt.lib.CanSendFromImpl;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.mailet.DsnParameters;
import org.apache.mailet.Mail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.inject.name.Names;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class LmtpServerTest {
    static class RecordingMailProcessor implements MailProcessor {
        private final ArrayList<Mail> mails = new ArrayList<>();

        @Override
        public void service(Mail mail) {
            mails.add(mail);
        }

        public List<Mail> getMails() {
            return mails;
        }
    }

    static class ThrowingMailProcessor implements MailProcessor {
        @Override
        public void service(Mail mail) {
            throw new RuntimeException("Oups");
        }
    }

    static int getLmtpPort(LMTPServerFactory lmtpServerFactory) {
        return lmtpServerFactory.getServers().stream()
            .findFirst()
            .flatMap(server -> server.getListenAddresses().stream().findFirst())
            .map(InetSocketAddress::getPort)
            .orElseThrow(() -> new IllegalStateException("LMTP server not defined"));
    }

    private MemoryDomainList domainList;
    private MemoryUsersRepository usersRepository;
    private InMemoryDNSService dnsService;
    private FileSystem fileSystem;
    private LMTPServerFactory lmtpServerFactory;

    @BeforeEach
    void setUpTestEnvironment() throws Exception {
        dnsService = new InMemoryDNSService()
            .registerMxRecord(Domain.LOCALHOST.asString(), "127.0.0.1")
            .registerMxRecord("examplebis.local", "127.0.0.1")
            .registerMxRecord("127.0.0.1", "127.0.0.1");
        domainList = new MemoryDomainList();
        domainList.addDomain(Domain.of("examplebis.local"));
        usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(Username.of("bob@examplebis.local"), "pwd");
        usersRepository.addUser(Username.of("cedric@examplebis.local"), "pwd");

        fileSystem = new FileSystemImpl(Configuration.builder()
            .workingDirectory("../")
            .configurationFromClasspath()
            .build().directories());
    }

    @AfterEach
    void tearDown() {
        lmtpServerFactory.destroy();
    }

    private MockProtocolHandlerLoader.Builder createMockProtocolHandlerLoaderBase() {
        MemoryRecipientRewriteTable rewriteTable = new MemoryRecipientRewriteTable();
        rewriteTable.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
        AliasReverseResolver aliasReverseResolver = new AliasReverseResolverImpl(rewriteTable);
        CanSendFrom canSendFrom = new CanSendFromImpl(rewriteTable, aliasReverseResolver);
        return MockProtocolHandlerLoader.builder()
            .put(binder -> binder.bind(DomainList.class).toInstance(domainList))
            .put(binder -> binder.bind(RecipientRewriteTable.class).toInstance(rewriteTable))
            .put(binder -> binder.bind(CanSendFrom.class).toInstance(canSendFrom))
            .put(binder -> binder.bind(FileSystem.class).toInstance(fileSystem))
            .put(binder -> binder.bind(DNSService.class).toInstance(dnsService))
            .put(binder -> binder.bind(UsersRepository.class).toInstance(usersRepository))
            .put(binder -> binder.bind(MetricFactory.class).to(RecordingMetricFactory.class))
            .put(binder -> binder.bind(UserEntityValidator.class).toInstance(UserEntityValidator.NOOP));
    }

    private LMTPServerFactory createLMTPServer(MockProtocolHandlerLoader loader, String configuration) throws Exception {
        LMTPServerFactory lmtpServerFactory = new LMTPServerFactory(loader, fileSystem, new RecordingMetricFactory());
        lmtpServerFactory.configure(ConfigLoader.getConfig(ClassLoader.getSystemResourceAsStream(configuration)));
        lmtpServerFactory.init();
        return lmtpServerFactory;
    }

    @Nested
    class MailetContainerTest {
        private RecordingMailProcessor recordingMailProcessor;

        @BeforeEach
        void setUp()  throws Exception {
            recordingMailProcessor = new RecordingMailProcessor();
            lmtpServerFactory = createLMTPServer(createMockProtocolHandlerLoaderBase()
                .put(binder -> binder.bind(MailProcessor.class).toInstance(recordingMailProcessor))
                .build(), "lmtpmailet.xml");
        }

        @Test
        void emailShouldTriggerTheMailProcessing() throws Exception {
            SocketChannel server = SocketChannel.open();
            server.connect(new InetSocketAddress(LOCALHOST_IP, getLmtpPort(lmtpServerFactory)));
            readBytes(server);

            server.write(ByteBuffer.wrap(("LHLO <" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("MAIL FROM: <bob@" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("RCPT TO: <bob@examplebis.local>\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("DATA\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server); // needed to synchronize
            server.write(ByteBuffer.wrap(("header:value\r\n\r\nbody").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap((".").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("QUIT\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);

            assertThat(recordingMailProcessor.getMails()).hasSize(1);
        }

        @Test
        void dataShouldHaveAReturnCodePerRecipient() throws Exception {
            SocketChannel server = SocketChannel.open();
            server.connect(new InetSocketAddress(LOCALHOST_IP, getLmtpPort(lmtpServerFactory)));
            readBytes(server);

            server.write(ByteBuffer.wrap(("LHLO <" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("MAIL FROM: <bob@" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("RCPT TO: <bob@examplebis.local>\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("RCPT TO: <cedric@examplebis.local>\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("DATA\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server); // needed to synchronize
            server.write(ByteBuffer.wrap(("header:value\r\n\r\nbody").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap((".").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
            byte[] dataResponse = readBytes(server);
            server.write(ByteBuffer.wrap(("QUIT\r\n").getBytes(StandardCharsets.UTF_8)));

            assertThat(new String(dataResponse, StandardCharsets.UTF_8))
                .contains("250 2.6.0 Message received <bob@examplebis.local>\r\n" +
                    "250 2.6.0 Message received <cedric@examplebis.local>");
        }
    }

    @Nested
    class DSNTest {
        private RecordingMailProcessor recordingMailProcessor;

        @BeforeEach
        void setUp()  throws Exception {
            recordingMailProcessor = new RecordingMailProcessor();
            lmtpServerFactory = createLMTPServer(createMockProtocolHandlerLoaderBase()
                .put(binder -> binder.bind(MailProcessor.class).toInstance(recordingMailProcessor))
                .build(), "lmtpdsn.xml");
        }

        @Test
        void emailShouldTriggerTheMailProcessing() throws Exception {
            SocketChannel server = SocketChannel.open();
            server.connect(new InetSocketAddress(LOCALHOST_IP, getLmtpPort(lmtpServerFactory)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("LHLO <" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("MAIL FROM: <bob@" + DOMAIN + "> RET=HDRS ENVID=QQ314159\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("RCPT TO: <bob@examplebis.local> NOTIFY=SUCCESS,FAILURE,DELAY ORCPT=rfc822;orcpt1@localhost\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("DATA\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("header:value\r\n\r\nbody").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap((".").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("QUIT\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);

            assertThat(recordingMailProcessor.getMails())
                .first()
                .extracting(Mail::dsnParameters)
                .satisfies(Throwing.consumer(maybeDSN -> assertThat(maybeDSN)
                    .isEqualTo(DsnParameters.builder()
                        .envId(DsnParameters.EnvId.of("QQ314159"))
                        .ret(DsnParameters.Ret.HDRS)
                        .addRcptParameter(new MailAddress("bob@examplebis.local"), DsnParameters.RecipientDsnParameters.of(
                            EnumSet.of(SUCCESS, FAILURE, DELAY), new MailAddress("orcpt1@localhost")))
                        .build())));
        }

        @Test
        void lhloShouldAdvertizeDSN() throws Exception {
            SocketChannel server = SocketChannel.open();
            server.connect(new InetSocketAddress(LOCALHOST_IP, getLmtpPort(lmtpServerFactory)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("LHLO <" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));

            assertThat(new String(readBytes(server), StandardCharsets.UTF_8)).contains("250 DSN\r\n");
        }
    }

    @Nested
    class NormalDSNTest {
        private InMemoryMailboxManager mailboxManager;

        @BeforeEach
        void setUp()  throws Exception {
            mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();

            lmtpServerFactory = createLMTPServer(createMockProtocolHandlerLoaderBase()
                .put(binder -> binder.bind(MailboxManager.class).annotatedWith(Names.named("mailboxmanager")).toInstance(mailboxManager))
                .build(), "lmtpnormaldsn.xml");
        }

        @Test
        void dsnMessagesShouldBeWellReceived() throws Exception {
            SocketChannel server = SocketChannel.open();
            server.connect(new InetSocketAddress(LOCALHOST_IP, getLmtpPort(lmtpServerFactory)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("LHLO <" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("MAIL FROM: <bob@" + DOMAIN + "> RET=HDRS ENVID=QQ314159\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("RCPT TO: <bob@examplebis.local> NOTIFY=SUCCESS,FAILURE,DELAY ORCPT=rfc822;orcpt1@localhost\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("DATA\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("header:value\r\n\r\nbody").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap((".").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("QUIT\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);

            Username username = Username.of("bob@examplebis.local");
            MailboxSession systemSession = mailboxManager.createSystemSession(username);
            assertThatCode(() ->
                assertThat(Flux.from(mailboxManager.getMailbox(MailboxPath.inbox(username), systemSession)
                    .listMessagesMetadata(MessageRange.all(), systemSession))
                    .count()
                    .block())
                    .isEqualTo(1))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class ThrowingTest {
        @BeforeEach
        void setUp()  throws Exception {
            lmtpServerFactory = createLMTPServer(createMockProtocolHandlerLoaderBase()
                .put(binder -> binder.bind(MailProcessor.class).toInstance(new ThrowingMailProcessor()))
                .build(), "lmtpmailet.xml");
        }

        @Test
        void emailShouldTriggerTheMailProcessing() throws Exception {
            SocketChannel server = SocketChannel.open();
            server.connect(new InetSocketAddress(LOCALHOST_IP, getLmtpPort(lmtpServerFactory)));
            readBytes(server);

            server.write(ByteBuffer.wrap(("LHLO <" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("MAIL FROM: <bob@" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("RCPT TO: <bob@examplebis.local>\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("DATA\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("header:value\r\n\r\nbody").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap((".").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
            byte[] dataResponse = readBytes(server);
            server.write(ByteBuffer.wrap(("QUIT\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);

            assertThat(new String(dataResponse, StandardCharsets.UTF_8))
                .startsWith("451 4.0.0 Temporary error deliver message");
        }

        @Test
        void dataShouldHaveAReturnCodePerRecipient() throws Exception {
            SocketChannel server = SocketChannel.open();
            server.connect(new InetSocketAddress(LOCALHOST_IP, getLmtpPort(lmtpServerFactory)));
            readBytes(server);

            server.write(ByteBuffer.wrap(("LHLO <" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("MAIL FROM: <bob@" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("RCPT TO: <bob@examplebis.local>\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("RCPT TO: <cedric@examplebis.local>\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("DATA\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server); // needed to synchronize
            server.write(ByteBuffer.wrap(("header:value\r\n\r\nbody").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap((".").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
            byte[] dataResponse = readBytes(server);
            server.write(ByteBuffer.wrap(("QUIT\r\n").getBytes(StandardCharsets.UTF_8)));

            assertThat(new String(dataResponse, StandardCharsets.UTF_8))
                .contains("451 4.0.0 Temporary error deliver message <bob@examplebis.local>\r\n" +
                    "451 4.0.0 Temporary error deliver message <cedric@examplebis.local>");
        }
    }

    @Nested
    class ThrowingOverQuotaExceptionTest {
        private InMemoryMailboxManager mailboxManager;

        @BeforeEach
        void setUp()  throws Exception {
            mailboxManager = mock(InMemoryMailboxManager.class);

            lmtpServerFactory = createLMTPServer(createMockProtocolHandlerLoaderBase()
                .put(binder -> binder.bind(MailboxManager.class).annotatedWith(Names.named("mailboxmanager")).toInstance(mailboxManager))
                .build(), "lmtpnormaldsn.xml");
        }

        @Test
        void shouldHandleOverQuotaException() throws Exception {
            MessageManager messageManager = mock(MessageManager.class);
            MailboxSession mailboxSession = mock(MailboxSession.class);

            when(mailboxManager.createSystemSession(any(Username.class))).thenReturn(mailboxSession);
            when(mailboxManager.mailboxExists(any(), any())).thenReturn(Mono.just(true));
            when(mailboxManager.getMailbox(any(MailboxPath.class), any())).thenReturn(messageManager);
            when(messageManager.appendMessage(any(), any(MailboxSession.class)))
                .thenThrow(new OverQuotaException("You have exceeded your quota", QuotaCountLimit.count(0),
                    QuotaCountUsage.count(0)));

            SocketChannel server = SocketChannel.open();
            server.connect(new InetSocketAddress(LOCALHOST_IP, getLmtpPort(lmtpServerFactory)));
            readBytes(server);

            server.write(ByteBuffer.wrap(("LHLO <" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("MAIL FROM: <bob@" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("RCPT TO: <bob@examplebis.local>\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("RCPT TO: <cedric@examplebis.local>\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("DATA\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server); // needed to synchronize
            server.write(ByteBuffer.wrap(("header:value\r\n\r\nbody").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap((".").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
            byte[] dataResponse = readBytes(server);
            server.write(ByteBuffer.wrap(("QUIT\r\n").getBytes(StandardCharsets.UTF_8)));

            assertThat(new String(dataResponse, StandardCharsets.UTF_8))
                .contains("552 5.2.2 Over Quota error when delivering message to <bob@examplebis.local>\r\n" +
                    "552 5.2.2 Over Quota error when delivering message to <cedric@examplebis.local>");
        }
    }

    @Nested
    class NormalTest {
        private InMemoryMailboxManager mailboxManager;

        @BeforeEach
        void setUp()  throws Exception {
            mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();

            lmtpServerFactory = createLMTPServer(createMockProtocolHandlerLoaderBase()
                .put(binder -> binder.bind(MailboxManager.class).annotatedWith(Names.named("mailboxmanager")).toInstance(mailboxManager))
                .build(), "lmtp.xml");
        }

        @Test
        void emailsShouldWellBeReceived() throws Exception {
            SocketChannel server = SocketChannel.open();
            server.connect(new InetSocketAddress(LOCALHOST_IP, getLmtpPort(lmtpServerFactory)));
            readBytes(server);

            server.write(ByteBuffer.wrap(("LHLO <" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("MAIL FROM: <bob@" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("RCPT TO: <bob@examplebis.local>\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("DATA\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server); // needed to synchronize
            server.write(ByteBuffer.wrap(("header:value\r\n\r\nbody").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap((".").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("QUIT\r\n").getBytes(StandardCharsets.UTF_8)));

            Username username = Username.of("bob@examplebis.local");
            MailboxSession systemSession = mailboxManager.createSystemSession(username);
            assertThatCode(() ->
                assertThat(Flux.from(mailboxManager.getMailbox(MailboxPath.inbox(username), systemSession)
                    .listMessagesMetadata(MessageRange.all(), systemSession))
                    .count()
                    .block())
                    .isEqualTo(1))
                .doesNotThrowAnyException();
            assertThat(
                IOUtils.toString(mailboxManager.getMailbox(MailboxPath.inbox(username), systemSession)
                    .getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, systemSession)
                    .next()
                    .getFullContent()
                    .getInputStream(), StandardCharsets.UTF_8))
                .endsWith("header:value\r\n\r\nbody\r\n");
        }

        @Test
        void dataShouldHaveAReturnCodePerRecipient() throws Exception {
            SocketChannel server = SocketChannel.open();
            server.connect(new InetSocketAddress(LOCALHOST_IP, getLmtpPort(lmtpServerFactory)));
            readBytes(server);

            server.write(ByteBuffer.wrap(("LHLO <" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("MAIL FROM: <bob@" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("RCPT TO: <bob@examplebis.local>\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("RCPT TO: <cedric@examplebis.local>\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server);
            server.write(ByteBuffer.wrap(("DATA\r\n").getBytes(StandardCharsets.UTF_8)));
            readBytes(server); // needed to synchronize
            server.write(ByteBuffer.wrap(("header:value\r\n\r\nbody").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap((".").getBytes(StandardCharsets.UTF_8)));
            server.write(ByteBuffer.wrap(("\r\n").getBytes(StandardCharsets.UTF_8)));
             byte[] dataResponse = readBytes(server);
            server.write(ByteBuffer.wrap(("QUIT\r\n").getBytes(StandardCharsets.UTF_8)));

            assertThat(new String(dataResponse, StandardCharsets.UTF_8))
                .contains("250 2.6.0 Message received <bob@examplebis.local>\r\n" +
                    "250 2.6.0 Message received <cedric@examplebis.local>");
        }

        @Test
        void ehloShouldBeRejected() throws Exception {
            SocketChannel server = SocketChannel.open();
            server.connect(new InetSocketAddress(LOCALHOST_IP, getLmtpPort(lmtpServerFactory)));
            readBytes(server);

            server.write(ByteBuffer.wrap(("EHLO <" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
            assertThat(new String(readBytes(server), StandardCharsets.UTF_8))
                .contains("500 Unable to process request: the command is unknown");
        }

        @Test
        void heloShouldBeRejected() throws Exception {
            SocketChannel server = SocketChannel.open();
            server.connect(new InetSocketAddress(LOCALHOST_IP, getLmtpPort(lmtpServerFactory)));
            readBytes(server);

            server.write(ByteBuffer.wrap(("HELO <" + DOMAIN + ">\r\n").getBytes(StandardCharsets.UTF_8)));
            assertThat(new String(readBytes(server), StandardCharsets.UTF_8))
                .contains("500 Unable to process request: the command is unknown");
        }
    }

    private byte[] readBytes(SocketChannel channel) throws IOException {
        ByteBuffer line = ByteBuffer.allocate(1024);
        channel.read(line);
        line.rewind();
        byte[] bline = new byte[line.remaining()];
        line.get(bline);
        return bline;
    }
}