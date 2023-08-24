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

package org.apache.james.sieve.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraSieveQuotaDAOV1Test {
    private static final Username USERNAME = Username.of("user");
    private static final QuotaSizeLimit QUOTA_SIZE = QuotaSizeLimit.size(15L);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraSieveRepositoryModule.MODULE);

    private CassandraSieveQuotaDAOV1 sieveQuotaDAO;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        sieveQuotaDAO = new CassandraSieveQuotaDAOV1(cassandra.getConf());
    }

    @Test
    void getQuotaShouldReturnEmptyByDefault() {
        assertThat(sieveQuotaDAO.getQuota().block())
            .isEmpty();
    }

    @Test
    void getQuotaUserShouldReturnEmptyByDefault() {
        assertThat(sieveQuotaDAO.getQuota(USERNAME).block())
            .isEmpty();
    }

    @Test
    void getQuotaShouldReturnStoredValue() {
        sieveQuotaDAO.setQuota(QUOTA_SIZE).block();

        assertThat(sieveQuotaDAO.getQuota().block())
            .contains(QUOTA_SIZE);
    }

    @Test
    void getQuotaUserShouldReturnStoredValue() {
        sieveQuotaDAO.setQuota(USERNAME, QUOTA_SIZE).block();

        assertThat(sieveQuotaDAO.getQuota(USERNAME).block())
            .contains(QUOTA_SIZE);
    }

    @Test
    void removeQuotaShouldDeleteQuota() {
        sieveQuotaDAO.setQuota(QUOTA_SIZE).block();

        sieveQuotaDAO.removeQuota().block();

        assertThat(sieveQuotaDAO.getQuota().block())
            .isEmpty();
    }

    @Test
    void removeQuotaUserShouldDeleteQuotaUser() {
        sieveQuotaDAO.setQuota(USERNAME, QUOTA_SIZE).block();

        sieveQuotaDAO.removeQuota(USERNAME).block();

        assertThat(sieveQuotaDAO.getQuota(USERNAME).block())
            .isEmpty();
    }

    @Test
    void removeQuotaShouldWorkWhenNoneStore() {
        sieveQuotaDAO.removeQuota().block();

        assertThat(sieveQuotaDAO.getQuota().block())
            .isEmpty();
    }

    @Test
    void removeQuotaUserShouldWorkWhenNoneStore() {
        sieveQuotaDAO.removeQuota(USERNAME).block();

        assertThat(sieveQuotaDAO.getQuota(USERNAME).block())
            .isEmpty();
    }

    @Test
    void spaceUsedByShouldReturnZeroByDefault() {
        assertThat(sieveQuotaDAO.spaceUsedBy(USERNAME).block()).isEqualTo(0);
    }

    @Test
    void spaceUsedByShouldReturnStoredValue() {
        long spaceUsed = 18L;

        sieveQuotaDAO.updateSpaceUsed(USERNAME, spaceUsed).block();

        assertThat(sieveQuotaDAO.spaceUsedBy(USERNAME).block()).isEqualTo(spaceUsed);
    }

    @Test
    void updateSpaceUsedShouldBeAdditive() {
        long spaceUsed = 18L;

        sieveQuotaDAO.updateSpaceUsed(USERNAME, spaceUsed).block();
        sieveQuotaDAO.updateSpaceUsed(USERNAME, spaceUsed).block();

        assertThat(sieveQuotaDAO.spaceUsedBy(USERNAME).block()).isEqualTo(2 * spaceUsed);
    }

    @Test
    void updateSpaceUsedShouldWorkWithNegativeValues() {
        long spaceUsed = 18L;

        sieveQuotaDAO.updateSpaceUsed(USERNAME, spaceUsed).block();
        sieveQuotaDAO.updateSpaceUsed(USERNAME, -1 * spaceUsed).block();

        assertThat(sieveQuotaDAO.spaceUsedBy(USERNAME).block()).isEqualTo(0L);
    }
}
