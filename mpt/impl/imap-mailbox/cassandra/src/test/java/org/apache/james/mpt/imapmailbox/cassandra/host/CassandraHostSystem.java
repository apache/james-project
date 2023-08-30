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
package org.apache.james.mpt.imapmailbox.cassandra.host;

import java.time.Instant;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.components.CassandraQuotaCurrentValueDao;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.TestCassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.quota.CassandraCurrentQuotaManagerV2;
import org.apache.james.mailbox.cassandra.quota.CassandraGlobalMaxQuotaDao;
import org.apache.james.mailbox.cassandra.quota.CassandraPerDomainMaxQuotaDao;
import org.apache.james.mailbox.cassandra.quota.CassandraPerUserMaxQuotaDao;
import org.apache.james.mailbox.cassandra.quota.CassandraPerUserMaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.MailboxManagerConfiguration;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreAttachmentManager;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
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
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.james.utils.UpdatableTickingClock;

import com.datastax.oss.driver.api.core.CqlSession;

public class CassandraHostSystem extends JamesImapHostSystem {

    private static final ImapFeatures IMAP_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT,
        Feature.MOVE_SUPPORT,
        Feature.USER_FLAGS_SUPPORT,
        Feature.QUOTA_SUPPORT,
        Feature.ANNOTATION_SUPPORT,
        Feature.MOD_SEQ_SEARCH);

    private final CassandraCluster cassandra;
    private CassandraMailboxManager mailboxManager;
    private CassandraPerUserMaxQuotaManager perUserMaxQuotaManager;
    
    public CassandraHostSystem(CassandraCluster cluster) {
        this.cassandra = cluster;
    }

    public CassandraCluster getCassandra() {
        return cassandra;
    }

    @Override
    public void beforeTest() throws Exception {
        super.beforeTest();
        CqlSession session = cassandra.getConf();
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();
        ThreadIdGuessingAlgorithm threadIdGuessingAlgorithm = new NaiveThreadIdGuessingAlgorithm();
        UpdatableTickingClock clock = new UpdatableTickingClock(Instant.now());
        CassandraMailboxSessionMapperFactory mapperFactory = TestCassandraMailboxSessionMapperFactory.forTests(
            cassandra, messageIdFactory);


        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        StoreRightManager storeRightManager = new StoreRightManager(mapperFactory, new UnionMailboxACLResolver(), eventBus);

        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mapperFactory, storeRightManager);
        SessionProviderImpl sessionProvider = new SessionProviderImpl(authenticator, authorizator);
        QuotaRootResolver quotaRootResolver = new DefaultUserQuotaRootResolver(sessionProvider, mapperFactory);

        perUserMaxQuotaManager = new CassandraPerUserMaxQuotaManager(
            new CassandraPerUserMaxQuotaDao(session),
            new CassandraPerDomainMaxQuotaDao(cassandra.getConf()),
            new CassandraGlobalMaxQuotaDao(session));
        CassandraCurrentQuotaManagerV2 currentQuotaManager = new CassandraCurrentQuotaManagerV2(new CassandraQuotaCurrentValueDao(session));
        StoreQuotaManager quotaManager = new StoreQuotaManager(currentQuotaManager, perUserMaxQuotaManager);
        ListeningCurrentQuotaUpdater quotaUpdater = new ListeningCurrentQuotaUpdater(currentQuotaManager, quotaRootResolver, eventBus, quotaManager);
        QuotaComponents quotaComponents = new QuotaComponents(perUserMaxQuotaManager, quotaManager, quotaRootResolver);

        StoreMessageIdManager messageIdManager = new StoreMessageIdManager(storeRightManager, mapperFactory, eventBus, quotaManager, quotaRootResolver, PreDeletionHooks.NO_PRE_DELETION_HOOK);
        StoreAttachmentManager attachmentManager = new StoreAttachmentManager(mapperFactory, messageIdManager);

        MessageSearchIndex index = new SimpleMessageSearchIndex(mapperFactory, mapperFactory, new DefaultTextExtractor(), attachmentManager);

        mailboxManager = new CassandraMailboxManager(mapperFactory, sessionProvider,
            new JVMMailboxPathLocker(), new MessageParser(), messageIdFactory,
            eventBus, annotationManager, storeRightManager, quotaComponents, index, MailboxManagerConfiguration.DEFAULT,
            PreDeletionHooks.NO_PRE_DELETION_HOOK, threadIdGuessingAlgorithm, clock);

        eventBus.register(quotaUpdater);

        SubscriptionManager subscriptionManager = new StoreSubscriptionManager(mapperFactory, mapperFactory, eventBus);

        configure(new DefaultImapDecoderFactory().buildImapDecoder(),
                new DefaultImapEncoderFactory().buildImapEncoder(),
                DefaultImapProcessorFactory.createDefaultProcessor(mailboxManager, eventBus, subscriptionManager, quotaManager, quotaRootResolver, new DefaultMetricFactory()));
    }

    @Override
    public boolean supports(Feature... features) {
        return IMAP_FEATURES.supports(features);
    }

    @Override
    public void setQuotaLimits(QuotaCountLimit maxMessageQuota, QuotaSizeLimit maxStorageQuota) {
        perUserMaxQuotaManager.setGlobalMaxMessage(maxMessageQuota);
        perUserMaxQuotaManager.setGlobalMaxStorage(maxStorageQuota);
    }

    @Override
    public MailboxManager getMailboxManager() {
        return mailboxManager;
    }

    @Override
    protected void await() {

    }
}
