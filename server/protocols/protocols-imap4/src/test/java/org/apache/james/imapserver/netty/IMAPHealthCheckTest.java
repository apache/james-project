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

import java.util.List;
import java.util.stream.IntStream;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.imap.api.DefaultConnectionCheckFactory;
import org.apache.james.imap.api.ImapMessage;
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
import org.apache.james.protocols.lib.mock.ConfigLoader;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

public class IMAPHealthCheckTest {
    static class FakeImapMessage implements ImapMessage {
    }

    private IMAPHealthCheck imapHealthCheck;
    private IMAPServerFactory imapServerFactory;
    private List<ReactiveThrottler> reactiveThrottlers;

    @BeforeEach
    void setUp() throws Exception {
        FakeAuthenticator authenticator = new FakeAuthenticator();

        InMemoryIntegrationResources memoryIntegrationResources = InMemoryIntegrationResources.builder()
            .authenticator(authenticator)
            .authorizator(FakeAuthorizator.defaultReject())
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();
        InMemoryMailboxManager mailboxManager = memoryIntegrationResources.getMailboxManager();

        RecordingMetricFactory metricFactory = new RecordingMetricFactory();

        imapServerFactory = new IMAPServerFactory(
            FileSystemImpl.forTestingWithConfigurationFromClasspath(),
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
                FetchProcessor.LocalCacheConfiguration.DEFAULT),
            new RecordingMetricFactory(),
            new NoopGaugeRegistry(),
            new DefaultConnectionCheckFactory());

        HierarchicalConfiguration<ImmutableNode> config = ConfigLoader.getConfig(ClassLoaderUtils.getSystemResourceAsSharedStream("imapServerHealthCheck.xml"));
        imapServerFactory.configure(config);
        imapServerFactory.init();
        reactiveThrottlers = imapServerFactory.getImapServers().stream().map(IMAPServer::getReactiveThrottler).toList();
        imapHealthCheck = new IMAPHealthCheck(imapServerFactory);
    }

    @AfterEach
    void afterEach() {
        imapServerFactory.destroy();
    }

    @Test
    void checkShouldReturnHealthyWhenAllQueueAreNotFull() {
        Result check = imapHealthCheck.check().block();

        assertThat(check.isHealthy()).isTrue();
    }

    @Test
    void checkShouldReturnDegradedWhenOneQueueIsFull() {
        IntStream.range(0, 11).forEach(i -> reactiveThrottlers.get(0).throttle(Mono.empty(), new FakeImapMessage()));
        Result check = imapHealthCheck.check().block();

        assertThat(check.isDegraded()).isTrue();
    }
}
