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

import java.util.Optional;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.events.EventBus;
import org.apache.james.mailbox.MailboxManagerTest;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgresMailboxManagerTest extends MailboxManagerTest<PostgresMailboxManager> {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresMailboxAggregateModule.MODULE);

    Optional<PostgresMailboxManager> mailboxManager = Optional.empty();

    @Override
    protected PostgresMailboxManager provideMailboxManager() {
        if (mailboxManager.isEmpty()) {
            mailboxManager = Optional.of(PostgresMailboxManagerProvider.provideMailboxManager(postgresExtension,
                new PreDeletionHooks(preDeletionHooks(), new RecordingMetricFactory())));
        }
        return mailboxManager.get();
    }

    @Override
    protected SubscriptionManager provideSubscriptionManager() {
        return new StoreSubscriptionManager(provideMailboxManager().getMapperFactory(), provideMailboxManager().getMapperFactory(), provideMailboxManager().getEventBus());
    }

    @Override
    protected EventBus retrieveEventBus(PostgresMailboxManager mailboxManager) {
        return mailboxManager.getEventBus();
    }
}
