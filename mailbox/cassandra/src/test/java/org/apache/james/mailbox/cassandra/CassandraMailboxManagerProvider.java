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

package org.apache.james.mailbox.cassandra;

import java.time.Clock;
import java.time.Instant;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.AttachmentContentLoader;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.quota.CassandraCurrentQuotaManagerV1;
import org.apache.james.mailbox.cassandra.quota.CassandraGlobalMaxQuotaDao;
import org.apache.james.mailbox.cassandra.quota.CassandraPerDomainMaxQuotaDao;
import org.apache.james.mailbox.cassandra.quota.CassandraPerUserMaxQuotaDao;
import org.apache.james.mailbox.cassandra.quota.CassandraPerUserMaxQuotaManager;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.MailboxManagerConfiguration;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.MailboxAnnotationListener;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.NaiveThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.quota.StoreQuotaManager;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.utils.UpdatableTickingClock;

import com.datastax.oss.driver.api.core.CqlSession;

public class CassandraMailboxManagerProvider {
    private static final int LIMIT_ANNOTATIONS = 3;
    private static final int LIMIT_ANNOTATION_SIZE = 30;

    public static CassandraMailboxManager provideMailboxManager(CassandraCluster cassandra,
                                                                PreDeletionHooks preDeletionHooks) {
        return provideMailboxManager(cassandra, preDeletionHooks, CassandraConfiguration.DEFAULT_CONFIGURATION);
    }

    public static CassandraMailboxManager provideMailboxManager(CassandraCluster cassandra,
                                                                PreDeletionHooks preDeletionHooks,
                                                                CassandraConfiguration cassandraConfiguration) {
        return provideMailboxManager(cassandra, preDeletionHooks, cassandraConfiguration,
            MailboxManagerConfiguration.DEFAULT);
    }

    public static CassandraMailboxManager provideMailboxManager(CassandraCluster cassandra,
                                                                PreDeletionHooks preDeletionHooks,
                                                                CassandraConfiguration cassandraConfiguration,
                                                                MailboxManagerConfiguration mailboxManagerConfiguration) {
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();
        ThreadIdGuessingAlgorithm threadIdGuessingAlgorithm = new NaiveThreadIdGuessingAlgorithm();
        UpdatableTickingClock clock = new UpdatableTickingClock(Instant.now());

        CassandraMailboxSessionMapperFactory mapperFactory = TestCassandraMailboxSessionMapperFactory.forTests(
            cassandra,
            messageIdFactory,
            cassandraConfiguration);

        return provideMailboxManager(cassandra.getConf(), preDeletionHooks, mapperFactory,
            mailboxManagerConfiguration, messageIdFactory, threadIdGuessingAlgorithm, clock);
    }

    private static CassandraMailboxManager provideMailboxManager(CqlSession session,
                                                                 PreDeletionHooks preDeletionHooks,
                                                                 CassandraMailboxSessionMapperFactory mapperFactory,
                                                                 MailboxManagerConfiguration mailboxManagerConfiguration,
                                                                 MessageId.Factory messageIdFactory,
                                                                 ThreadIdGuessingAlgorithm threadIdGuessingAlgorithm,
                                                                 Clock clock) {
        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        MessageParser messageParser = new MessageParser();
        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        StoreRightManager storeRightManager = new StoreRightManager(mapperFactory, aclResolver, eventBus);

        Authenticator noAuthenticator = null;
        Authorizator noAuthorizator = null;
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mapperFactory, storeRightManager,
            LIMIT_ANNOTATIONS, LIMIT_ANNOTATION_SIZE);

        SessionProviderImpl sessionProvider = new SessionProviderImpl(noAuthenticator, noAuthorizator);
        CassandraPerUserMaxQuotaManager maxQuotaManager = new CassandraPerUserMaxQuotaManager(new CassandraPerUserMaxQuotaDao(session),
            new CassandraPerDomainMaxQuotaDao(session),
            new CassandraGlobalMaxQuotaDao(session));
        CassandraCurrentQuotaManagerV1 currentQuotaUpdater = new CassandraCurrentQuotaManagerV1(session);
        StoreQuotaManager storeQuotaManager = new StoreQuotaManager(currentQuotaUpdater, maxQuotaManager);
        QuotaRootResolver quotaRootResolver = new DefaultUserQuotaRootResolver(sessionProvider, mapperFactory);
        ListeningCurrentQuotaUpdater quotaUpdater = new ListeningCurrentQuotaUpdater(currentQuotaUpdater, quotaRootResolver, eventBus, storeQuotaManager);
        QuotaComponents quotaComponents = new QuotaComponents(maxQuotaManager, storeQuotaManager, quotaRootResolver);

        AttachmentContentLoader attachmentContentLoader = null;
        MessageSearchIndex index = new SimpleMessageSearchIndex(mapperFactory, mapperFactory, new DefaultTextExtractor(), attachmentContentLoader);

        CassandraMailboxManager manager = new CassandraMailboxManager(mapperFactory, sessionProvider, new NoMailboxPathLocker(),
            messageParser, messageIdFactory, eventBus, annotationManager, storeRightManager,
            quotaComponents, index, mailboxManagerConfiguration, preDeletionHooks, threadIdGuessingAlgorithm, clock);

        eventBus.register(quotaUpdater);
        eventBus.register(new MailboxAnnotationListener(mapperFactory, sessionProvider));
        eventBus.register(mapperFactory.deleteMessageListener());

        return manager;
    }

}
