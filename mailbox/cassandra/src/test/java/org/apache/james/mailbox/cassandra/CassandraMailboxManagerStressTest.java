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

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRestartRule;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.mailbox.MailboxManagerStressTest;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;

public class CassandraMailboxManagerStressTest extends MailboxManagerStressTest<CassandraMailboxManager> {
    
    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();

    @Rule
    public DockerCassandraRestartRule cassandraRestartRule = new DockerCassandraRestartRule();

    private CassandraCluster cassandra;
    
    @Before
    public void setup() throws Exception {
        cassandra = CassandraCluster.create(MailboxAggregateModule.MODULE_WITH_QUOTA, cassandraServer.getHost());
        super.setUp();
    }
    
    @Override
    protected CassandraMailboxManager provideManager() {
        return CassandraMailboxManagerProvider.provideMailboxManager(cassandra.getConf(), cassandra.getTypesProvider(), PreDeletionHooks.NO_PRE_DELETION_HOOK);
    }

    @Override
    protected EventBus retrieveEventBus(CassandraMailboxManager mailboxManager) {
        return mailboxManager.getEventBus();
    }

    @After
    public void tearDown() {
        cassandra.clearTables();
        cassandra.closeCluster();
    }
}
