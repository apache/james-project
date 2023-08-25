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

package org.apache.james.mailbox.cassandra.quota.migration;

import static org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver.SEPARATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraMutualizedQuotaModule;
import org.apache.james.backends.cassandra.components.CassandraQuotaCurrentValueDao;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.domainlist.cassandra.CassandraDomainListModule;
import org.apache.james.mailbox.cassandra.modules.CassandraQuotaModule;
import org.apache.james.mailbox.cassandra.quota.CassandraCurrentQuotaManagerV1;
import org.apache.james.mailbox.cassandra.quota.CassandraCurrentQuotaManagerV2;
import org.apache.james.mailbox.model.CurrentQuotas;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.cassandra.CassandraUsersDAO;
import org.apache.james.user.cassandra.CassandraUsersRepositoryModule;
import org.apache.james.user.lib.UsersRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraCurrentQuotaManagerMigrationTest {
    private static final Username BOB = Username.of("bob");
    private static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot(MailboxConstants.USER_NAMESPACE + SEPARATOR + BOB.asString(), BOB.getDomainPart());

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(
        CassandraQuotaModule.MODULE,
        CassandraUsersRepositoryModule.MODULE,
        CassandraDomainListModule.MODULE,
        CassandraMutualizedQuotaModule.MODULE));

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

    private CassandraCurrentQuotaManagerV1 oldCurrentQuotaManager;
    private CassandraCurrentQuotaManagerV2 newCurrentQuotaManager;
    private UserQuotaRootResolver userQuotaRootResolver;
    private CassandraCurrentQuotaManagerMigration testee;

    @BeforeEach
    void setUp() throws Exception {
        CassandraCluster cassandra = cassandraCluster.getCassandraCluster();

        oldCurrentQuotaManager = new CassandraCurrentQuotaManagerV1(cassandra.getConf());
        newCurrentQuotaManager = new CassandraCurrentQuotaManagerV2(new CassandraQuotaCurrentValueDao(cassandra.getConf()));
        UsersRepository usersRepository = getUsersRepository();

        usersRepository.addUser(BOB, "passBob");

        userQuotaRootResolver = mock(UserQuotaRootResolver.class);
        when(userQuotaRootResolver.forUser(eq(BOB))).thenReturn(QUOTA_ROOT);

        testee = new CassandraCurrentQuotaManagerMigration(usersRepository, userQuotaRootResolver, oldCurrentQuotaManager, newCurrentQuotaManager);
    }

    @Test
    void shouldMigrateQuotaUsage() throws Exception {
        CurrentQuotas currentQuotas = new CurrentQuotas(QuotaCountUsage.count(42L), QuotaSizeUsage.size(172L));
        QuotaOperation quotaOperation =  QuotaOperation.from(QUOTA_ROOT, currentQuotas);

        oldCurrentQuotaManager.setCurrentQuotas(quotaOperation).block();

        testee.apply();

        assertThat(newCurrentQuotaManager.getCurrentQuotas(QUOTA_ROOT).block())
            .isEqualTo(currentQuotas);
    }

    @Test
    void shouldNotFailWhenNoQuota() throws Exception {
        CurrentQuotas defaultQuota = new CurrentQuotas(QuotaCountUsage.count(0L), QuotaSizeUsage.size(0L));

        testee.apply();

        assertThat(newCurrentQuotaManager.getCurrentQuotas(QUOTA_ROOT).block())
            .isEqualTo(defaultQuota);
    }
}
