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

package org.apache.james.messagefastview.cleanup;

import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.cassandra.CassandraDomainListModule;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.jmap.cassandra.projections.CassandraMessageFastViewProjection;
import org.apache.james.jmap.cassandra.projections.CassandraMessageFastViewProjectionModule;
import org.apache.james.mailbox.AttachmentContentLoader;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.CassandraTestSystemFixture;
import org.apache.james.mailbox.cassandra.TestCassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasService;
import org.apache.james.mailbox.quota.task.RecomputeMailboxCurrentQuotasService;
import org.apache.james.mailbox.store.MailboxManagerConfiguration;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.MailboxAnnotationListener;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.NaiveThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.impl.MessageParserImpl;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.cassandra.CassandraUsersDAO;
import org.apache.james.user.cassandra.CassandraUsersRepositoryModule;
import org.apache.james.user.lib.UsersRepositoryImpl;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableSet;

public class CassandraMessageFastViewCleanupServiceTest implements MessageFastViewCleanupServiceContract {
    static final DomainList NO_DOMAIN_LIST = null;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(
        MailboxAggregateModule.MODULE,
        CassandraMessageFastViewProjectionModule.MODULE,
        CassandraDomainListModule.MODULE,
        CassandraUsersRepositoryModule.MODULE));

    UsersRepositoryImpl usersRepository;
    StoreMailboxManager mailboxManager;
    SessionProvider sessionProvider;
    MessageFastViewProjection messageFastViewProjection;
    MessageId.Factory messageIdFactory;
    MessageFastViewCleanupService testee;

    @BeforeEach
    void setUp() {
        CassandraCluster cassandra = cassandraCluster.getCassandraCluster();
        CassandraMailboxSessionMapperFactory mapperFactory = createMapperFactory(cassandra);

        CassandraUsersDAO usersDAO = new CassandraUsersDAO(cassandra.getConf());
        usersRepository = new UsersRepositoryImpl(NO_DOMAIN_LIST, usersDAO);
        usersRepository.setEnableVirtualHosting(false);

        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        mailboxManager = createMailboxManager(mapperFactory, eventBus);
        sessionProvider  = mailboxManager.getSessionProvider();
        messageFastViewProjection = new CassandraMessageFastViewProjection(new RecordingMetricFactory(), cassandraCluster.getCassandraCluster().getConf());
        messageIdFactory = mailboxManager.getMessageIdFactory();

        MessageIdManager messageIdManager = new StoreMessageIdManager(
            mailboxManager,
            mapperFactory,
            eventBus,
            new NoQuotaManager(),
            new DefaultUserQuotaRootResolver(mailboxManager.getSessionProvider(), mapperFactory),
            PreDeletionHooks.NO_PRE_DELETION_HOOK);
        testee = new MessageFastViewCleanupService(messageFastViewProjection, messageIdManager, sessionProvider);
    }

    @Override
    public UsersRepository usersRepository() {
        return usersRepository;
    }

    @Override
    public MailboxManager mailboxManager() {
        return mailboxManager;
    }

    @Override
    public SessionProvider sessionProvider() {
        return sessionProvider;
    }

    @Override
    public MessageFastViewProjection messageFastViewProjection() {
        return messageFastViewProjection;
    }

    @Override
    public MessageId.Factory messageIdFactory() {
        return messageIdFactory;
    }

    @Override
    public MessageFastViewCleanupService testee() {
        return testee;
    }

    private CassandraMailboxSessionMapperFactory createMapperFactory(CassandraCluster cassandra) {
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();

        return TestCassandraMailboxSessionMapperFactory.forTests(cassandra, messageIdFactory);
    }

    private CassandraMailboxManager createMailboxManager(CassandraMailboxSessionMapperFactory mapperFactory, EventBus eventBus) {
        StoreRightManager storeRightManager = new StoreRightManager(mapperFactory, new UnionMailboxACLResolver(), eventBus);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mapperFactory, storeRightManager);

        SessionProviderImpl sessionProvider = new SessionProviderImpl(mock(Authenticator.class), mock(Authorizator.class));

        QuotaComponents quotaComponents = QuotaComponents.disabled(sessionProvider, mapperFactory);
        AttachmentContentLoader attachmentContentLoader = null;
        MessageSearchIndex index = new SimpleMessageSearchIndex(mapperFactory, mapperFactory, new DefaultTextExtractor(), attachmentContentLoader);
        CassandraMailboxManager cassandraMailboxManager = new CassandraMailboxManager(mapperFactory, sessionProvider,
            new NoMailboxPathLocker(), new MessageParserImpl(), new CassandraMessageId.Factory(),
            eventBus, annotationManager, storeRightManager, quotaComponents, index, MailboxManagerConfiguration.DEFAULT, PreDeletionHooks.NO_PRE_DELETION_HOOK,
            new NaiveThreadIdGuessingAlgorithm(), new UpdatableTickingClock(Instant.now()));

        eventBus.register(new MailboxAnnotationListener(mapperFactory, sessionProvider));
        eventBus.register(mapperFactory.deleteMessageListener());

        return cassandraMailboxManager;
    }
}
