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
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.components.CassandraMutualizedQuotaDataDefinition;
import org.apache.james.backends.cassandra.components.CassandraQuotaLimitDao;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxQuotaDataDefinition;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaChangeNotifier;
import org.apache.james.mailbox.store.quota.GenericMaxQuotaManagerTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraPerUserMaxQuotaManagerV2Test extends GenericMaxQuotaManagerTest {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraDataDefinition.aggregateModules(
        CassandraMailboxQuotaDataDefinition.MODULE,
        CassandraMutualizedQuotaDataDefinition.MODULE));

    @Override
    protected MaxQuotaManager provideMaxQuotaManager() {
        return new CassandraPerUserMaxQuotaManagerV2(new CassandraQuotaLimitDao(cassandraCluster.getCassandraCluster().getConf()),
            QuotaChangeNotifier.NOOP);
    }

    @Test
    void quotaDetailsShouldGroupStatements(CassandraCluster cassandra) {
        StatementRecorder statementRecorder = cassandra.getConf().recordStatements();

        maxQuotaManager.quotaDetails(QUOTA_ROOT);

        assertThat(statementRecorder.listExecutedStatements()).hasSize(3);
        // 1 statement for user limits, 1 for domain limits, 1 for global limits
    }
}
