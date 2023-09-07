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

import java.util.Optional;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraMutualizedQuotaModule;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.mailbox.cassandra.mail.utils.GuiceUtils;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxQuotaModule;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.cassandra.CassandraUsersDAO;
import org.apache.james.user.cassandra.CassandraUsersRepositoryModule;
import org.apache.james.user.lib.UsersRepositoryImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

import com.google.inject.Injector;

public class CassandraPerUserMaxQuotaManagerMigrationTest {

    private static final Domain DOMAIN = Domain.of("domain.tld");
    private static final Username USERNAME = Username.of("bob");
    private static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("#private&bob", Optional.empty());

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(
        CassandraUsersRepositoryModule.MODULE,
        CassandraMailboxQuotaModule.MODULE,
        CassandraMutualizedQuotaModule.MODULE));

    private static CassandraPerUserMaxQuotaManagerMigration migration;
    private static MaxQuotaManager oldMaxQuotaManager;
    private static MaxQuotaManager newMaxQuotaManager;
    private static UsersRepository usersRepository;

    @BeforeAll
    static void setup() throws Exception {
        DomainList domainList = new SimpleDomainList();
        domainList.addDomain(DOMAIN);
        CassandraUsersDAO usersDAO = new CassandraUsersDAO(cassandraCluster.getCassandraCluster().getConf());
        usersRepository = new UsersRepositoryImpl<>(domainList, usersDAO);
        Injector testInjector = GuiceUtils.testInjector(cassandraCluster.getCassandraCluster());
        oldMaxQuotaManager = testInjector.getInstance(CassandraPerUserMaxQuotaManagerV1.class);
        newMaxQuotaManager = testInjector.getInstance(CassandraPerUserMaxQuotaManagerV2.class);
        UserQuotaRootResolver userQuotaRootResolver = Mockito.mock(UserQuotaRootResolver.class);
        Mockito.when(userQuotaRootResolver.forUser(USERNAME)).thenReturn(QUOTA_ROOT);
        migration = new CassandraPerUserMaxQuotaManagerMigration(usersRepository,
            domainList,
            oldMaxQuotaManager,
            newMaxQuotaManager,
            userQuotaRootResolver);
    }

    @Test
    void shouldMigrateGlobalLimit() throws Exception {
        oldMaxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(100L));
        oldMaxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(150L));
        migration.apply();
        assertThat(newMaxQuotaManager.getGlobalMaxMessage().get().asLong()).isEqualTo(100L);
        assertThat(newMaxQuotaManager.getGlobalMaxStorage().get().asLong()).isEqualTo(150L);
    }

    @Test
    void shouldNotFailWhenNoGlobalLimit() throws Exception {
        migration.apply();
        assertThat(newMaxQuotaManager.getGlobalMaxMessage()).isEmpty();
        assertThat(newMaxQuotaManager.getGlobalMaxStorage()).isEmpty();
    }

    @Test
    void shouldMigrateDomainLimit() throws Exception {
        oldMaxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCountLimit.count(100L));
        oldMaxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSizeLimit.size(150L));
        migration.apply();
        assertThat(newMaxQuotaManager.getDomainMaxMessage(DOMAIN).get().asLong()).isEqualTo(100);
        assertThat(newMaxQuotaManager.getDomainMaxStorage(DOMAIN).get().asLong()).isEqualTo(150L);
    }

    @Test
    void shouldNotFailWhenNoDomainLimit() throws Exception {
        migration.apply();
        assertThat(newMaxQuotaManager.getDomainMaxMessage(DOMAIN)).isEmpty();
        assertThat(newMaxQuotaManager.getDomainMaxStorage(DOMAIN)).isEmpty();
    }

    @Test
    void shouldMigrateUserLimit() throws Exception {
        usersRepository.addUser(USERNAME, "1");
        oldMaxQuotaManager.setMaxMessage(QUOTA_ROOT, QuotaCountLimit.count(200L));
        oldMaxQuotaManager.setMaxStorage(QUOTA_ROOT, QuotaSizeLimit.size(250L));
        migration.apply();
        assertThat(newMaxQuotaManager.getMaxMessage(QUOTA_ROOT).get().asLong()).isEqualTo(200);
        assertThat(newMaxQuotaManager.getMaxStorage(QUOTA_ROOT).get().asLong()).isEqualTo(250L);
    }

    @Test
    void shouldNotFailWhenNoUserLimit() throws Exception {
        migration.apply();
        assertThat(newMaxQuotaManager.getMaxMessage(QUOTA_ROOT)).isEmpty();
        assertThat(newMaxQuotaManager.getMaxStorage(QUOTA_ROOT)).isEmpty();
    }

}
