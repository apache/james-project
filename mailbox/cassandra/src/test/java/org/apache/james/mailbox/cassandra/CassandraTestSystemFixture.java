/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.cassandra;

import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.components.CassandraQuotaCurrentValueDao;
import org.apache.james.backends.cassandra.components.CassandraQuotaLimitDao;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.AttachmentContentLoader;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.quota.CassandraCurrentQuotaManagerV2;
import org.apache.james.mailbox.cassandra.quota.CassandraPerUserMaxQuotaManagerV2;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.store.MailboxManagerConfiguration;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
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
import org.apache.james.utils.UpdatableTickingClock;

public class CassandraTestSystemFixture {

    public static CassandraMailboxSessionMapperFactory createMapperFactory(CassandraCluster cassandra) {
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();

        return TestCassandraMailboxSessionMapperFactory.forTests(cassandra, messageIdFactory);
    }

    public static CassandraMailboxManager createMailboxManager(CassandraMailboxSessionMapperFactory mapperFactory) {
        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        StoreRightManager storeRightManager = new StoreRightManager(mapperFactory, new UnionMailboxACLResolver(), eventBus);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mapperFactory, storeRightManager);

        SessionProviderImpl sessionProvider = new SessionProviderImpl(mock(Authenticator.class), mock(Authorizator.class));

        QuotaComponents quotaComponents = QuotaComponents.disabled(sessionProvider, mapperFactory);
        AttachmentContentLoader attachmentContentLoader = null;
        MessageSearchIndex index = new SimpleMessageSearchIndex(mapperFactory, mapperFactory, new DefaultTextExtractor(), attachmentContentLoader);
        CassandraMailboxManager cassandraMailboxManager = new CassandraMailboxManager(mapperFactory, sessionProvider,
            new NoMailboxPathLocker(), new MessageParser(), new CassandraMessageId.Factory(),
            eventBus, annotationManager, storeRightManager, quotaComponents, index, MailboxManagerConfiguration.DEFAULT, PreDeletionHooks.NO_PRE_DELETION_HOOK,
            new NaiveThreadIdGuessingAlgorithm(), new UpdatableTickingClock(Instant.now()));

        eventBus.register(new MailboxAnnotationListener(mapperFactory, sessionProvider));
        eventBus.register(mapperFactory.deleteMessageListener());

        return cassandraMailboxManager;
    }

    static StoreMessageIdManager createMessageIdManager(CassandraMailboxSessionMapperFactory mapperFactory, QuotaManager quotaManager, EventBus eventBus,
                                                        PreDeletionHooks preDeletionHooks) {
        CassandraMailboxManager mailboxManager = createMailboxManager(mapperFactory);
        return new StoreMessageIdManager(
            mailboxManager,
            mapperFactory,
            eventBus,
            quotaManager,
            new DefaultUserQuotaRootResolver(mailboxManager.getSessionProvider(), mapperFactory),
            preDeletionHooks);
    }

    static MaxQuotaManager createMaxQuotaManager(CassandraCluster cassandra) {
        return new CassandraPerUserMaxQuotaManagerV2(new CassandraQuotaLimitDao(cassandra.getConf()));
    }

    public static CurrentQuotaManager createCurrentQuotaManager(CassandraCluster cassandra) {
        return new CassandraCurrentQuotaManagerV2(new CassandraQuotaCurrentValueDao(cassandra.getConf()));
    }

}
