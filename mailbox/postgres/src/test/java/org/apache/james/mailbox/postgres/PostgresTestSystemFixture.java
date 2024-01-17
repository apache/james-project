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

package org.apache.james.mailbox.postgres;

import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.quota.PostgresQuotaCurrentValueDAO;
import org.apache.james.backends.postgres.quota.PostgresQuotaLimitDAO;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.AttachmentContentLoader;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.postgres.quota.PostgresCurrentQuotaManager;
import org.apache.james.mailbox.postgres.quota.PostgresPerUserMaxQuotaManager;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.MailboxAnnotationListener;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.NaiveThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.apache.james.utils.UpdatableTickingClock;

public class PostgresTestSystemFixture {
    public static PostgresMailboxSessionMapperFactory createMapperFactory(PostgresExtension postgresExtension) {
        BlobId.Factory blobIdFactory = new HashBlobId.Factory();
        DeDuplicationBlobStore blobStore = new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.DEFAULT, blobIdFactory);

        return new PostgresMailboxSessionMapperFactory(postgresExtension.getExecutorFactory(), Clock.systemUTC(), blobStore, blobIdFactory);
    }

    public static PostgresMailboxManager createMailboxManager(PostgresMailboxSessionMapperFactory mapperFactory) {
        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        StoreRightManager storeRightManager = new StoreRightManager(mapperFactory, new UnionMailboxACLResolver(), eventBus);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mapperFactory, storeRightManager);

        SessionProviderImpl sessionProvider = new SessionProviderImpl(mock(Authenticator.class), mock(Authorizator.class));

        QuotaComponents quotaComponents = QuotaComponents.disabled(sessionProvider, mapperFactory);
        AttachmentContentLoader attachmentContentLoader = null;
        MessageSearchIndex index = new SimpleMessageSearchIndex(mapperFactory, mapperFactory, new DefaultTextExtractor(), attachmentContentLoader);
        PostgresMailboxManager postgresMailboxManager = new PostgresMailboxManager(mapperFactory, sessionProvider,
            new MessageParser(), new PostgresMessageId.Factory(),
            eventBus, annotationManager, storeRightManager, quotaComponents, index, new NaiveThreadIdGuessingAlgorithm(), PreDeletionHooks.NO_PRE_DELETION_HOOK,
            new UpdatableTickingClock(Instant.now()));

        eventBus.register(new MailboxAnnotationListener(mapperFactory, sessionProvider));
        eventBus.register(mapperFactory.deleteMessageListener());

        return postgresMailboxManager;
    }

    static StoreMessageIdManager createMessageIdManager(PostgresMailboxSessionMapperFactory mapperFactory, QuotaManager quotaManager, EventBus eventBus,
                                                        PreDeletionHooks preDeletionHooks) {
        PostgresMailboxManager mailboxManager = createMailboxManager(mapperFactory);
        return new StoreMessageIdManager(
            mailboxManager,
            mapperFactory,
            eventBus,
            quotaManager,
            new DefaultUserQuotaRootResolver(mailboxManager.getSessionProvider(), mapperFactory),
            preDeletionHooks);
    }

    static MaxQuotaManager createMaxQuotaManager(PostgresExtension postgresExtension) {
        return new PostgresPerUserMaxQuotaManager(new PostgresQuotaLimitDAO(postgresExtension.getPostgresExecutor()));
    }

    public static CurrentQuotaManager createCurrentQuotaManager(PostgresExtension postgresExtension) {
        return new PostgresCurrentQuotaManager(new PostgresQuotaCurrentValueDAO(postgresExtension.getPostgresExecutor()));
    }
}
