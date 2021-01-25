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

import java.util.Set;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.events.EventBus;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.store.MessageIdManagerTestSystem;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.metrics.tests.RecordingMetricFactory;

class CassandraMessageIdManagerTestSystem {

    static MessageIdManagerTestSystem createTestingData(CassandraCluster cassandra, QuotaManager quotaManager, EventBus eventBus,
                                                        Set<PreDeletionHook> preDeletionHooks) {
        CassandraMailboxSessionMapperFactory mapperFactory = CassandraTestSystemFixture.createMapperFactory(cassandra);

        return new MessageIdManagerTestSystem(CassandraTestSystemFixture.createMessageIdManager(mapperFactory, quotaManager, eventBus, new PreDeletionHooks(preDeletionHooks, new RecordingMetricFactory())),
            new CassandraMessageId.Factory(),
            mapperFactory,
            CassandraTestSystemFixture.createMailboxManager(mapperFactory)) {
        };
    }

    static MessageIdManagerTestSystem createTestingDataWithQuota(CassandraCluster cassandra, QuotaManager quotaManager, CurrentQuotaManager currentQuotaManager) {
        CassandraMailboxSessionMapperFactory mapperFactory = CassandraTestSystemFixture.createMapperFactory(cassandra);

        CassandraMailboxManager mailboxManager = CassandraTestSystemFixture.createMailboxManager(mapperFactory);
        ListeningCurrentQuotaUpdater listeningCurrentQuotaUpdater = new ListeningCurrentQuotaUpdater(
            currentQuotaManager,
            mailboxManager.getQuotaComponents().getQuotaRootResolver(), mailboxManager.getEventBus(), quotaManager);
        mailboxManager.getEventBus().register(listeningCurrentQuotaUpdater);
        return new MessageIdManagerTestSystem(CassandraTestSystemFixture.createMessageIdManager(mapperFactory, quotaManager, mailboxManager.getEventBus(),
            PreDeletionHooks.NO_PRE_DELETION_HOOK),
            new CassandraMessageId.Factory(),
            mapperFactory,
            mailboxManager);
    }

}