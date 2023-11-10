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

package org.apache.james.mpt.imapmailbox.postgres.host;

import java.time.Instant;

import javax.persistence.EntityManagerFactory;

import org.apache.james.backends.jpa.JPAConfiguration;
import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.utils.JamesPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.SimpleJamesPostgresConnectionFactory;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.AttachmentContentLoader;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.postgres.JPAMailboxFixture;
import org.apache.james.mailbox.postgres.PostgresMailboxSessionMapperFactory;
import org.apache.james.mailbox.postgres.mail.JPAModSeqProvider;
import org.apache.james.mailbox.postgres.mail.JPAUidProvider;
import org.apache.james.mailbox.postgres.openjpa.OpenJPAMailboxManager;
import org.apache.james.mailbox.postgres.quota.JPAPerUserMaxQuotaDAO;
import org.apache.james.mailbox.postgres.quota.JPAPerUserMaxQuotaManager;
import org.apache.james.mailbox.postgres.quota.JpaCurrentQuotaManager;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.event.MailboxAnnotationListener;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.NaiveThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class PostgresHostSystem extends JamesImapHostSystem {

    private static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(
        ImmutableList.<Class<?>>builder()
            .addAll(JPAMailboxFixture.MAILBOX_PERSISTANCE_CLASSES)
            .addAll(JPAMailboxFixture.QUOTA_PERSISTANCE_CLASSES)
            .build());

    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT,
        Feature.USER_FLAGS_SUPPORT,
        Feature.ANNOTATION_SUPPORT,
        Feature.QUOTA_SUPPORT,
        Feature.MOVE_SUPPORT,
        Feature.MOD_SEQ_SEARCH);


    static PostgresHostSystem build(PostgresExtension postgresExtension) {
        return new PostgresHostSystem(postgresExtension);
    }

    private JPAPerUserMaxQuotaManager maxQuotaManager;
    private OpenJPAMailboxManager mailboxManager;
    private final PostgresExtension postgresExtension;
    private static JamesPostgresConnectionFactory postgresConnectionFactory;
    public PostgresHostSystem(PostgresExtension postgresExtension) {
        this.postgresExtension = postgresExtension;
    }

    public void beforeAll() {
        Preconditions.checkNotNull(postgresExtension.getConnectionFactory());
        postgresConnectionFactory = new SimpleJamesPostgresConnectionFactory(postgresExtension.getConnectionFactory());
    }

    @Override
    public void beforeTest() throws Exception {
        super.beforeTest();
        EntityManagerFactory entityManagerFactory = JPA_TEST_CLUSTER.getEntityManagerFactory();
        JPAUidProvider uidProvider = new JPAUidProvider(entityManagerFactory);
        JPAModSeqProvider modSeqProvider = new JPAModSeqProvider(entityManagerFactory);
        JPAConfiguration jpaConfiguration = JPAConfiguration.builder()
            .driverName("driverName")
            .driverURL("driverUrl")
            .build();
        PostgresMailboxSessionMapperFactory mapperFactory = new PostgresMailboxSessionMapperFactory(entityManagerFactory, uidProvider, modSeqProvider, jpaConfiguration, postgresConnectionFactory);

        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        MessageParser messageParser = new MessageParser();


        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        StoreRightManager storeRightManager = new StoreRightManager(mapperFactory, aclResolver, eventBus);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mapperFactory, storeRightManager);
        SessionProviderImpl sessionProvider = new SessionProviderImpl(authenticator, authorizator);
        DefaultUserQuotaRootResolver quotaRootResolver = new DefaultUserQuotaRootResolver(sessionProvider, mapperFactory);
        JpaCurrentQuotaManager currentQuotaManager = new JpaCurrentQuotaManager(entityManagerFactory);
        maxQuotaManager = new JPAPerUserMaxQuotaManager(entityManagerFactory, new JPAPerUserMaxQuotaDAO(entityManagerFactory));
        StoreQuotaManager storeQuotaManager = new StoreQuotaManager(currentQuotaManager, maxQuotaManager);
        ListeningCurrentQuotaUpdater quotaUpdater = new ListeningCurrentQuotaUpdater(currentQuotaManager, quotaRootResolver, eventBus, storeQuotaManager);
        QuotaComponents quotaComponents = new QuotaComponents(maxQuotaManager, storeQuotaManager, quotaRootResolver);
        AttachmentContentLoader attachmentContentLoader = null;
        MessageSearchIndex index = new SimpleMessageSearchIndex(mapperFactory, mapperFactory, new DefaultTextExtractor(), attachmentContentLoader);

        mailboxManager = new OpenJPAMailboxManager(mapperFactory, sessionProvider, messageParser, new DefaultMessageId.Factory(),
            eventBus, annotationManager, storeRightManager, quotaComponents, index, new NaiveThreadIdGuessingAlgorithm(), new UpdatableTickingClock(Instant.now()));

        eventBus.register(quotaUpdater);
        eventBus.register(new MailboxAnnotationListener(mapperFactory, sessionProvider));

        SubscriptionManager subscriptionManager = new StoreSubscriptionManager(mapperFactory, mapperFactory, eventBus);

        ImapProcessor defaultImapProcessorFactory =
            DefaultImapProcessorFactory.createDefaultProcessor(
                mailboxManager,
                eventBus,
                subscriptionManager,
                storeQuotaManager,
                quotaRootResolver,
                new DefaultMetricFactory());

        configure(new DefaultImapDecoderFactory().buildImapDecoder(),
            new DefaultImapEncoderFactory().buildImapEncoder(),
            defaultImapProcessorFactory);
    }

    @Override
    public void afterTest() {
        JPA_TEST_CLUSTER.clear(ImmutableList.<String>builder()
            .addAll(JPAMailboxFixture.MAILBOX_TABLE_NAMES)
            .addAll(JPAMailboxFixture.QUOTA_TABLES_NAMES)
            .build());
    }

    @Override
    protected MailboxManager getMailboxManager() {
        return mailboxManager;
    }

    @Override
    public boolean supports(Feature... features) {
        return SUPPORTED_FEATURES.supports(features);
    }

    @Override
    public void setQuotaLimits(QuotaCountLimit maxMessageQuota, QuotaSizeLimit maxStorageQuota) {
        maxQuotaManager.setGlobalMaxMessage(maxMessageQuota);
        maxQuotaManager.setGlobalMaxStorage(maxStorageQuota);
    }

    @Override
    protected void await() {

    }
}
