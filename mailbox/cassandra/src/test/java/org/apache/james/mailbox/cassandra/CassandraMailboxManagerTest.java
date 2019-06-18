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
package org.apache.james.mailbox.cassandra;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.CassandraRestartExtension;
import org.apache.james.mailbox.MailboxManagerTest;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

@ExtendWith(CassandraRestartExtension.class)
public class CassandraMailboxManagerTest extends MailboxManagerTest<CassandraMailboxManager> {
    @RegisterExtension
    static CassandraClusterExtension cassandra = new CassandraClusterExtension(MailboxAggregateModule.MODULE_WITH_QUOTA);

    @Override
    protected CassandraMailboxManager provideMailboxManager() {
        return CassandraMailboxManagerProvider.provideMailboxManager(
            cassandra.getCassandraCluster().getConf(),
            cassandra.getCassandraCluster().getTypesProvider(),
            new PreDeletionHooks(preDeletionHooks(), new NoopMetricFactory()));
    }

    @Override
    protected EventBus retrieveEventBus(CassandraMailboxManager mailboxManager) {
        return mailboxManager.getEventBus();
    }
}
