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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import javax.net.ssl.X509TrustManager;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.net.imap.AuthenticatingIMAPClient;
import org.apache.james.core.Username;
import org.apache.james.imap.api.ConnectionCheck;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.fetch.FetchProcessor;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.apache.james.protocols.lib.LegacyJavaEncryptionFactory;
import org.apache.james.protocols.lib.mock.ConfigLoader;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@SuppressWarnings("checkstyle:membername")
class IMAPServerTest {
    private static final String _129K_MESSAGE = "header: value\r\n" + "012345678\r\n".repeat(13107);
    private static final String _65K_MESSAGE = "header: value\r\n" + "012345678\r\n".repeat(6553);
    private static final Username USER = Username.of("user@domain.org");
    private static final Username USER2 = Username.of("bobo@domain.org");
    private static final Username USER3 = Username.of("user3@domain.org");
    private static final String USER_PASS = "pass";
    public static final String SMALL_MESSAGE = "header: value\r\n\r\nBODY";
    private InMemoryIntegrationResources memoryIntegrationResources;
    private FakeAuthenticator authenticator;

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    private InMemoryMailboxManager mailboxManager;

    private IMAPServer createImapServer(HierarchicalConfiguration<ImmutableNode> config,
                                        InMemoryIntegrationResources inMemoryIntegrationResources,
                                        FetchProcessor.LocalCacheConfiguration localCacheConfiguration) throws Exception {
        memoryIntegrationResources = inMemoryIntegrationResources;

        RecordingMetricFactory metricFactory = new RecordingMetricFactory();
        Set<ConnectionCheck> connectionChecks = defaultConnectionChecks();
        mailboxManager = spy(memoryIntegrationResources.getMailboxManager());
        IMAPServer imapServer = new IMAPServer(
            new DefaultImapDecoderFactory().buildImapDecoder(),
            new DefaultImapEncoderFactory().buildImapEncoder(),
            DefaultImapProcessorFactory.createXListSupportingProcessor(
                mailboxManager,
                memoryIntegrationResources.getEventBus(),
                new StoreSubscriptionManager(mailboxManager.getMapperFactory(),
                    mailboxManager.getMapperFactory(),
                    mailboxManager.getEventBus()),
                null,
                memoryIntegrationResources.getQuotaManager(),
                memoryIntegrationResources.getQuotaRootResolver(),
                metricFactory,
                localCacheConfiguration),
            new ImapMetrics(metricFactory),
            new NoopGaugeRegistry(), connectionChecks);

        FileSystemImpl fileSystem = FileSystemImpl.forTestingWithConfigurationFromClasspath();
        imapServer.setFileSystem(fileSystem);
        imapServer.setEncryptionFactory(new LegacyJavaEncryptionFactory(fileSystem));

        imapServer.configure(config);
        imapServer.init();

        return imapServer;
    }

    private IMAPServer createImapServer(HierarchicalConfiguration<ImmutableNode> config, FetchProcessor.LocalCacheConfiguration localCacheConfiguration) throws Exception {
        authenticator = new FakeAuthenticator();
        authenticator.addUser(USER, USER_PASS);
        authenticator.addUser(USER2, USER_PASS);
        authenticator.addUser(USER3, USER_PASS);

        memoryIntegrationResources = InMemoryIntegrationResources.builder()
            .authenticator(authenticator)
            .authorizator(FakeAuthorizator.defaultReject())
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        return createImapServer(config, memoryIntegrationResources, localCacheConfiguration);
    }

    private IMAPServer createImapServer(HierarchicalConfiguration<ImmutableNode> config) throws Exception {
        return createImapServer(config, FetchProcessor.LocalCacheConfiguration.DEFAULT);
    }

