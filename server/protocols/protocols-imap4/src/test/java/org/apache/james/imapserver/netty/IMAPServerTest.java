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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.EOFException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;
import org.apache.commons.configuration2.io.FileHandler;
import org.apache.commons.net.imap.IMAPReply;
import org.apache.commons.net.imap.IMAPSClient;
import org.apache.james.core.Username;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.base.AbstractChainedProcessor;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;

class IMAPServerTest {
    public static ListAppender<ILoggingEvent> getListAppenderForClass(Class clazz) {
        Logger logger = (Logger) LoggerFactory.getLogger(clazz);

        ListAppender<ILoggingEvent> loggingEventListAppender = new ListAppender<>();
        loggingEventListAppender.start();

        logger.addAppender(loggingEventListAppender);
        return loggingEventListAppender;
    }

    private static final String _129K_MESSAGE = "header: value\r\n" + "012345678\r\n".repeat(13107);
    private static final String _65K_MESSAGE = "header: value\r\n" + "012345678\r\n".repeat(6553);
    private static final Username USER = Username.of("user@domain.org");
    private static final String USER_PASS = "pass";
    public static final String SMALL_MESSAGE = "header: value\r\n\r\nBODY";

    private static XMLConfiguration getConfig(InputStream configStream) throws Exception {
        FileBasedConfigurationBuilder<XMLConfiguration> builder = new FileBasedConfigurationBuilder<>(XMLConfiguration.class)
            .configure(new Parameters()
                .xml()
                .setListDelimiterHandler(new DisabledListDelimiterHandler()));
        XMLConfiguration xmlConfiguration = builder.getConfiguration();
        FileHandler fileHandler = new FileHandler(xmlConfiguration);
        fileHandler.load(configStream);
        configStream.close();

        return xmlConfiguration;
    }

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();

    private IMAPServer createImapServer(String configurationFile) throws Exception {
        FakeAuthenticator authenticator = new FakeAuthenticator();
        authenticator.addUser(USER, USER_PASS);

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .authenticator(authenticator)
            .authorizator(FakeAuthorizator.defaultReject())
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();
        RecordingMetricFactory metricFactory = new RecordingMetricFactory();
        IMAPServer imapServer = new IMAPServer(
            DefaultImapDecoderFactory.createDecoder(),
            new DefaultImapEncoderFactory().buildImapEncoder(),
            DefaultImapProcessorFactory.createXListSupportingProcessor(
                resources.getMailboxManager(),
                resources.getEventBus(),
                new StoreSubscriptionManager(resources.getMailboxManager().getMapperFactory()),
                null,
                resources.getQuotaManager(),
                resources.getQuotaRootResolver(),
                metricFactory),
            new ImapMetrics(metricFactory));
        Configuration configuration = Configuration.builder()
            .workingDirectory("../")
            .configurationFromClasspath()
            .build();
        FileSystemImpl fileSystem = new FileSystemImpl(configuration.directories());
        imapServer.setFileSystem(fileSystem);
        imapServer.configure(getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream(configurationFile)));
        imapServer.init();

        return imapServer;
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
                .doOnNext(s -> System.out.println("A: " + s))
                .doOnNext(responses::addLast)
                .subscribeOn(Schedulers.elastic())
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
            ListAppender<ILoggingEvent> listAppender = getListAppenderForClass(AbstractChainedProcessor.class);

            send("a0 STARTTLS\r\n");
            send("a1 NOOP\r\n");

            Thread.sleep(50);

            assertThat(listAppender.list)
                .filteredOn(event -> event.getFormattedMessage().contains("Processing org.apache.james.imap.message.request.NoopRequest"))
                .isEmpty();
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
    }
}
