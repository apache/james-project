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
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.store.AbstractMessageIdManagerQuotaTest;
import org.apache.james.mailbox.store.MessageIdManagerTestSystem;
import org.apache.james.mailbox.store.quota.StoreQuotaManager;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;

public class CassandraMessageIdManagerQuotaTest extends AbstractMessageIdManagerQuotaTest {

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();

    @Rule
    public DockerCassandraRestartRule cassandraRestartRule = new DockerCassandraRestartRule();

    private CassandraCluster cassandra;

    @Override
    @Before
    public void setUp() throws Exception {
        cassandra = CassandraCluster.create(MailboxAggregateModule.MODULE_WITH_QUOTA, cassandraServer.getHost());
        super.setUp();
    }

    @After
    public void tearDown() {
        cassandra.clearTables();
        cassandra.closeCluster();
    }

    @Override
    protected MessageIdManagerTestSystem createTestSystem(QuotaManager quotaManager, CurrentQuotaManager currentQuotaManager) throws Exception {
        return CassandraMessageIdManagerTestSystem.createTestingDataWithQuota(cassandra, quotaManager, currentQuotaManager);
    }

    @Override
    protected MaxQuotaManager createMaxQuotaManager() {
        return CassandraTestSystemFixture.createMaxQuotaManager(cassandra);
    }

    @Override
    protected QuotaManager createQuotaManager(MaxQuotaManager maxQuotaManager, CurrentQuotaManager currentQuotaManager) {
        return new StoreQuotaManager(currentQuotaManager, maxQuotaManager);
    }

    @Override
    protected CurrentQuotaManager createCurrentQuotaManager() {
        return CassandraTestSystemFixture.createCurrentQuotaManager(cassandra);
    }
}
