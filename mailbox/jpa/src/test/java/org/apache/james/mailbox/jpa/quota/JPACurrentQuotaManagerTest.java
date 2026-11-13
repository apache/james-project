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

package org.apache.james.mailbox.jpa.quota;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;

import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.jpa.JPAMailboxFixture;
import org.apache.james.mailbox.jpa.quota.model.JpaCurrentQuota;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.store.quota.CurrentQuotaManagerContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class JPACurrentQuotaManagerTest implements CurrentQuotaManagerContract {

    static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(JPAMailboxFixture.QUOTA_PERSISTANCE_CLASSES);

    @Override
    public CurrentQuotaManager testee() {
        return new JpaCurrentQuotaManager(JPA_TEST_CLUSTER.getEntityManagerFactory());
    }

    @AfterEach
    void tearDown() {
        JPA_TEST_CLUSTER.clear(JPAMailboxFixture.QUOTA_TABLES_NAMES);
    }

    @Test
    void setCurrentQuotasShouldNotWriteWhenQuotasAreUnchanged() {
        QuotaOperation zeroQuotas = new QuotaOperation(QUOTA_ROOT, QuotaCountUsage.count(0), QuotaSizeUsage.size(0));

        Mono.from(testee().setCurrentQuotas(zeroQuotas)).block();

        EntityManager entityManager = JPA_TEST_CLUSTER.getEntityManagerFactory().createEntityManager();
        try {
            assertThat(entityManager.find(JpaCurrentQuota.class, QUOTA_ROOT.getValue())).isNull();
        } finally {
            entityManager.close();
        }
    }
}
