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

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.Optional;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.events.EventBus;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxManagerTest;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraMailboxManagerWithoutAclTest extends MailboxManagerTest<CassandraMailboxManager> {
    @RegisterExtension
    static CassandraClusterExtension cassandra = new CassandraClusterExtension(MailboxAggregateModule.MODULE_WITH_QUOTA);

    @Override
    protected CassandraMailboxManager provideMailboxManager() {
        CassandraMailboxManager cassandraMailboxManager = spy(CassandraMailboxManagerProvider.provideMailboxManager(
            cassandra.getCassandraCluster(),
            new PreDeletionHooks(preDeletionHooks(), new RecordingMetricFactory()),
            CassandraConfiguration.builder()
                .aclEnabled(Optional.of(false))
                .build()));
        when(cassandraMailboxManager.getSupportedMailboxCapabilities())
            .thenReturn(EnumSet.of(
                MailboxManager.MailboxCapabilities.Move,
                MailboxManager.MailboxCapabilities.UserFlag,
                MailboxManager.MailboxCapabilities.Namespace,
                MailboxManager.MailboxCapabilities.Annotation,
                MailboxManager.MailboxCapabilities.Quota));
        return cassandraMailboxManager;
    }

    @Override
    protected SubscriptionManager provideSubscriptionManager() {
        return new StoreSubscriptionManager(provideMailboxManager().getMapperFactory(), provideMailboxManager().getMapperFactory(), provideMailboxManager().getEventBus());
    }

    @Override
    protected EventBus retrieveEventBus(CassandraMailboxManager mailboxManager) {
        return mailboxManager.getEventBus();
    }
}
