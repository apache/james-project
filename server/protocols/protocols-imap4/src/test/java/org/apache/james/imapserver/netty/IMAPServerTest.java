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

import static jakarta.mail.Flags.Flag.ANSWERED;
import static jakarta.mail.Folder.READ_WRITE;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.apache.james.jwt.OidcTokenFixture.INTROSPECTION_RESPONSE;
import static org.apache.james.mailbox.MessageManager.FlagsUpdateMode.REPLACE;
import static org.apache.james.mailbox.MessageManager.MailboxMetaData.FetchGroup.NO_COUNT;
import static org.apache.james.mailbox.MessageManager.MailboxMetaData.RecentMode.IGNORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.net.imap.AuthenticatingIMAPClient;
import org.apache.commons.net.imap.IMAPReply;
import org.apache.commons.net.imap.IMAPSClient;
import org.apache.james.core.Username;
import org.apache.james.imap.api.ConnectionCheck;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.base.AbstractProcessor;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.jwt.OidcTokenFixture;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.api.OIDCSASLHelper;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.apache.james.protocols.lib.mock.ConfigLoader;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.james.utils.TestIMAPClient;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.stubbing.Answer;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.compression.JdkZlibDecoder;
import io.netty.handler.codec.compression.JdkZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.ssl.SslContextBuilder;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.BodyTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.RecipientStringTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SubjectTerm;
import nl.altindag.ssl.exception.GenericKeyStoreException;
import nl.altindag.ssl.pem.exception.PrivateKeyParseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;

class IMAPServerTest {
    private static final String _129K_MESSAGE = "header: value\r\n" + "012345678\r\n".repeat(13107);
    private static final String _65K_MESSAGE = "header: value\r\n" + "012345678\r\n".repeat(6553);
    private static final Username USER = Username.of("user@domain.org");
    private static final Username USER2 = Username.of("bobo@domain.org");
    private static final Username USER3= Username.of("user3@domain.org");
    private static final String USER_PASS = "pass";
    public static final String SMALL_MESSAGE = "header: value\r\n\r\nBODY";
    private InMemoryIntegrationResources memoryIntegrationResources;
    private FakeAuthenticator authenticator;

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    private InMemoryMailboxManager mailboxManager;

    private IMAPServer createImapServer(HierarchicalConfiguration<ImmutableNode> config,
                                        InMemoryIntegrationResources inMemoryIntegrationResources) throws Exception {
        memoryIntegrationResources = inMemoryIntegrationResources;

        RecordingMetricFactory metricFactory = new RecordingMetricFactory();
        Set<ConnectionCheck> connectionChecks = defaultConnectionChecks();
        mailboxManager = spy(memoryIntegrationResources.getMailboxManager());
        IMAPServer imapServer = new IMAPServer(
            DefaultImapDecoderFactory.createDecoder(),
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
                metricFactory),
            new ImapMetrics(metricFactory),
            new NoopGaugeRegistry(), connectionChecks);

        FileSystemImpl fileSystem = FileSystemImpl.forTestingWithConfigurationFromClasspath();
        imapServer.setFileSystem(fileSystem);

        imapServer.configure(config);
        imapServer.init();

