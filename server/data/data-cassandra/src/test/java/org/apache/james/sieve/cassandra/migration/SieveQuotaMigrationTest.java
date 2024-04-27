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

package org.apache.james.sieve.cassandra.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraMutualizedQuotaModule;
import org.apache.james.backends.cassandra.components.CassandraQuotaCurrentValueDao;
import org.apache.james.backends.cassandra.components.CassandraQuotaLimitDao;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.domainlist.cassandra.CassandraDomainListModule;
import org.apache.james.sieve.cassandra.CassandraSieveQuotaDAOV1;
import org.apache.james.sieve.cassandra.CassandraSieveQuotaDAOV2;
import org.apache.james.sieve.cassandra.CassandraSieveQuotaModule;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.cassandra.CassandraUsersDAO;
import org.apache.james.user.cassandra.CassandraUsersRepositoryModule;
import org.apache.james.user.lib.UsersRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SieveQuotaMigrationTest {
    public static final Username BOB = Username.of("bob");

    private UsersRepositoryImpl<CassandraUsersDAO> getUsersRepository() throws Exception {
        boolean enableVirtualHosting = false;
        DomainList domainList = new SimpleDomainList();
        domainList.addDomain(Domain.of("domain.tld"));
        CassandraUsersDAO usersDAO = new CassandraUsersDAO(cassandraCluster.getCassandraCluster().getConf());
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("enableVirtualHosting", String.valueOf(enableVirtualHosting));

        UsersRepositoryImpl<CassandraUsersDAO> usersRepository = new UsersRepositoryImpl<>(domainList, usersDAO);
        usersRepository.configure(configuration);
        return usersRepository;
    }

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(
        CassandraSieveQuotaModule.MODULE,
        CassandraUsersRepositoryModule.MODULE,
        CassandraDomainListModule.MODULE,
        CassandraMutualizedQuotaModule.MODULE));

    private CassandraSieveQuotaDAOV1 oldDAO;
    private CassandraSieveQuotaDAOV2 newDAO;
    private SieveQuotaMigration testee;

    @BeforeEach
    void setUp() throws Exception {
        oldDAO = new CassandraSieveQuotaDAOV1(cassandraCluster.getCassandraCluster().getConf());
        newDAO = new CassandraSieveQuotaDAOV2(new CassandraQuotaCurrentValueDao(cassandraCluster.getCassandraCluster().getConf()),
            new CassandraQuotaLimitDao(cassandraCluster.getCassandraCluster().getConf()));
        UsersRepository usersRepository = getUsersRepository();

        usersRepository.addUser(BOB, "passBob");

        testee = new SieveQuotaMigration(usersRepository, oldDAO, newDAO);
    }

    @Test
    void shouldMigrateGlobalLimit() throws Exception {
        oldDAO.setQuota(QuotaSizeLimit.size(32)).block();

        testee.apply();

        assertThat(newDAO.getQuota().block())
            .contains(QuotaSizeLimit.size(32));
    }

    @Test
    void shouldNotFailWhenNoGlobalLimit() throws Exception {
        testee.apply();

        assertThat(newDAO.getQuota().block())
            .isEmpty();
    }

    @Test
    void shouldNotFailWhenNoUserLimit() throws Exception {
        testee.apply();

        assertThat(newDAO.getQuota(BOB).block())
            .isEmpty();
    }

    @Test
    void shouldNotFailWhenNoSpaceUsed() throws Exception {
        testee.apply();

        assertThat(newDAO.spaceUsedBy(BOB).block())
            .isEqualTo(0L);
    }

    @Test
    void shouldMigrateUserSpace() throws Exception {
        oldDAO.updateSpaceUsed(BOB, 42).block();

        testee.apply();

        assertThat(newDAO.spaceUsedBy(BOB).block())
            .isEqualTo(42L);
    }


    @Test
    void shouldMigrateUserLimit() throws Exception {
        oldDAO.setQuota(BOB, QuotaSizeLimit.size(90)).block();

        testee.apply();

        assertThat(newDAO.getQuota(BOB).block())
            .contains(QuotaSizeLimit.size(90));
    }
}