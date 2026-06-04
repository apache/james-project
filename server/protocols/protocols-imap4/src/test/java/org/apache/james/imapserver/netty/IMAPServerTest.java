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
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.apache.james.mailbox.MessageManager.FlagsUpdateMode.REPLACE;
import static org.apache.james.mailbox.MessageManager.MailboxMetaData.FetchGroup.NO_COUNT;
import static org.apache.james.mailbox.MessageManager.MailboxMetaData.RecentMode.IGNORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import jakarta.mail.Flags;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.net.imap.AuthenticatingIMAPClient;
import org.apache.commons.net.imap.IMAPSClient;
import org.apache.james.core.Username;
import org.apache.james.imap.api.ConnectionCheck;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.fetch.FetchProcessor;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UidValidity;
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
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.stubbing.Answer;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.compression.JdkZlibDecoder;
import io.netty.handler.codec.compression.JdkZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.ssl.SslContextBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;

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
                .secure(Throwing.consumer(ssl -> ssl.sslContext(SslContextBuilder.forClient().trustManager(new BlindTrustManager()).build())))
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
        void idleShouldBeAllowedWhenAuthenticatedState() throws Exception {
            // Given an authenticated user
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            // When IDLE command is issued (Authenticated state)
            clientConnection.write(ByteBuffer.wrap(("a3 IDLE\r\n").getBytes(StandardCharsets.UTF_8)));

            // Then the server should respond Idling response
            Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(readStringUntil(clientConnection, s -> s.contains("+ Idling")))
                    .isNotNull());
        }

        @Test
        void idleShouldDoNothingResponseWhenAuthenticatedStateAndHasNewMessages() throws Exception {
            // Given an authenticated user
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            // When IDLE command is issued (Authenticated state)
            clientConnection.write(ByteBuffer.wrap(("a3 IDLE\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("+ Idling"));

            // And a new message is appended
            inbox.appendMessage(MessageManager.AppendCommand.builder().build("h: value\r\n\r\nbody".getBytes()), mailboxSession);

            ImmutableList.Builder<String> listenerResult = ImmutableList.builder();
            Mono.fromCallable(() -> new String(readBytes(clientConnection), StandardCharsets.US_ASCII))
                .doOnNext(listenerResult::add)
                .subscribeOn(Schedulers.boundedElastic()).subscribe();

            Thread.sleep(200);
            // Then the server should not send any response
            assertThat(listenerResult.build()).isEmpty();
        }

        @Test
        void idleShouldBeInterruptibleWhenAuthenticatedState() throws Exception {
            // Given an authenticated user
            clientConnection.write(ByteBuffer.wrap(String.format("a0 LOGIN %s %s\r\n", USER.asString(), USER_PASS).getBytes(StandardCharsets.UTF_8)));
            readBytes(clientConnection);

            // When IDLE command is issued (Authenticated state)
            clientConnection.write(ByteBuffer.wrap(("a3 IDLE\r\n").getBytes(StandardCharsets.UTF_8)));
            readStringUntil(clientConnection, s -> s.contains("+ Idling"));

            // And DONE command is issued
            clientConnection.write(ByteBuffer.wrap(("DONE\r\n").getBytes(StandardCharsets.UTF_8)));

            // Then the server should respond IDLE completed
            Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(readStringUntil(clientConnection, s -> s.contains("a3 OK IDLE completed.")))
                    .isNotNull());
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

    @Nested
    class PlainAuthenticateThenAnotherCommand {
        IMAPServer imapServer;
        int port;

        @BeforeEach
        void setup() throws Exception {
            imapServer = createImapServer("imapServer.xml");
            port = imapServer.getListenAddresses().get(0).getPort();
        }

        @AfterEach
        void tearDown() {
            if (imapServer != null) {
                imapServer.destroy();
            }
        }

        @Test
        void authenticateShouldOnlyConsumeAuthDataCommandNotTheNextCommand() throws Exception {
            ConcurrentTestRunner.builder()
                    .operation((threadNumber, step) -> {
                        AuthenticatingIMAPClient imapClient = new AuthenticatingIMAPClient();
                        imapClient.connect("127.0.0.1", port);
                        assertThat(imapClient.authenticate(AuthenticatingIMAPClient.AUTH_METHOD.PLAIN, USER.asString(),
                                USER_PASS)).isTrue();
                        assertThat(imapClient.logout()).isTrue();
                        imapClient.disconnect();
                    })
                    .threadCount(10)
                    .operationCount(200)
                    .runSuccessfullyWithin(Duration.ofMinutes(10));
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
