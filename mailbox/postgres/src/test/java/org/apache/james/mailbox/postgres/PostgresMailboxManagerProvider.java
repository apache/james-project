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
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.postgres.mail.PostgresMailboxManager;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.NaiveThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.apache.james.utils.UpdatableTickingClock;

public class PostgresMailboxManagerProvider {

    private static final int LIMIT_ANNOTATIONS = 3;
    private static final int LIMIT_ANNOTATION_SIZE = 30;

    public static final BlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();

    public static PostgresMailboxManager provideMailboxManager(PostgresExtension postgresExtension) {
        DeDuplicationBlobStore blobStore = new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.DEFAULT, BLOB_ID_FACTORY);
        MailboxSessionMapperFactory mf = provideMailboxSessionMapperFactory(postgresExtension, BLOB_ID_FACTORY, blobStore);

        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        MessageParser messageParser = new MessageParser();

        Authenticator noAuthenticator = null;
        Authorizator noAuthorizator = null;

        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        StoreRightManager storeRightManager = new StoreRightManager(mf, aclResolver, eventBus);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mf, storeRightManager,
            LIMIT_ANNOTATIONS, LIMIT_ANNOTATION_SIZE);
        SessionProviderImpl sessionProvider = new SessionProviderImpl(noAuthenticator, noAuthorizator);
        QuotaComponents quotaComponents = QuotaComponents.disabled(sessionProvider, mf);
        MessageSearchIndex index = new SimpleMessageSearchIndex(mf, mf, new DefaultTextExtractor(), new PostgresAttachmentContentLoader());

        PostgresMessageDAO.Factory postgresMessageDAOFactory = new PostgresMessageDAO.Factory(BLOB_ID_FACTORY, postgresExtension.getExecutorFactory());
        PostgresMailboxMessageDAO.Factory postgresMailboxMessageDAOFactory = new PostgresMailboxMessageDAO.Factory(postgresExtension.getExecutorFactory());

        eventBus.register(new DeleteMessageListener(blobStore, postgresMailboxMessageDAOFactory, postgresMessageDAOFactory));

        return new PostgresMailboxManager((PostgresMailboxSessionMapperFactory) mf, sessionProvider,
            messageParser, new PostgresMessageId.Factory(),
            eventBus, annotationManager,
            storeRightManager, quotaComponents, index, new NaiveThreadIdGuessingAlgorithm(), new UpdatableTickingClock(Instant.now()));
    }

    public static MailboxSessionMapperFactory provideMailboxSessionMapperFactory(PostgresExtension postgresExtension) {
        BlobId.Factory blobIdFactory = new HashBlobId.Factory();
        DeDuplicationBlobStore blobStore = new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.DEFAULT, blobIdFactory);

        return provideMailboxSessionMapperFactory(postgresExtension, blobIdFactory, blobStore);
    }

    public static MailboxSessionMapperFactory provideMailboxSessionMapperFactory(PostgresExtension postgresExtension,
                                                                                 BlobId.Factory blobIdFactory,
                                                                                 DeDuplicationBlobStore blobStore) {
        return new PostgresMailboxSessionMapperFactory(
            postgresExtension.getExecutorFactory(),
            Clock.systemUTC(),
            blobStore,
            blobIdFactory);
    }

}
