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

package org.apache.james.mailbox.postgres.quota;

import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.mailbox.postgres.JPAMailboxFixture;
import org.apache.james.mailbox.postgres.quota.JPAPerUserMaxQuotaDAO;
import org.apache.james.mailbox.postgres.quota.JPAPerUserMaxQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.store.quota.GenericMaxQuotaManagerTest;
import org.junit.jupiter.api.AfterEach;

class JPAPerUserMaxQuotaTest extends GenericMaxQuotaManagerTest {

    static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(JPAMailboxFixture.QUOTA_PERSISTANCE_CLASSES);

    @Override
    protected MaxQuotaManager provideMaxQuotaManager() {
        return new JPAPerUserMaxQuotaManager(JPA_TEST_CLUSTER.getEntityManagerFactory(), new JPAPerUserMaxQuotaDAO(JPA_TEST_CLUSTER.getEntityManagerFactory()));
    }

    @AfterEach
    void cleanUp() {
        JPA_TEST_CLUSTER.clear(JPAMailboxFixture.QUOTA_TABLES_NAMES);
    }
}
