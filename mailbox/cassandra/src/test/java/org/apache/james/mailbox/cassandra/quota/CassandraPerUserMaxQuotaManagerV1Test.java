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

package org.apache.james.mailbox.cassandra.quota;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.StatementRecorder;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraMutualizedQuotaModule;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.mailbox.cassandra.mail.utils.GuiceUtils;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxQuotaModule;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.store.quota.GenericMaxQuotaManagerTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraPerUserMaxQuotaManagerV1Test extends GenericMaxQuotaManagerTest {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(
        CassandraBlobModule.MODULE,
        CassandraMailboxQuotaModule.MODULE,
        CassandraMutualizedQuotaModule.MODULE));

    @Override
    protected MaxQuotaManager provideMaxQuotaManager() {
        return GuiceUtils.testInjector(cassandraCluster.getCassandraCluster())
            .getInstance(CassandraPerUserMaxQuotaManagerV1.class);
    }

    @Test
    void quotaDetailsShouldGroupStatements(CassandraCluster cassandra) {
        StatementRecorder statementRecorder = cassandra.getConf().recordStatements();

        maxQuotaManager.quotaDetails(QUOTA_ROOT);

        assertThat(statementRecorder.listExecutedStatements()).hasSize(3);
        // 1 statement for user limits, 1 for domain limits, 1 for global limits
    }
}
