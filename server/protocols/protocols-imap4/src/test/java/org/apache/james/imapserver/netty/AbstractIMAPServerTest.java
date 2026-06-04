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

import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
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
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
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
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@SuppressWarnings("checkstyle:membername")
abstract class AbstractIMAPServerTest {
    protected static final String _129K_MESSAGE = "header: value\r\n" + "012345678\r\n".repeat(13107);
    protected static final String _65K_MESSAGE = "header: value\r\n" + "012345678\r\n".repeat(6553);
    protected static final Username USER = Username.of("user@domain.org");
    protected static final Username USER2 = Username.of("bobo@domain.org");
    protected static final Username USER3 = Username.of("user3@domain.org");
    protected static final String USER_PASS = "pass";
    protected static final String SMALL_MESSAGE = "header: value\r\n\r\nBODY";

    protected static class BlindTrustManager implements X509TrustManager {
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {

        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {

        }
    }

    protected InMemoryIntegrationResources memoryIntegrationResources;
    protected FakeAuthenticator authenticator;

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    protected InMemoryMailboxManager mailboxManager;

    protected IMAPServer createImapServer(HierarchicalConfiguration<ImmutableNode> config,
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

    protected IMAPServer createImapServer(HierarchicalConfiguration<ImmutableNode> config, FetchProcessor.LocalCacheConfiguration localCacheConfiguration) throws Exception {
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

    protected IMAPServer createImapServer(HierarchicalConfiguration<ImmutableNode> config) throws Exception {
        return createImapServer(config, FetchProcessor.LocalCacheConfiguration.DEFAULT);
    }

    protected IMAPServer createImapServer(String configurationFile, FetchProcessor.LocalCacheConfiguration localCacheConfiguration) throws Exception {
        return createImapServer(ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream(configurationFile)), localCacheConfiguration);
    }

    protected IMAPServer createImapServer(String configurationFile) throws Exception {
        return createImapServer(configurationFile, FetchProcessor.LocalCacheConfiguration.DEFAULT);
    }

    protected Set<ConnectionCheck> defaultConnectionChecks() {
        return ImmutableSet.of(new IpConnectionCheck());
    }

    protected static ListAppender<ILoggingEvent> getListAppenderForClass(Class<?> clazz) {
        Logger logger = (Logger) LoggerFactory.getLogger(clazz);

        ListAppender<ILoggingEvent> loggingEventListAppender = new ListAppender<>();
        loggingEventListAppender.start();

        logger.addAppender(loggingEventListAppender);
        return loggingEventListAppender;
    }

    protected AuthenticatingIMAPClient imapsClient(int port) throws Exception {
        AuthenticatingIMAPClient client = new AuthenticatingIMAPClient(false, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        client.connect("127.0.0.1", port);
        client.execTLS();
        return client;
    }

    protected byte[] readBytes(SocketChannel channel) throws IOException {
        ByteBuffer line = ByteBuffer.allocate(1024);
        channel.read(line);
        line.rewind();
        byte[] bline = new byte[line.remaining()];
        line.get(bline);
        return bline;
    }

    protected List<String> readStringUntil(SocketChannel channel, Predicate<String> condition) throws IOException {
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