        return imapServer;
    }
    private IMAPServer createImapServer(HierarchicalConfiguration<ImmutableNode> config) throws Exception {
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

        return createImapServer(config, memoryIntegrationResources);
    }

    private IMAPServer createImapServer(String configurationFile) throws Exception {
        return createImapServer(ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream(configurationFile)));
    }

    private Set<ConnectionCheck> defaultConnectionChecks() {
        return ImmutableSet.of(new IpConnectionCheck());
    }

    @Nested
    class ConnectionCheckTest {

        IMAPServer imapServer;
        private final IpConnectionCheck ipConnectionCheck = new IpConnectionCheck();
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("imapServerImapConnectCheck.xml"));
            imapServer = createImapServer(config);
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void banIpWhenBannedIpConnect() {
            imapServer.getConnectionChecks().stream()
                .filter(check -> check instanceof IpConnectionCheck)
                .map(check -> (IpConnectionCheck) check)
                .forEach(ipCheck -> ipCheck.setBannedIps(Set.of("127.0.0.1")));

            assertThatThrownBy(() -> testIMAPClient.connect("127.0.0.1", port)
                .login(USER.asString(), USER_PASS)
                .append("INBOX", SMALL_MESSAGE));
        }

        @Test
        void allowConnectWithUnbannedIp() throws IOException {
            imapServer.getConnectionChecks().stream()
                .filter(check -> check instanceof IpConnectionCheck)
                .map(check -> (IpConnectionCheck) check)
                .forEach(ipCheck -> ipCheck.setBannedIps(Set.of("127.0.0.2")));

            testIMAPClient.connect("127.0.0.1", port)
                .login(USER.asString(), USER_PASS)
                .append("INBOX", SMALL_MESSAGE);

            assertThat(testIMAPClient
                .select("INBOX")
                .readFirstMessage())
                .contains("* 1 FETCH (FLAGS (\\Recent \\Seen) BODY[] {21}\r\nheader: value\r\n\r\nBODY)\r\n");
        }
    }

    @Nested
    class PartialFetch {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServer.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void fetchShouldRetrieveMessage() throws Exception {
            testIMAPClient.connect("127.0.0.1", port)
                .login(USER.asString(), USER_PASS)
                .append("INBOX", SMALL_MESSAGE);

            assertThat(testIMAPClient
                    .select("INBOX")
                    .readFirstMessage())
                .contains("* 1 FETCH (FLAGS (\\Recent \\Seen) BODY[] {21}\r\nheader: value\r\n\r\nBODY)\r\n");
        }

        @Test
        void fetchShouldRetrieveMessageWhenOffsetAndLimitExceedingMessageSize() throws Exception {
            testIMAPClient.connect("127.0.0.1", port)
                .login(USER.asString(), USER_PASS)
                .append("INBOX", SMALL_MESSAGE);

            assertThat(testIMAPClient
                    .select("INBOX")
                    .readFirstMessageInMailbox("BODY[]<8.20>"))
                .contains("* 1 FETCH (FLAGS (\\Recent \\Seen) BODY[]<8> {13}\r\nvalue\r\n\r\nBODY)\r\n");
        }

        @Test
        void fetchShouldRetrieveMessageWhenOffsetAndLimitEqualMessageSize() throws Exception {
            testIMAPClient.connect("127.0.0.1", port)
                .login(USER.asString(), USER_PASS)
                .append("INBOX", SMALL_MESSAGE);

            assertThat(testIMAPClient
                    .select("INBOX")
                    .readFirstMessageInMailbox("BODY[]<8.13>"))
                .contains("* 1 FETCH (FLAGS (\\Recent \\Seen) BODY[]<8> {13}\r\nvalue\r\n\r\nBODY)\r\n");
        }

        @Test
        void fetchShouldRetrieveMessageWhenOffsetAndLimitBelowMessageSize() throws Exception {
            testIMAPClient.connect("127.0.0.1", port)
                .login(USER.asString(), USER_PASS)
                .append("INBOX", SMALL_MESSAGE);

            assertThat(testIMAPClient
                    .select("INBOX")
                    .readFirstMessageInMailbox("BODY[]<8.12>"))
                .contains("* 1 FETCH (FLAGS (\\Recent \\Seen) BODY[]<8> {12}\r\nvalue\r\n\r\nBOD)\r\n");
        }

        @Test
        void fetchShouldRetrieveMessageWhenOffsetAndNoLimitSpecified() throws Exception {
            testIMAPClient.connect("127.0.0.1", port)
                .login(USER.asString(), USER_PASS)
                .append("INBOX", SMALL_MESSAGE);

            assertThat(testIMAPClient
                    .select("INBOX")
                    .readFirstMessageInMailbox("BODY[]<8>"))
                .contains("* 1 FETCH (FLAGS (\\Recent \\Seen) BODY[]<8> {13}\r\nvalue\r\n\r\nBODY)\r\n");
        }
    }

    @Nested
    class NoLimit {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServerNoLimits.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void smallAppendsShouldWork() throws Exception {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", SMALL_MESSAGE))
                .doesNotThrowAnyException();

            assertThat(testIMAPClient.select("INBOX")
                    .readFirstMessage())
                .contains("\r\n" + SMALL_MESSAGE + ")\r\n");
        }

        @Test
        void capabilityAdvertizeAppendLimit() throws Exception {
            assertThat(
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .capability())
                .contains("APPENDLIMIT")
                .doesNotContain("APPENDLIMIT=");
        }

        @Test
        void statusAdvertizeAppendLimit() throws Exception {
            assertThat(
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .sendCommand("STATUS \"INBOX\" (APPENDLIMIT)"))
                .contains("* STATUS \"INBOX\" (APPENDLIMIT NIL)");
        }

        @Test
        void mediumAppendsShouldWork() throws Exception {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", _65K_MESSAGE))
                .doesNotThrowAnyException();

            assertThat(testIMAPClient.select("INBOX")
                    .readFirstMessage())
                .contains("\r\n" + _65K_MESSAGE + ")\r\n");
        }

        @Test
        void loginFixationShouldBeRejected() throws Exception {
            InMemoryMailboxManager mailboxManager = memoryIntegrationResources.getMailboxManager();
            mailboxManager.createMailbox(
                MailboxPath.forUser(USER, "pwnd"),
                mailboxManager.createSystemSession(USER));
            mailboxManager.createMailbox(
                MailboxPath.forUser(USER2, "notvuln"),
                mailboxManager.createSystemSession(USER2));

            testIMAPClient.connect("127.0.0.1", port)
                // Injected by a man in the middle attacker
                .rawLogin(USER.asString(), USER_PASS);

            assertThatThrownBy(() -> testIMAPClient.rawLogin(USER2.asString(), USER_PASS))
                .isInstanceOf(IOException.class)
                .hasMessage("Login failed");
        }

        @RepeatedTest(200)
        void largeAppendsShouldWork() throws Exception {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", _129K_MESSAGE))
                .doesNotThrowAnyException();

            assertThat(testIMAPClient.select("INBOX")
                    .readFirstMessage())
                .contains("\r\n" + _129K_MESSAGE + ")\r\n");
        }
    }

    @Nested
    class AppendNonSynchronizedLitterals {
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

    @Nested
    class Proxy {
        private static final String CLIENT_IP = "255.255.255.254";
        private static final String PROXY_IP = "255.255.255.255";
        private static final String RANDOM_IP = "127.0.0.2";

        IMAPServer imapServer;
        private SocketChannel clientConnection;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServerProxy.xml");
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

        private void addBannedIps(String clientIp) {
            imapServer.getConnectionChecks().stream()
                .filter(check -> check instanceof IpConnectionCheck)
                .map(check -> (IpConnectionCheck) check)
                .forEach(ipCheck -> ipCheck.setBannedIps(Set.of(clientIp)));
        }

        @Test
        void shouldNotFailOnProxyInformation() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("PROXY %s %s %s %d %d\r\na0 LOGIN %s %s\r\n",
                "TCP4", CLIENT_IP, PROXY_IP, 65535, 65535,
                USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));

            assertThat(new String(readBytes(clientConnection), StandardCharsets.US_ASCII))
                .startsWith("a0 OK");
        }

        @Test
        void shouldDetectAndBanByClientIP() throws IOException {
            addBannedIps(CLIENT_IP);

            // WHEN connect as CLIENT_IP to PROXY_DESTINATION via PROXY_IP
            clientConnection.write(ByteBuffer.wrap(String.format("PROXY %s %s %s %d %d\r\na0 LOGIN %s %s\r\n",
                "TCP4", CLIENT_IP, PROXY_IP, 65535, 65535,
                USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));

            // THEN LOGIN should be rejected
            assertThat(new String(readBytes(clientConnection), StandardCharsets.US_ASCII))
                .doesNotStartWith("a0 OK");
        }

        @Test
        void shouldNotBanByProxyIP() throws IOException {
            // GIVEN somehow PROXY_IP has been banned by mistake
            addBannedIps(PROXY_IP);

            clientConnection.write(ByteBuffer.wrap(String.format("PROXY %s %s %s %d %d\r\na0 LOGIN %s %s\r\n",
                "TCP4", CLIENT_IP, PROXY_IP, 65535, 65535,
                USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));

            // THEN CLIENT_IP still can connect
            assertThat(new String(readBytes(clientConnection), StandardCharsets.US_ASCII))
                .startsWith("a0 OK");
        }

        @Test
        void clientUsageShouldBeNormalWhenClientIPIsNotBanned() throws IOException {
            addBannedIps(RANDOM_IP);

            clientConnection.write(ByteBuffer.wrap(String.format("PROXY %s %s %s %d %d\r\na0 LOGIN %s %s\r\n",
                "TCP4", CLIENT_IP, PROXY_IP, 65535, 65535,
                USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));

            assertThat(new String(readBytes(clientConnection), StandardCharsets.US_ASCII))
                .startsWith("a0 OK");
        }
    }

    @Nested
    class Compress {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServerCompress.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void shouldNotThrowWhenCompressionEnabled() throws Exception {
            InMemoryMailboxManager mailboxManager = memoryIntegrationResources.getMailboxManager();
            MailboxSession mailboxSession = mailboxManager.createSystemSession(USER);
            mailboxManager.createMailbox(
                MailboxPath.inbox(USER),
                mailboxSession);
            mailboxManager.getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .appendMessage(MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession);

            Properties props = new Properties();
            props.put("mail.imap.user", USER.asString());
            props.put("mail.imap.host", "127.0.0.1");
            props.put("mail.imap.auth.mechanisms", "LOGIN");
            props.put("mail.imap.compress.enable", true);
            final Session session = Session.getInstance(props);
            final Store store = session.getStore("imap");
            store.connect("127.0.0.1", port, USER.asString(), USER_PASS);
            final FetchProfile fetchProfile = new FetchProfile();
            fetchProfile.add(FetchProfile.Item.ENVELOPE);
            final IMAPFolder inbox = (IMAPFolder) store.getFolder("INBOX");
            inbox.open(READ_WRITE);

            inbox.getMessageByUID(1);
        }

        @Test
        void compressShouldFailWhenUnknownCompressionAlgorithm() throws Exception {
            String reply = testIMAPClient.connect("127.0.0.1", port)
                .login(USER.asString(), USER_PASS)
                .sendCommand("COMPRESS BAD");

            assertThat(reply).contains("AAAB BAD COMPRESS failed. Illegal arguments.");
        }
    }

    @Nested
    class CompressWithSSL {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServerSSLCompress.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void shouldNotThrowWhenCompressionEnabled() throws Exception {
            InMemoryMailboxManager mailboxManager = memoryIntegrationResources.getMailboxManager();
            MailboxSession mailboxSession = mailboxManager.createSystemSession(USER);
            mailboxManager.createMailbox(
                MailboxPath.inbox(USER),
                mailboxSession);
            mailboxManager.getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .appendMessage(MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"), mailboxSession);

            Properties props = new Properties();
            props.put("mail.imaps.user", USER.asString());
            props.put("mail.imaps.host", "127.0.0.1");
            props.put("mail.imaps.auth.mechanisms", "LOGIN");
            props.put("mail.imaps.compress.enable", true);
            props.put("mail.imaps.ssl.enable", "true");
            props.put("mail.imaps.ssl.trust", "*");
            props.put("mail.imaps.ssl.checkserveridentity", "false");
            final Session session = Session.getInstance(props);
            final Store store = session.getStore("imaps");
            store.connect("127.0.0.1", port, USER.asString(), USER_PASS);
            final FetchProfile fetchProfile = new FetchProfile();
            fetchProfile.add(FetchProfile.Item.ENVELOPE);
            final IMAPFolder inbox = (IMAPFolder) store.getFolder("INBOX");
            inbox.open(READ_WRITE);

            inbox.getMessageByUID(1);
        }

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


    @Nested
    class StartTLS {
        IMAPServer imapServer;
        private int port;
        private Connection connection;
        private ConcurrentLinkedDeque<String> responses;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServerStartTLS.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
            connection = TcpClient.create()
                .noSSL()
                .remoteAddress(() -> new InetSocketAddress(LOCALHOST_IP, port))
                .option(ChannelOption.TCP_NODELAY, true)
                .connectNow();
            responses = new ConcurrentLinkedDeque<>();
            connection.inbound().receive().asString()
                .doOnNext(responses::addLast)
                .subscribeOn(Schedulers.newSingle("imap-test"))
                .subscribe();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void extraLinesBatchedWithStartTLSShouldBeSanitized() throws Exception {
            IMAPSClient imapClient = new IMAPSClient();
            imapClient.connect("127.0.0.1", port);
            assertThatThrownBy(() -> imapClient.sendCommand("STARTTLS\r\nA1 NOOP\r\n"))
                .isInstanceOf(EOFException.class)
                .hasMessage("Connection closed without indication.");
        }

        @Test
        void extraLFLinesBatchedWithStartTLSShouldBeSanitized() throws Exception {
            IMAPSClient imapClient = new IMAPSClient();
            imapClient.connect("127.0.0.1", port);
            assertThatThrownBy(() -> imapClient.sendCommand("STARTTLS\nA1 NOOP\r\n"))
                .isInstanceOf(EOFException.class)
                .hasMessage("Connection closed without indication.");
        }

        @Test
        void tagsShouldBeWellSanitized() throws Exception {
            IMAPSClient imapClient = new IMAPSClient();
            imapClient.connect("127.0.0.1", port);
            assertThatThrownBy(() -> imapClient.sendCommand("NOOP\r\n A1 STARTTLS\r\nA2 NOOP"))
                .isInstanceOf(EOFException.class)
                .hasMessage("Connection closed without indication.");
        }

        @Test
        void lineFollowingStartTLSShouldBeSanitized() throws Exception {
            IMAPSClient imapClient = new IMAPSClient();
            imapClient.connect("127.0.0.1", port);
            assertThatThrownBy(() -> imapClient.sendCommand("STARTTLS A1 NOOP\r\n"))
                .isInstanceOf(EOFException.class)
                .hasMessage("Connection closed without indication.");
        }

        @Test
        void startTLSShouldFailWhenAuthenticated() throws Exception {
            // Avoids session fixation attacks as described in https://www.usenix.org/system/files/sec21-poddebniak.pdf
            // section 6.2

            IMAPSClient imapClient = new IMAPSClient();
            imapClient.connect("127.0.0.1", port);
            imapClient.login(USER.asString(), USER_PASS);
            int imapCode = imapClient.sendCommand("STARTTLS\r\n");

            assertThat(imapCode).isEqualTo(IMAPReply.NO);
        }

        private void send(String format) {
            connection.outbound()
                .send(Mono.just(Unpooled.wrappedBuffer(format
                    .getBytes(StandardCharsets.UTF_8))))
                .then()
                .subscribe();
        }

        @RepeatedTest(10)
        void concurrencyShouldNotLeadToCommandInjection() throws Exception {
            ListAppender<ILoggingEvent> listAppender = getListAppenderForClass(AbstractProcessor.class);

            send("a0 STARTTLS\r\n");
            send("a1 NOOP\r\n");

            Thread.sleep(50);

            assertThat(listAppender.list)
                .filteredOn(event -> event.getFormattedMessage().contains("Processing org.apache.james.imap.message.request.NoopRequest"))
                .isEmpty();
        }
    }

    @Nested
    class Ssl {
        IMAPServer imapServer;

        @AfterEach
        void tearDown() {
            if (imapServer != null) {
                imapServer.destroy();
            }
        }

        @Test
        void initShouldAcceptJKSFormat() {
            assertThatCode(() -> imapServer = createImapServer("imapServerSslJKS.xml"))
                .doesNotThrowAnyException();
        }

        @Test
        void initShouldAcceptPKCS12Format() {
            assertThatCode(() -> imapServer = createImapServer("imapServerSslPKCS12.xml"))
                .doesNotThrowAnyException();
        }

        @Test
        void initShouldAcceptPEMKeysWithPassword() {
            assertThatCode(() -> imapServer = createImapServer("imapServerSslPEM.xml"))
                .doesNotThrowAnyException();
        }

        @Test
        void initShouldAcceptPEMKeysWithoutPassword() {
            assertThatCode(() -> imapServer = createImapServer("imapServerSslPEMNoPass.xml"))
                .doesNotThrowAnyException();
        }

        @Test
        void initShouldAcceptJKSByDefault() {
            assertThatCode(() -> imapServer = createImapServer("imapServerSslDefaultJKS.xml"))
                .doesNotThrowAnyException();
        }

        @Test
        void initShouldThrowWhenSslEnabledWithoutKeys() {
            assertThatThrownBy(() -> createImapServer("imapServerSslNoKeys.xml"))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("keystore or (privateKey and certificates) needs to get configured");
        }

        @Test
        void initShouldThrowWhenJKSWithBadPassword() {
            assertThatThrownBy(() -> createImapServer("imapServerSslJKSBadPassword.xml"))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessageContaining("keystore password was incorrect");
        }

        @Test
        void initShouldThrowWhenPEMWithBadPassword() {
            assertThatThrownBy(() -> createImapServer("imapServerSslPEMBadPass.xml"))
                .isInstanceOf(PrivateKeyParseException.class);
        }

        @Test
        void initShouldThrowWhenPEMWithMissingPassword() {
            assertThatThrownBy(() -> createImapServer("imapServerSslPEMMissingPass.xml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A password is mandatory with an encrypted key");
        }

        @Test
        void initShouldNotThrowWhenPEMWithExtraPassword() {
            assertThatCode(() -> imapServer = createImapServer("imapServerSslPEMExtraPass.xml"))
                .doesNotThrowAnyException();
        }

        @Test
        void initShouldThrowWhenJKSWenNotFound() {
            assertThatThrownBy(() -> createImapServer("imapServerSslJKSNotFound.xml"))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessage("class path resource [keystore.notfound.jks] cannot be resolved to URL because it does not exist");
        }

        @Test
        void initShouldThrowWhenPKCS12WithBadPassword() {
            assertThatThrownBy(() -> createImapServer("imapServerSslPKCS12WrongPassword.xml"))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessageContaining("keystore password was incorrect");
        }

        @Test
        void initShouldThrowWhenPKCS12WithMissingPassword() {
            assertThatThrownBy(() -> createImapServer("imapServerSslPKCS12MissingPassword.xml"))
                .isInstanceOf(GenericKeyStoreException.class)
                .hasMessageContaining("keystore password was incorrect");
        }
    }

    @Nested
    class Limit {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServer.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void smallAppendsShouldWork() throws Exception {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", SMALL_MESSAGE))
                .doesNotThrowAnyException();

            assertThat(testIMAPClient.select("INBOX")
                    .readFirstMessage())
                .contains("\r\n" + SMALL_MESSAGE + ")\r\n");
        }

        @Test
        void mediumAppendsShouldWork() throws Exception {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", _65K_MESSAGE))
                .doesNotThrowAnyException();

            assertThat(testIMAPClient.select("INBOX")
                    .readFirstMessage())
                .contains("\r\n" + _65K_MESSAGE + ")\r\n");
        }

        @Test
        void largeAppendsShouldBeRejected() {
            assertThatThrownBy(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", _129K_MESSAGE))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("BAD APPEND failed.");
        }

        @Test
        void capabilityAdvertizeAppendLimit() throws Exception {
            assertThat(
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .capability())
                .contains("APPENDLIMIT=131072");
        }

        @Test
        void statusAdvertizeAppendLimit() throws Exception {
            assertThat(
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .sendCommand("STATUS \"INBOX\" (APPENDLIMIT)"))
                .contains("* STATUS \"INBOX\" (APPENDLIMIT 131072)");
        }
    }

    @Nested
    class PlainAuthDisabled {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServerPlainAuthDisabled.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void loginShouldFail() {
            assertThatThrownBy(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS))
                .hasMessage("Login failed");
        }

        @Test
        void authenticatePlainShouldFail() {
            assertThatThrownBy(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .authenticatePlain(USER.asString(), USER_PASS))
                .hasMessage("Login failed");
        }

        @Test
        void capabilityShouldNotAdvertiseLoginAndAuthenticationPlain() throws Exception {
            testIMAPClient.connect("127.0.0.1", port);

            assertThat(testIMAPClient.capability())
                .contains("LOGINDISABLED")
                .doesNotContain("AUTH=PLAIN");
        }
    }

    @Nested
    class PlainAuthEnabledWithoutRequireSSL {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServerPlainAuthEnabledWithoutRequireSSL.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void loginShouldSucceed() {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS))
                .doesNotThrowAnyException();
        }

        @RepeatedTest(100)
        void authenticatePlainShouldSucceed() {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .authenticatePlain(USER.asString(), USER_PASS))
                .doesNotThrowAnyException();
        }

        @Test
        void capabilityShouldAdvertiseLoginAndAuthenticationPlain() throws Exception {
            testIMAPClient.connect("127.0.0.1", port);

            assertThat(testIMAPClient.capability())
                .doesNotContain("LOGINDISABLED")
                .contains("AUTH=PLAIN");
        }

        @Test
        void authenticatePlainShouldSucceedWhenPasswordHasMoreThan255Characters() {
            Username user1 = Username.of("user1@domain.org");
            String user1Password = "1".repeat(300);
            authenticator.addUser(user1, user1Password);
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .authenticatePlain(user1.asString(), user1Password))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class PlainAuthDisallowed {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServerPlainAuthDisallowed.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void loginShouldFailOnUnEncryptedChannel() {
            assertThatThrownBy(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS))
                .hasMessage("Login failed");
        }

        @Test
        void capabilityShouldNotAdvertiseLoginOnUnEncryptedChannel() throws Exception {
            testIMAPClient.connect("127.0.0.1", port);

            assertThat(testIMAPClient.capability())
                .contains("LOGINDISABLED")
                .doesNotContain("AUTH=PLAIN");
        }
    }

    @Nested
    class PlainAuthDisallowedSSL {
        IMAPServer imapServer;
        private int port;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServerPlainAuthAllowed.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            imapServer.destroy();
        }

        @Test
        void loginShouldSucceedOnUnEncryptedChannel() {
            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS))
                .doesNotThrowAnyException();
        }

        @Test
        void capabilityShouldAdvertiseLoginOnUnEncryptedChannel() throws Exception {
            testIMAPClient.connect("127.0.0.1", port);

            assertThat(testIMAPClient.capability())
                .doesNotContain("LOGINDISABLED")
                .contains("AUTH=PLAIN");
        }
    }

    @Nested
    class AuthenticationRequireSSL {
        IMAPServer imapServer;

        @AfterEach
        void tearDown() {
            if (imapServer != null) {
                imapServer.destroy();
            }
        }

        @Test
        void loginShouldFailWhenRequireSSLAndUnEncryptedChannel() throws Exception {
            imapServer = createImapServer("imapServerRequireSSLIsTrueAndStartSSLIsFalse.xml");
            int port = imapServer.getListenAddresses().get(0).getPort();

            assertThatThrownBy(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS))
                .hasMessage("Login failed");

        }

        @Test
        void loginShouldSuccessWhenRequireSSLAndEncryptedChannel() throws Exception {
            imapServer = createImapServer("imapServerRequireSSLIsTrueAndStartSSLIsTrue.xml");
            int port = imapServer.getListenAddresses().get(0).getPort();

            IMAPSClient client = new IMAPSClient(false, BogusSslContextFactory.getClientContext());
            client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
            client.connect("127.0.0.1", port);
            client.execTLS();
            client.login(USER.asString(), USER_PASS);

            assertThat(client.getReplyString()).contains("OK LOGIN completed.");
        }

        @Test
        void loginShouldSuccessWhenNOTRequireSSLAndUnEncryptedChannel() throws Exception {
            imapServer = createImapServer("imapServerRequireSSLIsFalseAndStartSSLIsFalse.xml");
            int port = imapServer.getListenAddresses().get(0).getPort();

            assertThatCode(() ->
                testIMAPClient.connect("127.0.0.1", port)
                    .login(USER.asString(), USER_PASS)
                    .append("INBOX", SMALL_MESSAGE))
                .doesNotThrowAnyException();
        }

        @Test
        void loginShouldSuccessWhenNOTRequireSSLAndEncryptedChannel() throws Exception {
            imapServer = createImapServer("imapServerRequireSSLIsFalseAndStartSSLIsTrue.xml");
            int port = imapServer.getListenAddresses().get(0).getPort();

            IMAPSClient client = new IMAPSClient(false, BogusSslContextFactory.getClientContext());
            client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
            client.connect("127.0.0.1", port);
            client.execTLS();
            client.login(USER.asString(), USER_PASS);

            assertThat(client.getReplyString()).contains("OK LOGIN completed.");
        }
    }

    @Nested
    class Oidc {
        String JWKS_URI_PATH = "/jwks";
        String INTROSPECT_TOKEN_URI_PATH = "/introspect";
        String USERINFO_URI_PATH = "/userinfo";
        ClientAndServer authServer;
        IMAPServer imapServer;
        int port;

        @BeforeEach
        void authSetup() throws Exception {
            authServer = ClientAndServer.startClientAndServer(0);
            authServer
                .when(HttpRequest.request().withPath(JWKS_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(OidcTokenFixture.JWKS_RESPONSE, StandardCharsets.UTF_8));

            FakeAuthenticator authenticator = new FakeAuthenticator();
            authenticator.addUser(USER, USER_PASS);
            authenticator.addUser(USER2, USER_PASS);

            InMemoryIntegrationResources integrationResources = InMemoryIntegrationResources.builder()
                .authenticator(authenticator)
                .authorizator(FakeAuthorizator.forGivenUserAndDelegatedUser(USER, USER2))
                .inVmEventBus()
                .defaultAnnotationLimits()
                .defaultMessageParser()
                .scanningSearchIndex()
                .noPreDeletionHooks()
                .storeQuotaManager()
                .build();

            HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("oauth.xml"));
            config.addProperty("auth.oidc.jwksURL", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), JWKS_URI_PATH));
            config.addProperty("auth.oidc.claim", OidcTokenFixture.CLAIM);
            config.addProperty("auth.oidc.oidcConfigurationURL", "https://example.com/jwks");
            config.addProperty("auth.oidc.scope", "email");

            imapServer = createImapServer(config, integrationResources);
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            if (imapServer != null) {
                imapServer.destroy();
            }
            authServer.stop();
        }

        @Test
        void oauthShouldSuccessWhenValidToken() throws Exception {
            String oauthBearer = OIDCSASLHelper.generateOauthBearer(USER.asString(), OidcTokenFixture.VALID_TOKEN);
            IMAPSClient client = imapsClient(port);
            client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
            assertThat(client.getReplyString()).contains("OK AUTHENTICATE completed.");
        }

        @Test
        void oauthShouldFailWhenInValidToken() throws Exception {
            IMAPSClient client = imapsClient(port);
            client.sendCommand("AUTHENTICATE OAUTHBEARER invalidtoken");
            assertThat(client.getReplyString()).contains("NO AUTHENTICATE failed.");
        }

        @Test
        void oauthShouldFailWhenConfigIsNotProvided() throws Exception {
            imapServer.destroy();
            HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("imapServerRequireSSLIsTrueAndStartSSLIsTrue.xml"));
            imapServer = createImapServer(config);
            int port = imapServer.getListenAddresses().get(0).getPort();

            IMAPSClient client = imapsClient(port);
            client.sendCommand("AUTHENTICATE OAUTHBEARER " + OidcTokenFixture.VALID_TOKEN);
            assertThat(client.getReplyString()).contains("NO AUTHENTICATE failed. Authentication mechanism is unsupported.");
        }

        @Test
        void capabilityShouldAdvertiseOAUTHBEARERWhenConfigIsProvided() throws Exception {
            IMAPSClient client = imapsClient(port);
            client.capability();
            assertThat(client.getReplyString()).contains("AUTH=OAUTHBEARER");
        }

        @Test
        void capabilityShouldAdvertiseXOAUTH2WhenConfigIsProvided() throws Exception {
            IMAPSClient client = imapsClient(port);
            client.capability();
            assertThat(client.getReplyString()).contains("AUTH=XOAUTH2");
        }

        @Test
        void oauthShouldSupportOAUTH2Type() throws Exception {
            String oauthBearer = OIDCSASLHelper.generateOauthBearer(USER.asString(), OidcTokenFixture.VALID_TOKEN);
            IMAPSClient client = imapsClient(port);
            client.sendCommand("AUTHENTICATE XOAUTH2 " + oauthBearer);
            assertThat(client.getReplyString()).contains("OK AUTHENTICATE completed.");
        }

        @Test
        void capabilityShouldNotAdvertiseOAUTHBEARERWhenConfigIsNotProvided() throws Exception {
            imapServer.destroy();
            HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("imapServerRequireSSLIsTrueAndStartSSLIsTrue.xml"));
            imapServer = createImapServer(config);
            int port = imapServer.getListenAddresses().get(0).getPort();

            IMAPSClient client = imapsClient(port);
            client.capability();
            assertThat(client.getReplyString()).doesNotContain("AUTH=OAUTHBEARER");
            assertThat(client.getReplyString()).doesNotContain("AUTH=XOAUTH2");
        }

        @Test
        void shouldNotOauthWhenAuthIsReady() throws Exception {
            String oauthBearer = OIDCSASLHelper.generateOauthBearer(USER.asString(), OidcTokenFixture.VALID_TOKEN);
            IMAPSClient client = imapsClient(port);
            client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
            client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
            assertThat(client.getReplyString()).contains("NO AUTHENTICATE failed. Command not valid in this state.");
        }

        @Test
        void appendShouldSuccessWhenAuthenticated() throws Exception {
            String oauthBearer = OIDCSASLHelper.generateOauthBearer(USER.asString(), OidcTokenFixture.VALID_TOKEN);
            IMAPSClient imapsClient = imapsClient(port);
            imapsClient.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
            imapsClient.create("INBOX");
            imapsClient.append("INBOX", null, null, SMALL_MESSAGE);

            assertThat(imapsClient.getReplyString()).contains("APPEND completed.");
        }

        @Test
        void appendShouldFailWhenNotAuthenticated() throws Exception {
            IMAPSClient imapsClient = imapsClient(port);
            imapsClient.create("INBOX");
            assertThat(imapsClient.getReplyString()).contains("Command not valid in this state.");
        }

        @Test
        void oauthShouldFailWhenIntrospectTokenReturnActiveIsFalse() throws Exception {
            imapServer.destroy();

            authServer
                .when(HttpRequest.request().withPath(INTROSPECT_TOKEN_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"active\": false}", StandardCharsets.UTF_8));

            HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("oauth.xml"));
            config.addProperty("auth.oidc.jwksURL", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), JWKS_URI_PATH));
            config.addProperty("auth.oidc.claim", OidcTokenFixture.CLAIM);
            config.addProperty("auth.oidc.oidcConfigurationURL", "https://example.com/jwks");
            config.addProperty("auth.oidc.scope", "email");
            config.addProperty("auth.oidc.introspection.url", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), INTROSPECT_TOKEN_URI_PATH));

            imapServer = createImapServer(config);

            int port = imapServer.getListenAddresses().get(0).getPort();

            String oauthBearer = OIDCSASLHelper.generateOauthBearer(USER.asString(), OidcTokenFixture.VALID_TOKEN);
            IMAPSClient client = imapsClient(port);
            client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
            assertThat(client.getReplyString()).contains("NO AUTHENTICATE failed.");
        }

        @Test
        void oauthShouldSuccessWhenIntrospectTokenReturnActiveIsTrue() throws Exception {
            imapServer.destroy();

            authServer
                .when(HttpRequest.request().withPath(INTROSPECT_TOKEN_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(INTROSPECTION_RESPONSE, StandardCharsets.UTF_8));

            HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("oauth.xml"));
            config.addProperty("auth.oidc.jwksURL", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), JWKS_URI_PATH));
            config.addProperty("auth.oidc.claim", OidcTokenFixture.CLAIM);
            config.addProperty("auth.oidc.oidcConfigurationURL", "https://example.com/jwks");
            config.addProperty("auth.oidc.scope", "email");
            config.addProperty("auth.oidc.introspection.url", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), INTROSPECT_TOKEN_URI_PATH));

            imapServer = createImapServer(config);

            int port = imapServer.getListenAddresses().get(0).getPort();

            String oauthBearer = OIDCSASLHelper.generateOauthBearer(USER.asString(), OidcTokenFixture.VALID_TOKEN);
            IMAPSClient client = imapsClient(port);
            client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
            assertThat(client.getReplyString()).contains("OK AUTHENTICATE completed.");
        }

        @Test
        void oauthShouldFailWhenIntrospectTokenServerError() throws Exception {
            imapServer.destroy();
            String invalidURI = "/invalidURI";
            authServer
                .when(HttpRequest.request().withPath(invalidURI))
                .respond(HttpResponse.response().withStatusCode(401));

            HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("oauth.xml"));
            config.addProperty("auth.oidc.jwksURL", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), JWKS_URI_PATH));
            config.addProperty("auth.oidc.claim", OidcTokenFixture.CLAIM);
            config.addProperty("auth.oidc.oidcConfigurationURL", "https://example.com/jwks");
            config.addProperty("auth.oidc.scope", "email");
            config.addProperty("auth.oidc.introspection.url", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), invalidURI));

            imapServer = createImapServer(config);

            int port = imapServer.getListenAddresses().get(0).getPort();

            String oauthBearer = OIDCSASLHelper.generateOauthBearer(USER.asString(), OidcTokenFixture.VALID_TOKEN);
            IMAPSClient client = imapsClient(port);
            client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
            assertThat(client.getReplyString()).contains("NO AUTHENTICATE processing failed.");
        }

        @Test
        void oauthShouldSuccessWhenCheckTokenByUserInfoIsPassed() throws Exception {
            imapServer.destroy();

            authServer
                .when(HttpRequest.request().withPath(USERINFO_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(OidcTokenFixture.USERINFO_RESPONSE, StandardCharsets.UTF_8));

            HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("oauth.xml"));
            config.addProperty("auth.oidc.jwksURL", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), JWKS_URI_PATH));
            config.addProperty("auth.oidc.claim", OidcTokenFixture.CLAIM);
            config.addProperty("auth.oidc.oidcConfigurationURL", "https://example.com/jwks");
            config.addProperty("auth.oidc.scope", "email");
            config.addProperty("auth.oidc.userinfo.url", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), USERINFO_URI_PATH));

            imapServer = createImapServer(config);

            int port = imapServer.getListenAddresses().get(0).getPort();

            String oauthBearer = OIDCSASLHelper.generateOauthBearer(USER.asString(), OidcTokenFixture.VALID_TOKEN);
            IMAPSClient client = imapsClient(port);
            client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
            assertThat(client.getReplyString()).contains("OK AUTHENTICATE completed.");
        }

        @Test
        void oauthShouldFailWhenCheckTokenByUserInfoIsFailed() throws Exception {
            imapServer.destroy();

            authServer
                .when(HttpRequest.request().withPath(USERINFO_URI_PATH))
                .respond(HttpResponse.response().withStatusCode(401)
                    .withHeader("Content-Type", "application/json"));

            HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("oauth.xml"));
            config.addProperty("auth.oidc.jwksURL", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), JWKS_URI_PATH));
            config.addProperty("auth.oidc.claim", OidcTokenFixture.CLAIM);
            config.addProperty("auth.oidc.oidcConfigurationURL", "https://example.com/jwks");
            config.addProperty("auth.oidc.scope", "email");
            config.addProperty("auth.oidc.userinfo.url", String.format("http://127.0.0.1:%s%s", authServer.getLocalPort(), USERINFO_URI_PATH));

            imapServer = createImapServer(config);

            int port = imapServer.getListenAddresses().get(0).getPort();

            String oauthBearer = OIDCSASLHelper.generateOauthBearer(USER.asString(), OidcTokenFixture.VALID_TOKEN);
            IMAPSClient client = imapsClient(port);
            client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
            assertThat(client.getReplyString()).contains("NO AUTHENTICATE processing failed.");
        }

        @Test
        void oauthShouldImpersonateFailWhenNOTDelegated() throws Exception {
            String oauthBearer = OIDCSASLHelper.generateOauthBearer(USER3.asString(), OidcTokenFixture.VALID_TOKEN);
            IMAPSClient client = imapsClient(port);
            client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
            assertThat(client.getReplyString()).contains("NO AUTHENTICATE");
        }

        @Test
        void oauthShouldImpersonateSuccessWhenDelegated() throws Exception {
            String oauthBearer = OIDCSASLHelper.generateOauthBearer(USER2.asString(), OidcTokenFixture.VALID_TOKEN);
            IMAPSClient client = imapsClient(port);
            client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
            assertThat(client.getReplyString()).contains("OK AUTHENTICATE completed.");
        }

        @Test
        void impersonationShouldWorkWhenDelegated() throws Exception {
            // USER2: append a message
            try (TestIMAPClient client = new TestIMAPClient(imapsClient(port))) {
                client.login(USER2.asString(), USER_PASS)
                    .append("INBOX", SMALL_MESSAGE);
            }

            // USER1 authenticate and impersonate as USER2
            try (TestIMAPClient client = new TestIMAPClient(imapsClient(port))) {
                String oauthBearer = OIDCSASLHelper.generateOauthBearer(USER2.asString(), OidcTokenFixture.VALID_TOKEN);
                String authenticateResponse = client.sendCommand("AUTHENTICATE OAUTHBEARER " + oauthBearer);
                assertThat(authenticateResponse).contains("OK AUTHENTICATE completed.");

                assertThat(client.select("INBOX")
                    .readFirstMessage()).contains(SMALL_MESSAGE);
            }
        }
    }

    private AuthenticatingIMAPClient imapsClient(int port) throws Exception {
        AuthenticatingIMAPClient client = new AuthenticatingIMAPClient(false, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        client.connect("127.0.0.1", port);
        client.execTLS();
        return client;
    }

    @Nested
    class Search {
        IMAPServer imapServer;
        private int port;
        private SocketChannel clientConnection;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServer.xml");
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

        // Not an MPT test as ThreadId is a variable server-set and implementation specific
        @Test
        void imapSearchShouldSupportThreadId() throws Exception {
            MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
            memoryIntegrationResources.getMailboxManager()
                .createMailbox(MailboxPath.inbox(USER), mailboxSession);
            MessageManager.AppendResult appendResult = memoryIntegrationResources.getMailboxManager()
                .getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .appendMessage(MessageManager.AppendCommand.builder().build("MIME-Version: 1.0\r\n" +
                    "Content-Type: text/html; charset=UTF-8\r\n" +
                    "Content-Transfer-Encoding: quoted-printable\r\n" +
                    "From: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                    "Sender: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                    "Reply-To: b@linagora.com\r\n" +
                    "To: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                    "Subject: Test utf-8 charset\r\n" +
                    "Message-ID: <Mime4j.5f1.9a40f68264d6f2fa.17876fb5605@linagora.com>\r\n" +
                    "Date: Sun, 28 Mar 2021 03:58:06 +0000\r\n" +
                    "\r\n" +
                    "<p>=E5=A4=A9=E5=A4=A9=E5=90=91=E4=B8=8A<br></p>\r\n"), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a0 OK"));
            clientConnection.write(ByteBuffer.wrap("a1 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK"));
            clientConnection.write(ByteBuffer.wrap(String.format("a2 UID SEARCH THREADID %s\r\n", appendResult.getThreadId().serialize()).getBytes(StandardCharsets.UTF_8)));

            readStringUntil(clientConnection, s -> s.contains(("* SEARCH " + appendResult.getId().getUid().asLong())));
        }

        // Not an MPT test as ThreadId is a variable server-set and implementation specific
        @Test
        void imapSearchShouldSupportEmailId() throws Exception {
            MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
            memoryIntegrationResources.getMailboxManager()
                .createMailbox(MailboxPath.inbox(USER), mailboxSession);
            MessageManager.AppendResult appendResult = memoryIntegrationResources.getMailboxManager()
                .getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .appendMessage(MessageManager.AppendCommand.builder().build("MIME-Version: 1.0\r\n" +
                    "Content-Type: text/html; charset=UTF-8\r\n" +
                    "Content-Transfer-Encoding: quoted-printable\r\n" +
                    "From: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                    "Sender: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                    "Reply-To: b@linagora.com\r\n" +
                    "To: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                    "Subject: Test utf-8 charset\r\n" +
                    "Message-ID: <Mime4j.5f1.9a40f68264d6f2fa.17876fb5605@linagora.com>\r\n" +
                    "Date: Sun, 28 Mar 2021 03:58:06 +0000\r\n" +
                    "\r\n" +
                    "<p>=E5=A4=A9=E5=A4=A9=E5=90=91=E4=B8=8A<br></p>\r\n"), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a0 OK"));
            clientConnection.write(ByteBuffer.wrap("a1 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK"));
            clientConnection.write(ByteBuffer.wrap(String.format("a2 UID SEARCH EMAILID %s\r\n", appendResult.getId().getMessageId().serialize()).getBytes(StandardCharsets.UTF_8)));

            readStringUntil(clientConnection, s -> s.contains(("* SEARCH " + appendResult.getId().getUid().asLong())));
        }

        @Test
        void searchingShouldSupportMultipleUTF8Criteria() throws Exception {
            String host = "127.0.0.1";
            Properties props = new Properties();
            props.put("mail.debug", "true");
            Session session = Session.getDefaultInstance(props, null);
            Store store = session.getStore("imap");
            store.connect(host, port, USER.asString(), USER_PASS);
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);

            SearchTerm subjectTerm = new SubjectTerm("java培训");
            SearchTerm fromTerm = new FromStringTerm("采购");
            SearchTerm recipientTerm = new RecipientStringTerm(Message.RecipientType.TO,"张三");
            SearchTerm ccRecipientTerm = new RecipientStringTerm(Message.RecipientType.CC,"李四");
            SearchTerm bccRecipientTerm = new RecipientStringTerm(Message.RecipientType.BCC,"王五");
            SearchTerm bodyTerm = new BodyTerm("天天向上");
            SearchTerm[] searchTerms = new SearchTerm[6];
            searchTerms[0] = subjectTerm;
            searchTerms[1] = bodyTerm;
            searchTerms[2] = fromTerm;
            searchTerms[3] = recipientTerm;
            searchTerms[4] = ccRecipientTerm;
            searchTerms[5] = bccRecipientTerm;
            SearchTerm andTerm = new AndTerm(searchTerms);

            assertThatCode(() -> folder.search(andTerm)).doesNotThrowAnyException();

            folder.close(false);
            store.close();
        }

        @Test
        void searchingASingleUTF8CriterionShouldComplete() throws Exception {
            MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
            memoryIntegrationResources.getMailboxManager()
                .createMailbox(MailboxPath.inbox(USER), mailboxSession);
            memoryIntegrationResources.getMailboxManager()
                .getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .appendMessage(MessageManager.AppendCommand.builder().build("MIME-Version: 1.0\r\n" +
                    "Content-Type: text/html; charset=UTF-8\r\n" +
                    "Content-Transfer-Encoding: quoted-printable\r\n" +
                    "From: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                    "Sender: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                    "Reply-To: b@linagora.com\r\n" +
                    "To: =?ISO-8859-1?Q?Beno=EEt_TELLIER?= <b@linagora.com>\r\n" +
                    "Subject: Test utf-8 charset\r\n" +
                    "Message-ID: <Mime4j.5f1.9a40f68264d6f2fa.17876fb5605@linagora.com>\r\n" +
                    "Date: Sun, 28 Mar 2021 03:58:06 +0000\r\n" +
                    "\r\n" +
                    "<p>=E5=A4=A9=E5=A4=A9=E5=90=91=E4=B8=8A<br></p>\r\n"), mailboxSession);

            String host = "127.0.0.1";
            Properties props = new Properties();
            props.put("mail.debug", "true");
            Session session = Session.getDefaultInstance(props, null);
            Store store = session.getStore("imap");
            store.connect(host, port, USER.asString(), USER_PASS);
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);

            SearchTerm bodyTerm = new BodyTerm("天天向上");

            assertThat(folder.search(bodyTerm)).hasSize(1);

            folder.close(false);
            store.close();
        }
    }

    @Nested
    class IdleSSL {
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
            ctx.init(null, new TrustManager[] { new BlindTrustManager() }, null);
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

    @Nested
    class SSL {
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
            ctx.init(null, new TrustManager[] { new BlindTrustManager() }, null);
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
        void startTlsCapabilityShouldFailWhenSSLSocket() throws Exception {
            clientConnection.getOutputStream().write("a0 STARTTLS\r\n".getBytes(StandardCharsets.UTF_8));
            assertThat(readString(clientConnection)).startsWith("a0 BAD STARTTLS failed. Unknown command.");
        }

        @Test
        void startTlsCapabilityShouldNotBeAdvertisedWhenSSLSocket() throws Exception {
            clientConnection.getOutputStream().write("a0 CAPABILITY\r\n".getBytes(StandardCharsets.UTF_8));
            assertThat(readString(clientConnection)).doesNotContain("STARTTLS");
        }

        private String readString(Socket channel) throws IOException {
            byte[] buffer = new byte[8193];
            int read = channel.getInputStream().read(buffer);
            return new String(buffer, 0, read, StandardCharsets.US_ASCII);
        }
    }

    @Nested
    class IdleSSLCompress {
        IMAPServer imapServer;
        private Connection connection;
        private ConcurrentLinkedDeque<String> responses;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServerSSLCompress.xml");
            int port = imapServer.getListenAddresses().get(0).getPort();
            MailboxSession mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
            memoryIntegrationResources.getMailboxManager()
                .createMailbox(MailboxPath.inbox(USER), mailboxSession);

            connection = TcpClient.create()
                .secure(ssl -> ssl.sslContext(SslContextBuilder.forClient().trustManager(new BlindTrustManager())))
                .remoteAddress(() -> new InetSocketAddress(LOCALHOST_IP, port))
                .connectNow();
            responses = new ConcurrentLinkedDeque<>();
            readBytes(connection);
        }

        @AfterEach
        void tearDown() {
            try {
                connection.disposeNow();
            } finally {
                imapServer.destroy();
            }
        }

        @Test
        void idleShouldBeInterruptible() {
            send(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS));

            send("a1 COMPRESS DEFLATE\r\n");

            Awaitility.await().until(() -> responses.stream().anyMatch(s -> s.contains("a1 OK COMPRESS DEFLATE active")));
            responses.clear();

            connection.addHandlerFirst(new JdkZlibDecoder(ZlibWrapper.ZLIB_OR_NONE));
            connection.addHandlerFirst(new JdkZlibEncoder(ZlibWrapper.NONE, 5));

            send("a2 SELECT INBOX\r\n");
            Awaitility.await().until(() -> responses.stream().anyMatch(s -> s.contains("a2 OK [READ-WRITE] SELECT completed.")));
            responses.clear();

            send("a3 IDLE\r\n");
            Awaitility.await().until(() -> responses.stream().anyMatch(s -> s.contains("+ Idling")));
            assertThat(responses).hasSize(1); // No pollution
            responses.clear();

            send("DONE\r\n");
            Awaitility.await().until(() -> responses.stream().anyMatch(s -> s.contains("a3 OK IDLE completed.")));
            assertThat(responses).hasSize(1); // No pollution
        }

        private void send(String format) {
            connection.outbound()
                .send(Mono.just(Unpooled.wrappedBuffer(format
                    .getBytes(StandardCharsets.UTF_8))))
                .then()
                .subscribe();
        }

        private void readBytes(Connection connection) {
            connection.inbound().receive().asString()
                .doOnNext(responses::addLast)
                .subscribeOn(Schedulers.newSingle("test"))
                .subscribe();
        }
    }

    @Nested
    class Idle {
        IMAPServer imapServer;
        private int port;
        private MailboxSession mailboxSession;
        private MessageManager inbox;
        private SocketChannel clientConnection;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServer.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
            mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
            memoryIntegrationResources.getMailboxManager()
                .createMailbox(MailboxPath.inbox(USER), mailboxSession);
            inbox = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession);

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
        void idleShouldSendInitialContinuation() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

            clientConnection.write(ByteBuffer.wrap(("a3 IDLE\r\n").getBytes(StandardCharsets.UTF_8)));


            Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(readStringUntil(clientConnection, s -> s.contains("+ Idling")))
                .isNotNull());
        }

        @Test
        void idleShouldBeInterruptible() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

            clientConnection.write(ByteBuffer.wrap(("a3 IDLE\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("+ Idling"));

            clientConnection.write(ByteBuffer.wrap(("DONE\r\n").getBytes(StandardCharsets.UTF_8)));

            Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(readStringUntil(clientConnection, s -> s.contains("a3 OK IDLE completed.")))
                    .isNotNull());
        }

        @Test
        void idleShouldBeInterruptibleWhenBatched() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

            clientConnection.write(ByteBuffer.wrap(("a3 IDLE\r\nDONE\r\n").getBytes(StandardCharsets.UTF_8)));

            Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(readStringUntil(clientConnection, s -> s.contains("a3 OK IDLE completed.")))
                    .isNotNull());
        }

        @Test
        void idleResponsesShouldBeOrdered() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

            clientConnection.write(ByteBuffer.wrap(("a3 IDLE\r\nDONE\r\n").getBytes(StandardCharsets.UTF_8)));

            // Assert continuation is sent before IDLE completion result
            Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(readStringUntil(clientConnection, s -> s.contains("a3 OK IDLE completed.")))
                    .filteredOn(s -> s.contains("+ Idling"))
                    .hasSize(1));
        }

        @Test
        void idleShouldReturnUnderstandableErrorMessageWhenBadDone() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

            clientConnection.write(ByteBuffer.wrap(("a3 IDLE\r\nBAD\r\n").getBytes(StandardCharsets.UTF_8)));

            Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(readStringUntil(clientConnection, s -> s.contains("a3 BAD IDLE failed. Continuation for IMAP IDLE was not understood. Expected 'DONE', got 'BAD'.")))
                    .isNotNull());
        }

        // Repeated run to detect more reliably data races
        @RepeatedTest(50)
        void idleShouldReturnUpdates() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

            clientConnection.write(ByteBuffer.wrap(("a3 IDLE\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("+ Idling"));

            inbox.appendMessage(MessageManager.AppendCommand.builder().build("h: value\r\n\r\nbody".getBytes()), mailboxSession);

            Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(readStringUntil(clientConnection, s -> s.contains("* 1 EXISTS")))
                .isNotNull());
        }
    }

    @Nested
    class CondStore {
        IMAPServer imapServer;
        private MailboxSession mailboxSession;
        private MessageManager inbox;
        private SocketChannel clientConnection;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServer.xml");
            int port = imapServer.getListenAddresses().get(0).getPort();
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
        void fetchShouldSupportChangedSince() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));


            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(14)), mailboxSession);
            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(31)), mailboxSession);

            ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(2)), mailboxSession);
            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);
            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

            clientConnection.write(ByteBuffer.wrap(String.format("a3 UID FETCH 1:* (FLAGS) (CHANGEDSINCE %d)\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

            List<String> replies = readStringUntil(clientConnection, s -> s.contains("a3 OK FETCH completed."));
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(replies)
                    .filteredOn(s -> s.contains("* 2 FETCH (MODSEQ (41) FLAGS (\\Answered \\Recent) UID 2)"))
                    .hasSize(1);
                softly.assertThat(replies)
                    .filteredOn(s -> s.contains("* 25 FETCH (MODSEQ (42) FLAGS (\\Answered \\Recent) UID 25)"))
                    .hasSize(1);
                softly.assertThat(replies)
                    .filteredOn(s -> s.contains("* 35 FETCH (MODSEQ (43) FLAGS (\\Answered \\Recent) UID 35)"))
                    .hasSize(1);

                softly.assertThat(replies)
                    .filteredOn(s -> s.contains("* 14 FETCH (MODSEQ (39) FLAGS (\\Answered \\Recent) UID 14)"))
                    .isEmpty();
                softly.assertThat(replies)
                    .filteredOn(s -> s.contains("* 31 FETCH (MODSEQ (40) FLAGS (\\Answered \\Recent) UID 31)"))
                    .isEmpty();
            });
        }

        @Test
        void searchShouldSupportModSeq() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));


            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(14)), mailboxSession);
            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(31)), mailboxSession);

            ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(2)), mailboxSession);
            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);
            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

            clientConnection.write(ByteBuffer.wrap(String.format("A150 SEARCH MODSEQ %d\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("A150 OK SEARCH completed.")))
                .filteredOn(s -> s.contains("* SEARCH 2 25 31 35 (MODSEQ 43)"))
                .hasSize(1);
        }

        @Test
        void searchShouldSupportModSeqWithFlagRestrictions() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));


            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(14)), mailboxSession);
            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(31)), mailboxSession);

            ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(2)), mailboxSession);
            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);
            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

            clientConnection.write(ByteBuffer.wrap(String.format("a SEARCH MODSEQ \"/flags/\\\\draft\" all %d\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

            // Restrictions are not applied however
            assertThat(readStringUntil(clientConnection, s -> s.contains("a OK SEARCH completed.")))
                .filteredOn(s -> s.contains("* SEARCH 2 25 31 35 (MODSEQ 43)"))
                .hasSize(1);
        }

        @Test
        void statusShouldAcceptHighestModSeqItem() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(2)), mailboxSession);
            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);
            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);
            ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

            clientConnection.write(ByteBuffer.wrap(("A042 STATUS INBOX (HIGHESTMODSEQ)\r\n").getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("A042 OK STATUS completed.")))
                .filteredOn(s -> s.contains(String.format("* STATUS \"INBOX\" (HIGHESTMODSEQ %d)", highestModSeq.asLong())))
                .hasSize(1);
        }

        @Test
        void selectShouldAcceptCondstore() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            clientConnection.write(ByteBuffer.wrap(("A142 SELECT INBOX (CONDSTORE)\r\n").getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("A142 OK [READ-WRITE] SELECT completed.")))
                .isNotNull();
        }

        @Test
        void selectShouldEnableCondstore() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            clientConnection.write(ByteBuffer.wrap(("A142 SELECT INBOX (CONDSTORE)\r\n").getBytes(StandardCharsets.UTF_8)));

            readStringUntil(clientConnection, s -> s.contains("A142 OK [READ-WRITE] SELECT completed."));

            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
            assertThat(readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed.")))
                .filteredOn(s -> s.contains("* 35 FETCH (MODSEQ (39) FLAGS (\\Answered \\Recent))"))
                .hasSize(1);
        }

        @Test
        void storeShouldSucceedWhenUnchangedSinceIsNotExceeded() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            clientConnection.write(ByteBuffer.wrap(("A142 SELECT INBOX (CONDSTORE)\r\n").getBytes(StandardCharsets.UTF_8)));

            readStringUntil(clientConnection, s -> s.contains("A142 OK [READ-WRITE] SELECT completed."));

            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);
            ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

            clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

            clientConnection.write(ByteBuffer.wrap((String.format("a103 UID STORE 35 (UNCHANGEDSINCE %d) +FLAGS.SILENT (\\Seen)\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8))));
            assertThat(readStringUntil(clientConnection, s -> s.contains("a103 OK STORE completed.")))
                .filteredOn(s -> s.contains("* 35 FETCH (MODSEQ (40) UID 35)"))
                .hasSize(1);
        }

        @Test
        void storeShouldSucceedWhenUnchangedSinceIsNotExceededAndNotSilent() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            clientConnection.write(ByteBuffer.wrap(("A142 SELECT INBOX (CONDSTORE)\r\n").getBytes(StandardCharsets.UTF_8)));

            readStringUntil(clientConnection, s -> s.contains("A142 OK [READ-WRITE] SELECT completed."));

            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);
            ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

            clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

            clientConnection.write(ByteBuffer.wrap((String.format("a103 UID STORE 35 (UNCHANGEDSINCE %d) +FLAGS (\\Seen)\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8))));
            assertThat(readStringUntil(clientConnection, s -> s.contains("a103 OK STORE completed.")))
                .filteredOn(s -> s.contains("* 35 FETCH (MODSEQ (40) FLAGS (\\Answered \\Recent \\Seen) UID 35)"))
                .hasSize(1);
        }

        @Test
        void storeShouldFailWhenUnchangedSinceIsExceeded() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            clientConnection.write(ByteBuffer.wrap(("A142 SELECT INBOX (CONDSTORE)\r\n").getBytes(StandardCharsets.UTF_8)));

            readStringUntil(clientConnection, s -> s.contains("A142 OK [READ-WRITE] SELECT completed."));

            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);
            ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

            clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

            clientConnection.write(ByteBuffer.wrap((String.format("a103 UID STORE 35 (UNCHANGEDSINCE %d) +FLAGS.SILENT (\\Seen)\r\n", highestModSeq.asLong() - 1).getBytes(StandardCharsets.UTF_8))));
            assertThat(readStringUntil(clientConnection, s -> s.contains("a103 OK [MODIFIED 0 35] STORE failed.")))
                .isNotNull();
        }

        @Test
        void storeShouldFailWhenUnchangedSinceIsZero() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            clientConnection.write(ByteBuffer.wrap(("A142 SELECT INBOX (CONDSTORE)\r\n").getBytes(StandardCharsets.UTF_8)));

            readStringUntil(clientConnection, s -> s.contains("A142 OK [READ-WRITE] SELECT completed."));

            inbox.setFlags(new Flags("dcustom"), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);
            ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

            clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

            clientConnection.write(ByteBuffer.wrap((String.format("a103 UID STORE 35 (UNCHANGEDSINCE 0) +FLAGS.SILENT (dcustom)\r\n", highestModSeq.asLong() - 1).getBytes(StandardCharsets.UTF_8))));
            assertThat(readStringUntil(clientConnection, s -> s.contains("a103 OK [MODIFIED 0 35] STORE failed.")))
                .isNotNull();
        }

        @Test
        void storeShouldFailWhenUnchangedSinceIsZeroAndMsn() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            clientConnection.write(ByteBuffer.wrap(("A142 SELECT INBOX (CONDSTORE)\r\n").getBytes(StandardCharsets.UTF_8)));

            readStringUntil(clientConnection, s -> s.contains("A142 OK [READ-WRITE] SELECT completed."));

            inbox.setFlags(new Flags("dcustom"), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);
            ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

            clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

            clientConnection.write(ByteBuffer.wrap((String.format("a103 STORE 35 (UNCHANGEDSINCE 0) +FLAGS.SILENT (dcustom)\r\n", highestModSeq.asLong() - 1).getBytes(StandardCharsets.UTF_8))));
            assertThat(readStringUntil(clientConnection, s -> s.contains("a103 OK [MODIFIED 0 35] STORE failed.")))
                .isNotNull();
        }

        @Test
        void storeShouldFailWhenUnchangedSinceIsZeroAndSystemFlagsUpdate() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            clientConnection.write(ByteBuffer.wrap(("A142 SELECT INBOX (CONDSTORE)\r\n").getBytes(StandardCharsets.UTF_8)));

            readStringUntil(clientConnection, s -> s.contains("A142 OK [READ-WRITE] SELECT completed."));

            inbox.setFlags(new Flags("dcustom"), REPLACE, MessageRange.one(MessageUid.of(35)), mailboxSession);
            ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

            clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

            clientConnection.write(ByteBuffer.wrap((String.format("a103 UID STORE 35 (UNCHANGEDSINCE 0) +FLAGS.SILENT (\\Answered)\r\n", highestModSeq.asLong() - 1).getBytes(StandardCharsets.UTF_8))));
            assertThat(readStringUntil(clientConnection, s -> s.contains("a103 OK [MODIFIED 0 35] STORE failed.")))
                .isNotNull();
        }

        @Test
        void storeShouldFailWhenSomeMessagesDoNotMatch() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            clientConnection.write(ByteBuffer.wrap(("A142 SELECT INBOX (CONDSTORE)\r\n").getBytes(StandardCharsets.UTF_8)));

            readStringUntil(clientConnection, s -> s.contains("A142 OK [READ-WRITE] SELECT completed."));

            ModSeq highestModSeq = inbox.getMetaData(IGNORE, mailboxSession, NO_COUNT).getHighestModSeq();

            inbox.setFlags(new Flags("custom"), REPLACE, MessageRange.one(MessageUid.of(7)), mailboxSession);
            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(9)), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(("a2 NOOP\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK NOOP completed."));

            clientConnection.write(ByteBuffer.wrap((String.format("a103 UID STORE 5,7,9 (UNCHANGEDSINCE %d) +FLAGS.SILENT (\\Seen)\r\n", highestModSeq.asLong() - 1).getBytes(StandardCharsets.UTF_8))));
            assertThat(readStringUntil(clientConnection, s -> s.contains("a103 OK [MODIFIED 0 5,7,9] STORE failed.")))
                .filteredOn(s -> s.contains("FETCH"))
                .isEmpty();
        }
    }

    @Nested
    class SequentialExecution {
        IMAPServer imapServer;
        private MailboxSession mailboxSession;
        private MessageManager inbox;
        private SocketChannel clientConnection;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServer.xml");
            int port = imapServer.getListenAddresses().get(0).getPort();
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

            Thread.sleep(1000);

            assertThat(loaded.get()).isLessThan(500);
            readStringUntil(clientConnection, s -> s.contains("A2 OK FETCH completed."));
            assertThat(loaded.get()).isEqualTo(500);
        }


        @Test
        void fetchShouldBackPressureWhenIMAPServerMemoryIsFullLoadingMessages() throws Exception {
            String msgIn = "MIME-Version: 1.0\r\n\r\nCONTENT\r\n\r\n" + "0123456789\r\n0123456789\r\n0123456789\r\n".repeat(8000);
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

            Thread.sleep(1000);

            assertThat(loaded.get()).isLessThan(500);
            readStringUntil(clientConnection, s -> s.contains("A2 OK FETCH completed."));
            assertThat(loaded.get()).isEqualTo(500);
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
    class QResync {
        IMAPServer imapServer;
        private MailboxSession mailboxSession;
        private MessageManager inbox;
        private SocketChannel clientConnection;

        @BeforeEach
        void beforeEach() throws Exception {
            imapServer = createImapServer("imapServer.xml");
            int port = imapServer.getListenAddresses().get(0).getPort();
            mailboxSession = memoryIntegrationResources.getMailboxManager().createSystemSession(USER);
            memoryIntegrationResources.getMailboxManager()
                .createMailbox(MailboxPath.inbox(USER), mailboxSession);
            inbox = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession);

            clientConnection = SocketChannel.open();
            clientConnection.connect(new InetSocketAddress(LOCALHOST_IP, port));
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

        @Test
        void selectShouldNotAnswerEmptyVanishedResponses() throws Exception {
            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

            UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
                .getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMailboxEntity().getUidValidity();

            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
            clientConnection.write(ByteBuffer.wrap(String.format("I00104 SELECT INBOX (QRESYNC (%d 88 2:37 (1,10,28 2,11,29)))\r\n", uidValidity.asLong()).getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed.")))
                .filteredOn(s -> s.contains("VANISHED"))
                .isEmpty();
        }

        @Test
        void selectShouldReturnDeletedMessagesWhenNoSubsequentModification() throws Exception {
            inbox.delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

            ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMetaData(IGNORE, mailboxSession, NO_COUNT)
                .getHighestModSeq();

            UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
                .getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMailboxEntity().getUidValidity();

            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .delete(ImmutableList.of(MessageUid.of(10)), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
            clientConnection.write(ByteBuffer.wrap(String.format("I00104 SELECT INBOX (QRESYNC (%d %d 2:37 (1,10,28 2,11,29)))\r\n", uidValidity.asLong(), highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed.")))
                .filteredOn(s -> s.contains("* VANISHED (EARLIER) 10"))
                .hasSize(1);
        }

        @Test
        void selectShouldReturnDeletedMessagesWhenSequenceMatchDataAndNoKnownUid() throws Exception {
            inbox.delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

            ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMetaData(IGNORE, mailboxSession, NO_COUNT)
                .getHighestModSeq();

            UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
                .getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMailboxEntity().getUidValidity();

            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .delete(ImmutableList.of(MessageUid.of(10)), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
            clientConnection.write(ByteBuffer.wrap(String.format("I00104 SELECT INBOX (QRESYNC (%d %d (1,10,28 2,11,29)))\r\n", uidValidity.asLong(), highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed.")))
                .filteredOn(s -> s.contains("* VANISHED (EARLIER) 10"))
                .hasSize(1);
        }

        @Test
        void selectShouldReturnDeletedMessagesWhenKnownUidSet() throws Exception {
            inbox.delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

            ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMetaData(IGNORE, mailboxSession, NO_COUNT)
                .getHighestModSeq();

            UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
                .getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMailboxEntity().getUidValidity();

            inbox.delete(ImmutableList.of(MessageUid.of(10), MessageUid.of(11), MessageUid.of(12),
                MessageUid.of(25), MessageUid.of(26),
                MessageUid.of(32)), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
            clientConnection.write(ByteBuffer.wrap(String.format("I00104 SELECT INBOX (QRESYNC (%d %d 5:11,28:36 (1,10,28 2,11,29)))\r\n", uidValidity.asLong(), highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed.")))
                .filteredOn(s -> s.contains("* VANISHED (EARLIER) 10:11,32"))
                .hasSize(1);
        }

        @Test
        void knownUidSetShouldBeUsedToRestrictVanishedResponses() throws Exception {
            inbox.delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

            ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMetaData(IGNORE, mailboxSession, NO_COUNT)
                .getHighestModSeq();

            UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
                .getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMailboxEntity().getUidValidity();

            inbox.delete(ImmutableList.of(MessageUid.of(10), MessageUid.of(11), MessageUid.of(12),
                MessageUid.of(25), MessageUid.of(26),
                MessageUid.of(32)), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
            // MSN 1 => UID 2 MATCH
            // MSN 13 => UID 17 MATCH
            // MSN 28 => UID 30 MISMATCH stored value is 34
            // Thus we know we can skip resynchronisation for UIDs up to 17
            clientConnection.write(ByteBuffer.wrap(String.format("I00104 SELECT INBOX (QRESYNC (%d %d 1:37 (1,13,28 2,17,30)))\r\n", uidValidity.asLong(), highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed.")))
                .filteredOn(s -> s.contains("* VANISHED (EARLIER) 25:26,32"))
                .hasSize(1);
        }

        @Test
        void knownUidSetShouldTolerateDeletedMessages() throws Exception {
            inbox.delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

            ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMetaData(IGNORE, mailboxSession, NO_COUNT)
                .getHighestModSeq();

            UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
                .getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMailboxEntity().getUidValidity();

            inbox.delete(ImmutableList.of(MessageUid.of(10), MessageUid.of(11), MessageUid.of(12),
                MessageUid.of(25), MessageUid.of(26),
                MessageUid.of(32)), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
            // MSN 1 => UID 2 MATCH
            // MSN 13 => UID 17 MATCH
            // MSN 28 => UID 32 MISMATCH stored value is 34 (32 not being stored)
            // Thus we know we can skip resynchronisation for UIDs up to 17
            clientConnection.write(ByteBuffer.wrap(String.format("I00104 SELECT INBOX (QRESYNC (%d %d 1:37 (1,13,28 2,17,32)))\r\n", uidValidity.asLong(), highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed.")))
                .filteredOn(s -> s.contains("* VANISHED (EARLIER) 25:26,32"))
                .hasSize(1);
        }

        @Test
        void selectShouldReturnDeletedMessagesWhenNoSequenceMatchData() throws Exception {
            inbox.delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

            ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMetaData(IGNORE, mailboxSession, NO_COUNT)
                .getHighestModSeq();

            UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
                .getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMailboxEntity().getUidValidity();

            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .delete(ImmutableList.of(MessageUid.of(10)), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
            clientConnection.write(ByteBuffer.wrap(String.format("I00104 SELECT INBOX (QRESYNC (%d %d 2:37))\r\n", uidValidity.asLong(), highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed.")))
                .filteredOn(s -> s.contains("* VANISHED (EARLIER) 10"))
                .hasSize(1);
        }

        @Test
        void selectShouldReturnDeletedMessagesWhenNoSequenceMatchDataAndKnownUid() throws Exception {
            inbox.delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

            ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMetaData(IGNORE, mailboxSession, NO_COUNT)
                .getHighestModSeq();

            UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
                .getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMailboxEntity().getUidValidity();

            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .delete(ImmutableList.of(MessageUid.of(10)), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
            clientConnection.write(ByteBuffer.wrap(String.format("I00104 SELECT INBOX (QRESYNC (%d %d))\r\n", uidValidity.asLong(), highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed.")))
                .filteredOn(s -> s.contains("* VANISHED (EARLIER) 10"))
                .hasSize(1);
        }

        @Test
        void selectShouldCombineIntoRangesWhenRespondingVanished() throws Exception {
            inbox.delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

            ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMetaData(IGNORE, mailboxSession, NO_COUNT)
                .getHighestModSeq();

            UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
                .getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMailboxEntity().getUidValidity();

            inbox.delete(ImmutableList.of(MessageUid.of(10), MessageUid.of(11), MessageUid.of(12),
                    MessageUid.of(25), MessageUid.of(26),
                    MessageUid.of(32)), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
            clientConnection.write(ByteBuffer.wrap(String.format("I00104 SELECT INBOX (QRESYNC (%d %d 2:37 (1,10,28 2,11,29)))\r\n", uidValidity.asLong(), highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed.")))
                .filteredOn(s -> s.contains("* VANISHED (EARLIER) 10:12,25:26,32"))
                .hasSize(1);
        }

        @Test
        void enableQRESYNCShouldReturnHighestModseq() throws Exception {
            inbox.delete(ImmutableList.of(MessageUid.MIN_VALUE), mailboxSession);

            ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMetaData(IGNORE, mailboxSession, NO_COUNT)
                .getHighestModSeq();

            UidValidity uidValidity = memoryIntegrationResources.getMailboxManager()
                .getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMailboxEntity().getUidValidity();

            inbox.delete(ImmutableList.of(MessageUid.of(10), MessageUid.of(11), MessageUid.of(12),
                    MessageUid.of(25), MessageUid.of(26),
                    MessageUid.of(32)), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap("I00104 SELECT INBOX\r\n".getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("I00104 OK [READ-WRITE] SELECT completed."));

            clientConnection.write(ByteBuffer.wrap(("a2 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));

            List<String> replies = readStringUntil(clientConnection, s -> s.contains("a2 OK ENABLE completed."));
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(replies)
                    .filteredOn(s -> s.contains("* OK [HIGHESTMODSEQ 41] Highest"))
                    .hasSize(1);
                softly.assertThat(replies)
                    .filteredOn(s -> s.contains("* ENABLED QRESYNC"))
                    .hasSize(1);
            });
        }

        private void setUpTestingData() {
            IntStream.range(0, 37)
                .forEach(Throwing.intConsumer(i -> inbox.appendMessage(MessageManager.AppendCommand.builder()
                    .build("MIME-Version: 1.0\r\n\r\nCONTENT\r\n"), mailboxSession)));
        }

        @Test
        void fetchShouldAllowChangedSinceModifier() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
            clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(10)), mailboxSession);

            ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMetaData(IGNORE, mailboxSession, NO_COUNT)
                .getHighestModSeq();
            clientConnection.write(ByteBuffer.wrap(String.format("I00104 UID FETCH 1:37 (FLAGS) (CHANGEDSINCE %d)\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK FETCH completed.")))
                .filteredOn(s -> s.contains("* 10 FETCH (MODSEQ (39) FLAGS (\\Answered \\Recent) UID 10)"))
                .hasSize(1);
        }

        @Test
        void fetchShouldNotReturnChangedItemsOutOfRange() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
            clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

            inbox.setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(10)), mailboxSession);

            ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMetaData(IGNORE, mailboxSession, NO_COUNT)
                .getHighestModSeq();
            clientConnection.write(ByteBuffer.wrap(String.format("I00104 UID FETCH 12:37 (FLAGS) (CHANGEDSINCE %d)\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK FETCH completed.")))
                .filteredOn(s -> s.contains("FLAGS")) // No FLAGS FETCH responses
                .hasSize(1);
        }

        @Test
        void fetchShouldSupportVanishedModifiedWithEarlierTag() throws Exception {
            inbox.delete(ImmutableList.of(MessageUid.of(14)), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
            clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(10)), mailboxSession);
            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);

            ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMetaData(IGNORE, mailboxSession, NO_COUNT)
                .getHighestModSeq();
            clientConnection.write(ByteBuffer.wrap(String.format("I00104 UID FETCH 12:37 (FLAGS) (CHANGEDSINCE %d VANISHED)\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK FETCH completed.")))
                .filteredOn(s -> s.contains("* VANISHED (EARLIER) 14"))
                .hasSize(1);
        }

        @Test
        void fetchShouldSupportVanishedModifiedWithoutChangedSince() throws Exception {
            inbox.delete(ImmutableList.of(MessageUid.of(14)), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
            clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(10)), mailboxSession);
            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);

            ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMetaData(IGNORE, mailboxSession, NO_COUNT)
                .getHighestModSeq();
            clientConnection.write(ByteBuffer.wrap(String.format("I00104 UID FETCH 12:37 (FLAGS) (VANISHED)\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

            assertThat(new String(readBytes(clientConnection))).contains("I00104 BAD FETCH VANISHED used without CHANGEDSINCE");
        }

        @Test
        void fetchShouldRejectVanishedWhenNoQRESYNC() throws Exception {
            inbox.delete(ImmutableList.of(MessageUid.of(14)), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(10)), mailboxSession);
            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);

            ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMetaData(IGNORE, mailboxSession, NO_COUNT)
                .getHighestModSeq();
            clientConnection.write(ByteBuffer.wrap(String.format("I00104 UID FETCH 12:37 (FLAGS) (CHANGEDSINCE %d VANISHED)\r\n", highestModSeq.asLong()).getBytes(StandardCharsets.UTF_8)));

            assertThat(new String(readBytes(clientConnection))).contains("I00104 BAD FETCH QRESYNC not enabled.");
        }

        @Test
        void unsolicitedNotificationsShouldBeSent() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
            clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(10)), mailboxSession);
            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(ANSWERED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);

            inbox.delete(ImmutableList.of(MessageUid.of(14)), mailboxSession);

            ModSeq highestModSeq = memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .getMetaData(IGNORE, mailboxSession, NO_COUNT)
                .getHighestModSeq();
            clientConnection.write(ByteBuffer.wrap("I00104 NOOP\r\n".getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK NOOP completed.")))
                .filteredOn(s -> s.contains("* VANISHED 14"))
                .hasSize(1);
        }

        @Test
        void expungeShouldReturnVanishedWhenQResyncIsActive() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
            clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(10)), mailboxSession);
            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(11)), mailboxSession);
            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(12)), mailboxSession);
            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);
            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(26)), mailboxSession);
            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(31)), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(("I00104 EXPUNGE\r\n").getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [HIGHESTMODSEQ 44] EXPUNGE completed.")))
                .filteredOn(s -> s.contains("* VANISHED 10:12,25:26,31"))
                .hasSize(1);
        }

        @Test
        void uidExpungeShouldReturnExpungededWhenQResyncIsActive() throws Exception {
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
            clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(10)), mailboxSession);
            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(11)), mailboxSession);
            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(12)), mailboxSession);
            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(25)), mailboxSession);
            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(26)), mailboxSession);
            memoryIntegrationResources.getMailboxManager().getMailbox(MailboxPath.inbox(USER), mailboxSession)
                .setFlags(new Flags(Flags.Flag.DELETED), REPLACE, MessageRange.one(MessageUid.of(31)), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(("I00104 UID EXPUNGE 1:37\r\n").getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("I00104 OK [HIGHESTMODSEQ 44] EXPUNGE completed.")))
                .filteredOn(s -> s.contains("* VANISHED 10:12,25:26,31"))
                .hasSize(1);
        }

        @Test
        void implicitMailboxSelectionChangesShouldReturnClosedNotifications() throws Exception {
            memoryIntegrationResources.getMailboxManager()
                .createMailbox(MailboxPath.forUser(USER, "other"), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));
            clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

            clientConnection.write(ByteBuffer.wrap(("a3 SELECT other\r\n").getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("a3 OK [READ-WRITE] SELECT completed.")))
                .filteredOn(s -> s.contains("* OK [CLOSED]"))
                .hasSize(1);
        }

        @Test
        void closeShouldNotReturnHighestModseqWhenUsingQResync() throws Exception {
            // See https://www.rfc-editor.org/errata_search.php?rfc=5162
            memoryIntegrationResources.getMailboxManager()
                .createMailbox(MailboxPath.forUser(USER, "other"), mailboxSession);

            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);
            clientConnection.write(ByteBuffer.wrap(("a1 ENABLE QRESYNC\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a1 OK ENABLE completed."));

            clientConnection.write(ByteBuffer.wrap(("a2 SELECT INBOX\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("a2 OK [READ-WRITE] SELECT completed."));

            clientConnection.write(ByteBuffer.wrap(("a3 CLOSE\r\n").getBytes(StandardCharsets.UTF_8)));

            assertThat(readStringUntil(clientConnection, s -> s.contains("a3 OK CLOSE completed.")))
                .isNotNull();
        }
    }

    @Nested
    class SslConcurrentConnections {
        IMAPServer imapServer;
        int port;

        @BeforeEach
        void setup() throws Exception {
            HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("imapSSL.xml"));
            imapServer = createImapServer(config);
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            if (imapServer != null) {
                imapServer.destroy();
            }
        }

        @Test
        void shouldSupportManyConcurrentSSLConnections() throws Exception {
            //Failed for 3.7.0, this serves as a non regression test
            ConcurrentTestRunner.builder()
                .operation((a, b) -> {
                    IMAPSClient imapsClient = imapsImplicitClient(port);
                    boolean capability = imapsClient.capability();
                    assertThat(capability).isTrue();
                    imapsClient.close();
                })
                .threadCount(10)
                .operationCount(200)
                .runSuccessfullyWithin(Duration.ofMinutes(10));
        }

        private IMAPSClient imapsImplicitClient(int port) throws Exception {
            IMAPSClient client = new IMAPSClient(true, BogusSslContextFactory.getClientContext());
            client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
            client.connect("127.0.0.1", port);
            return client;
        }
    }
}
