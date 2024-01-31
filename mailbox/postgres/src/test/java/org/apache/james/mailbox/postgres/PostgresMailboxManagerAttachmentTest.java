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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.postgres.mail.dao.PostgresAttachmentDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresThreadDAO;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.AbstractMailboxManagerAttachmentTest;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreAttachmentManager;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.NaiveThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableSet;

public class PostgresMailboxManagerAttachmentTest extends AbstractMailboxManagerAttachmentTest {

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresMailboxAggregateModule.MODULE);
    private static PostgresMailboxManager mailboxManager;
    private static PostgresMailboxManager parseFailingMailboxManager;
    private static PostgresMailboxSessionMapperFactory mapperFactory;

    @BeforeEach
    void beforeAll() throws Exception {
        BlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();
        DeDuplicationBlobStore blobStore = new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.DEFAULT, BLOB_ID_FACTORY);
        mapperFactory = new PostgresMailboxSessionMapperFactory(postgresExtension.getExecutorFactory(), Clock.systemUTC(), blobStore, BLOB_ID_FACTORY);

        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        MessageParser messageParser = new MessageParser();

        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        StoreRightManager storeRightManager = new StoreRightManager(mapperFactory, aclResolver, eventBus);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mapperFactory, storeRightManager, 3, 30);
        SessionProviderImpl sessionProvider = new SessionProviderImpl(null, null);
        QuotaComponents quotaComponents = QuotaComponents.disabled(sessionProvider, mapperFactory);

        MessageIdManager messageIdManager = new StoreMessageIdManager(storeRightManager, mapperFactory
            , eventBus, new NoQuotaManager(), mock(QuotaRootResolver.class), PreDeletionHooks.NO_PRE_DELETION_HOOK);

        StoreAttachmentManager storeAttachmentManager = new StoreAttachmentManager(mapperFactory, messageIdManager);

        MessageSearchIndex index = new SimpleMessageSearchIndex(mapperFactory, mapperFactory, new DefaultTextExtractor(), storeAttachmentManager);

        PostgresMessageDAO.Factory postgresMessageDAOFactory = new PostgresMessageDAO.Factory(BLOB_ID_FACTORY, postgresExtension.getExecutorFactory());
        PostgresMailboxMessageDAO.Factory postgresMailboxMessageDAOFactory = new PostgresMailboxMessageDAO.Factory(postgresExtension.getExecutorFactory());
        PostgresAttachmentDAO.Factory attachmentDAOFactory = new PostgresAttachmentDAO.Factory(postgresExtension.getExecutorFactory(), BLOB_ID_FACTORY);
        PostgresThreadDAO.Factory threadDAOFactory = new PostgresThreadDAO.Factory(postgresExtension.getExecutorFactory());

        eventBus.register(new DeleteMessageListener(blobStore, postgresMailboxMessageDAOFactory, postgresMessageDAOFactory,
            attachmentDAOFactory, threadDAOFactory, ImmutableSet.of()));

        mailboxManager = new PostgresMailboxManager(mapperFactory, sessionProvider,
            messageParser, new PostgresMessageId.Factory(),
            eventBus, annotationManager,
            storeRightManager, quotaComponents, index, new NaiveThreadIdGuessingAlgorithm(),
            PreDeletionHooks.NO_PRE_DELETION_HOOK,
            new UpdatableTickingClock(Instant.now()));

        MessageParser failingMessageParser = mock(MessageParser.class);
        when(failingMessageParser.retrieveAttachments(any(InputStream.class)))
            .thenThrow(new RuntimeException("Message parser set to fail"));


        parseFailingMailboxManager = new PostgresMailboxManager(mapperFactory, sessionProvider,
            failingMessageParser, new PostgresMessageId.Factory(),
            eventBus, annotationManager,
            storeRightManager, quotaComponents, index, new NaiveThreadIdGuessingAlgorithm(),
            PreDeletionHooks.NO_PRE_DELETION_HOOK,
            new UpdatableTickingClock(Instant.now()));

        super.setUp();
    }

    @Override
    protected MailboxManager getMailboxManager() {
        return mailboxManager;
    }

    @Override
    protected MailboxManager getParseFailingMailboxManager() {
        return parseFailingMailboxManager;
    }

    @Override
    protected MailboxSessionMapperFactory getMailboxSessionMapperFactory() {
        return mapperFactory;
    }

    @Override
    protected AttachmentMapperFactory getAttachmentMapperFactory() {
        return mapperFactory;
    }
}
