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

import java.util.Optional;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CassandraSieveQuotaDAOTest {

    public static final String USER = "user";
    private static CassandraCluster cassandra = CassandraCluster.create(new CassandraSieveRepositoryModule());
    private CassandraSieveQuotaDAO sieveQuotaDAO;

    @Before
    public void setUp() {
        cassandra.ensureAllTables();
        sieveQuotaDAO = new CassandraSieveQuotaDAO(cassandra.getConf());
    }

    @After
    public void tearDown() {
        cassandra.clearAllTables();
    }

    @Test
    public void getQuotaShouldReturnEmptyByDefault() {
        assertThat(sieveQuotaDAO.getQuota().join().isPresent())
            .isFalse();
    }

    @Test
    public void getQuotaUserShouldReturnEmptyByDefault() {
        assertThat(sieveQuotaDAO.getQuota(USER).join().isPresent())
            .isFalse();
    }

    @Test
    public void getQuotaShouldReturnStoredValue() {
        long quota = 15L;
        sieveQuotaDAO.setQuota(quota).join();

        Optional<Long> actual = sieveQuotaDAO.getQuota().join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(quota);
    }

    @Test
    public void getQuotaUserShouldReturnStoredValue() {
        long quota = 15L;
        sieveQuotaDAO.setQuota(USER, quota).join();

        Optional<Long> actual = sieveQuotaDAO.getQuota(USER).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(quota);
    }

    @Test
    public void removeQuotaShouldDeleteQuota() {
        sieveQuotaDAO.setQuota(15L).join();

        sieveQuotaDAO.removeQuota().join();

        Optional<Long> actual = sieveQuotaDAO.getQuota().join();
        assertThat(actual.isPresent()).isFalse();
    }

    @Test
    public void removeQuotaUserShouldDeleteQuotaUser() {
        sieveQuotaDAO.setQuota(USER, 15L).join();

        sieveQuotaDAO.removeQuota(USER).join();

        Optional<Long> actual = sieveQuotaDAO.getQuota(USER).join();
        assertThat(actual.isPresent()).isFalse();
    }

    @Test
    public void removeQuotaShouldWorkWhenNoneStore() {
        sieveQuotaDAO.removeQuota().join();

        Optional<Long> actual = sieveQuotaDAO.getQuota().join();
        assertThat(actual.isPresent()).isFalse();
    }

    @Test
    public void removeQuotaUserShouldWorkWhenNoneStore() {
        sieveQuotaDAO.removeQuota(USER).join();

        Optional<Long> actual = sieveQuotaDAO.getQuota(USER).join();
        assertThat(actual.isPresent()).isFalse();
    }

    @Test
    public void spaceUsedByShouldReturnZeroByDefault() {
        assertThat(sieveQuotaDAO.spaceUsedBy(USER).join()).isEqualTo(0);
    }

    @Test
    public void spaceUsedByShouldReturnStoredValue() {
        long spaceUsed = 18L;

        sieveQuotaDAO.updateSpaceUsed(USER, spaceUsed).join();

        assertThat(sieveQuotaDAO.spaceUsedBy(USER).join()).isEqualTo(spaceUsed);
    }

    @Test
    public void updateSpaceUsedShouldBeAdditive() {
        long spaceUsed = 18L;

        sieveQuotaDAO.updateSpaceUsed(USER, spaceUsed).join();
        sieveQuotaDAO.updateSpaceUsed(USER, spaceUsed).join();

        assertThat(sieveQuotaDAO.spaceUsedBy(USER).join()).isEqualTo(2 * spaceUsed);
    }

    @Test
    public void updateSpaceUsedShouldWorkWithNegativeValues() {
        long spaceUsed = 18L;

        sieveQuotaDAO.updateSpaceUsed(USER, spaceUsed).join();
        sieveQuotaDAO.updateSpaceUsed(USER, -1 * spaceUsed).join();

        assertThat(sieveQuotaDAO.spaceUsedBy(USER).join()).isEqualTo(0L);
    }
}
