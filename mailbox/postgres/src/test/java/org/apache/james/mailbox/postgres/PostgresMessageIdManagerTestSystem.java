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

import java.util.Set;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.events.EventBus;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.store.MessageIdManagerTestSystem;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.metrics.tests.RecordingMetricFactory;

public class PostgresMessageIdManagerTestSystem {
    static MessageIdManagerTestSystem createTestingData(PostgresExtension postgresExtension, QuotaManager quotaManager, EventBus eventBus,
                                                        Set<PreDeletionHook> preDeletionHooks) {
        PostgresMailboxSessionMapperFactory mapperFactory = PostgresTestSystemFixture.createMapperFactory(postgresExtension);

        return new MessageIdManagerTestSystem(PostgresTestSystemFixture.createMessageIdManager(mapperFactory, quotaManager, eventBus, new PreDeletionHooks(preDeletionHooks, new RecordingMetricFactory())),
            new PostgresMessageId.Factory(),
            mapperFactory,
            PostgresTestSystemFixture.createMailboxManager(mapperFactory)) {
        };
    }

    static MessageIdManagerTestSystem createTestingDataWithQuota(PostgresExtension postgresExtension, QuotaManager quotaManager, CurrentQuotaManager currentQuotaManager) {
        PostgresMailboxSessionMapperFactory mapperFactory = PostgresTestSystemFixture.createMapperFactory(postgresExtension);

        PostgresMailboxManager mailboxManager = PostgresTestSystemFixture.createMailboxManager(mapperFactory);
        ListeningCurrentQuotaUpdater listeningCurrentQuotaUpdater = new ListeningCurrentQuotaUpdater(
            currentQuotaManager,
            mailboxManager.getQuotaComponents().getQuotaRootResolver(), mailboxManager.getEventBus(), quotaManager);
        mailboxManager.getEventBus().register(listeningCurrentQuotaUpdater);
        return new MessageIdManagerTestSystem(PostgresTestSystemFixture.createMessageIdManager(mapperFactory, quotaManager, mailboxManager.getEventBus(),
            PreDeletionHooks.NO_PRE_DELETION_HOOK),
            new PostgresMessageId.Factory(),
            mapperFactory,
            mailboxManager);
    }
}
