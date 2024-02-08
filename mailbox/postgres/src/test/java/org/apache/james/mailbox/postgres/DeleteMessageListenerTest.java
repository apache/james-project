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

import static org.apache.james.mailbox.postgres.PostgresMailboxManagerProvider.BLOB_ID_FACTORY;

import java.time.Clock;
import java.time.Instant;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.postgres.mail.dao.PostgresAttachmentDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresThreadDAO;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.blob.deduplication.PassThroughBlobStore;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

public class DeleteMessageListenerTest extends DeleteMessageListenerContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresMailboxAggregateModule.MODULE);

    private static PostgresMailboxManager mailboxManager;
    private static BlobStore blobStore;

    @BeforeAll
    static void beforeAll() {
        blobStore = new PassThroughBlobStore(new MemoryBlobStoreDAO(), BucketName.DEFAULT, BLOB_ID_FACTORY);

        PostgresMailboxSessionMapperFactory mapperFactory = new PostgresMailboxSessionMapperFactory(
            postgresExtension.getExecutorFactory(),
            Clock.systemUTC(),
            blobStore,
            BLOB_ID_FACTORY);

        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        MessageParser messageParser = new MessageParser();

        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        StoreRightManager storeRightManager = new StoreRightManager(mapperFactory, aclResolver, eventBus);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mapperFactory, storeRightManager, 3, 30);
        SessionProviderImpl sessionProvider = new SessionProviderImpl(null, null);
        QuotaComponents quotaComponents = QuotaComponents.disabled(sessionProvider, mapperFactory);
        MessageSearchIndex index = new SimpleMessageSearchIndex(mapperFactory, mapperFactory, new DefaultTextExtractor(), new UnsupportAttachmentContentLoader());

        eventBus.register(mapperFactory.deleteMessageListener());

        mailboxManager = new PostgresMailboxManager(mapperFactory, sessionProvider,
            messageParser, new PostgresMessageId.Factory(),
            eventBus, annotationManager,
            storeRightManager, quotaComponents, index, new PostgresThreadIdGuessingAlgorithm(new PostgresThreadDAO.Factory(postgresExtension.getExecutorFactory()),
                new PostgresMailboxMessageDAO.Factory(postgresExtension.getExecutorFactory())),
            PreDeletionHooks.NO_PRE_DELETION_HOOK, new UpdatableTickingClock(Instant.now()));
    }

    @Override
    PostgresMailboxManager provideMailboxManager() {
        return mailboxManager;
    }

    @Override
    PostgresMessageDAO providePostgresMessageDAO() {
        return new PostgresMessageDAO(postgresExtension.getPostgresExecutor(), BLOB_ID_FACTORY);
    }

    @Override
    PostgresMailboxMessageDAO providePostgresMailboxMessageDAO() {
        return new PostgresMailboxMessageDAO(postgresExtension.getPostgresExecutor());
    }

    @Override
    PostgresThreadDAO threadDAO() {
        return new PostgresThreadDAO(postgresExtension.getPostgresExecutor());
    }

    @Override
    PostgresAttachmentDAO attachmentDAO() {
        return new PostgresAttachmentDAO(postgresExtension.getPostgresExecutor(), BLOB_ID_FACTORY);
    }

    @Override
    BlobStore blobStore() {
        return blobStore;
    }
}