    private IMAPServer createImapServer(String configurationFile, FetchProcessor.LocalCacheConfiguration localCacheConfiguration) throws Exception {
        return createImapServer(ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream(configurationFile)), localCacheConfiguration);
    }

    private IMAPServer createImapServer(String configurationFile) throws Exception {
        return createImapServer(configurationFile, FetchProcessor.LocalCacheConfiguration.DEFAULT);
    }

    private Set<ConnectionCheck> defaultConnectionChecks() {
        return ImmutableSet.of(new IpConnectionCheck());
    }







    public static class BlindTrustManager implements X509TrustManager {
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType) {

        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {


        }
    }

    public static ListAppender<ILoggingEvent> getListAppenderForClass(Class clazz) {
        Logger logger = (Logger) LoggerFactory.getLogger(clazz);

        ListAppender<ILoggingEvent> loggingEventListAppender = new ListAppender<>();
        loggingEventListAppender.start();

        logger.addAppender(loggingEventListAppender);
        return loggingEventListAppender;
    }












    private AuthenticatingIMAPClient imapsClient(int port) throws Exception {
        AuthenticatingIMAPClient client = new AuthenticatingIMAPClient(false, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        client.connect("127.0.0.1", port);
        client.execTLS();
        return client;
    }








    private byte[] readBytes(SocketChannel channel) throws IOException {
        ByteBuffer line = ByteBuffer.allocate(1024);
        channel.read(line);
        line.rewind();
        byte[] bline = new byte[line.remaining()];
        line.get(bline);
        return bline;
    }

    private List<String> readStringUntil(SocketChannel channel, Predicate<String> condition) throws IOException {
        ImmutableList.Builder<String> result = ImmutableList.builder();
        while (true) {
            String line = new String(readBytes(channel), StandardCharsets.US_ASCII);
            result.add(line);
            if (condition.test(line)) {
                return result.build();
            }
        }
    }




    
    @Nested
    class IDCommandTest {
        IMAPServer imapServer;

        @AfterEach
        void tearDown() {
            if (imapServer != null) {   
                imapServer.destroy();
            }
        }

        @Test
        void idCommandShouldReturnNILWhenNoConfigured() throws Exception {
            imapServer = createImapServer("imapServer.xml");

            assertThat(
                testIMAPClient.connect("127.0.0.1", imapServer.getListenAddresses().getFirst().getPort())
                    .sendCommand("ID (\"name\" \"Apache James\")"))
                .contains("* ID NIL")
                .contains("OK ID completed.");
        }

        @Test
        void idCommandShouldReturnConfiguredResponse() throws Exception {
            imapServer = createImapServer("imapServerIdCommandResponseFields.xml");
            assertThat(
                testIMAPClient.connect("127.0.0.1", imapServer.getListenAddresses().getFirst().getPort())
                    .sendCommand("ID (\"name\" \"Apache James\")"))
                .contains("* ID (\"name\" \"Apache James\" \"version\" \"3.9.0\")")
                .contains("OK ID completed.");
        }

        @Test
        void concurrentIdCommandsInTheSameSessionShouldSucceed() throws Exception {
            imapServer = createImapServer("imapServer.xml");

            testIMAPClient.connect("127.0.0.1", imapServer.getListenAddresses().getFirst().getPort());
            ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> {
                    assertThat(testIMAPClient.sendCommand("ID (\"name\" \"Apache James\")"))
                        .contains("* ID NIL")
                        .contains("OK ID completed.");
                })
                .threadCount(20)
                .operationCount(1)
                .runSuccessfullyWithin(Duration.ofMinutes(5));
        }
    }

    @Nested
    class RenameMailboxTest {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServer.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
            MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
            memoryIntegrationResources.getMailboxManager()
                .createMailbox(MailboxPath.forUser(USER, "mailbox1"), mailboxSession);
            memoryIntegrationResources.getMailboxManager()
                .createMailbox(MailboxPath.forUser(USER, "mailbox2"), mailboxSession);
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void renameShouldFailWhenTargetMailboxAlreadyExists() throws Exception {
            testIMAPClient.connect("127.0.0.1", port)
                .login(USER.asString(), USER_PASS);

            String response = testIMAPClient.sendCommand("RENAME mailbox1 mailbox2");

            assertThat(response).contains("NO RENAME failed. Mailbox already exists.");
        }

        @Test
        void renameShouldFailWhenRequestedMailboxDoesNotExist() throws Exception {
            testIMAPClient.connect("127.0.0.1", port)
                .login(USER.asString(), USER_PASS);

            String response = testIMAPClient.sendCommand("RENAME nonExistingMailbox newMailboxName");

            assertThat(response).contains("NO RENAME failed. Mailbox not found.");
        }

        @Test
        void renameShouldFailWhenInsufficientRightsOnSharedMailbox() throws Exception {
            // Create a mailbox for another user
            memoryIntegrationResources.getMailboxManager()
                .createMailbox(MailboxPath.forUser(USER2, "sharedMailbox.child1"),
                    memoryIntegrationResources.getMailboxManager().createSystemSession(USER2));

            // Ensure the current user does not have the "delete mailbox" right on the shared mailbox
            memoryIntegrationResources.getMailboxManager()
                .applyRightsCommand(MailboxPath.forUser(USER2, "sharedMailbox"),
                    MailboxACL.command()
                        .forUser(USER)
                        .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.Insert,
                            MailboxACL.Right.CreateMailbox,
                            MailboxACL.Right.Administer, MailboxACL.Right.Write)
                        .asAddition(),
                    memoryIntegrationResources.getMailboxManager().createSystemSession(USER2));
            memoryIntegrationResources.getMailboxManager()
                .applyRightsCommand(MailboxPath.forUser(USER2, "sharedMailbox.child1"),
                    MailboxACL.command()
                        .forUser(USER)
                        .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read, MailboxACL.Right.Insert,
                            MailboxACL.Right.CreateMailbox,
                            MailboxACL.Right.Administer, MailboxACL.Right.Write)
                        .asAddition(),
                    memoryIntegrationResources.getMailboxManager().createSystemSession(USER2));

            // Connect and attempt to rename the shared mailbox
            testIMAPClient.connect("127.0.0.1", port)
                .login(USER.asString(), USER_PASS);
            String response = testIMAPClient.sendCommand("RENAME #user.bobo.sharedMailbox.child1 #user.bobo.sharedMailbox.newChild");

            // Assert that the operation fails due to insufficient rights
            assertThat(response).contains("NO RENAME failed. Insufficient rights.");
        }
    }

}
