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

import java.time.Clock;
import java.time.Instant;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.quota.PostgresQuotaCurrentValueDAO;
import org.apache.james.backends.postgres.quota.PostgresQuotaLimitDAO;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
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
import org.apache.james.mailbox.postgres.PostgresMailboxSessionMapperFactory;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.PostgresMailboxManager;
import org.apache.james.mailbox.postgres.quota.PostgresCurrentQuotaManager;
import org.apache.james.mailbox.postgres.quota.PostgresPerUserMaxQuotaManager;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.event.MailboxAnnotationListener;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.NaiveThreadIdGuessingAlgorithm;
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
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.apache.james.utils.UpdatableTickingClock;

import com.google.common.base.Preconditions;

public class PostgresHostSystem extends JamesImapHostSystem {

    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT,
        Feature.USER_FLAGS_SUPPORT,
        Feature.ANNOTATION_SUPPORT,
        Feature.QUOTA_SUPPORT,
        Feature.MOVE_SUPPORT,
        Feature.MOD_SEQ_SEARCH);


    static PostgresHostSystem build(PostgresExtension postgresExtension) {
        return new PostgresHostSystem(postgresExtension);
    }

    private PostgresPerUserMaxQuotaManager maxQuotaManager;
    private PostgresMailboxManager mailboxManager;
    private final PostgresExtension postgresExtension;

    public PostgresHostSystem(PostgresExtension postgresExtension) {
        this.postgresExtension = postgresExtension;
    }

    public void beforeAll() {
        Preconditions.checkNotNull(postgresExtension.getConnectionFactory());
    }

    @Override
    public void beforeTest() throws Exception {
        super.beforeTest();

        BlobId.Factory blobIdFactory = new HashBlobId.Factory();
        DeDuplicationBlobStore blobStore = new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.DEFAULT, blobIdFactory);

        PostgresMailboxSessionMapperFactory mapperFactory = new PostgresMailboxSessionMapperFactory(postgresExtension.getExecutorFactory(), Clock.systemUTC(), blobStore, blobIdFactory);

        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        MessageParser messageParser = new MessageParser();


        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        StoreRightManager storeRightManager = new StoreRightManager(mapperFactory, aclResolver, eventBus);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mapperFactory, storeRightManager);
        SessionProviderImpl sessionProvider = new SessionProviderImpl(authenticator, authorizator);
        DefaultUserQuotaRootResolver quotaRootResolver = new DefaultUserQuotaRootResolver(sessionProvider, mapperFactory);
        CurrentQuotaManager currentQuotaManager = new PostgresCurrentQuotaManager(new PostgresQuotaCurrentValueDAO(postgresExtension.getPostgresExecutor()));
        maxQuotaManager = new PostgresPerUserMaxQuotaManager(new PostgresQuotaLimitDAO(postgresExtension.getPostgresExecutor()));
        StoreQuotaManager storeQuotaManager = new StoreQuotaManager(currentQuotaManager, maxQuotaManager);
        ListeningCurrentQuotaUpdater quotaUpdater = new ListeningCurrentQuotaUpdater(currentQuotaManager, quotaRootResolver, eventBus, storeQuotaManager);
        QuotaComponents quotaComponents = new QuotaComponents(maxQuotaManager, storeQuotaManager, quotaRootResolver);
        AttachmentContentLoader attachmentContentLoader = null;
        MessageSearchIndex index = new SimpleMessageSearchIndex(mapperFactory, mapperFactory, new DefaultTextExtractor(), attachmentContentLoader);

        mailboxManager = new PostgresMailboxManager(mapperFactory, sessionProvider, messageParser,
            new PostgresMessageId.Factory(),
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
