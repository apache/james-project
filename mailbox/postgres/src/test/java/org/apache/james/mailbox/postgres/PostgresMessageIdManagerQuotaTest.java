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

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.quota.PostgresQuotaModule;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.store.AbstractMessageIdManagerQuotaTest;
import org.apache.james.mailbox.store.MessageIdManagerTestSystem;
import org.apache.james.mailbox.store.quota.StoreQuotaManager;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresMessageIdManagerQuotaTest extends AbstractMessageIdManagerQuotaTest {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresModule.aggregateModules(
        PostgresMailboxAggregateModule.MODULE,
        PostgresQuotaModule.MODULE));

    @Override
    protected MessageIdManagerTestSystem createTestSystem(QuotaManager quotaManager, CurrentQuotaManager currentQuotaManager) throws Exception {
        return PostgresMessageIdManagerTestSystem.createTestingDataWithQuota(postgresExtension, quotaManager, currentQuotaManager);
    }

    @Override
    protected MaxQuotaManager createMaxQuotaManager() {
        return PostgresTestSystemFixture.createMaxQuotaManager(postgresExtension);
    }

    @Override
    protected QuotaManager createQuotaManager(MaxQuotaManager maxQuotaManager, CurrentQuotaManager currentQuotaManager) {
        return new StoreQuotaManager(currentQuotaManager, maxQuotaManager);
    }

    @Override
    protected CurrentQuotaManager createCurrentQuotaManager() {
        return PostgresTestSystemFixture.createCurrentQuotaManager(postgresExtension);
    }
}
