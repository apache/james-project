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

package org.apache.james.mailbox.postgres.mail.task;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.quota.PostgresQuotaCurrentValueDAO;
import org.apache.james.backends.postgres.quota.PostgresQuotaModule;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.postgres.PostgresMailboxAggregateModule;
import org.apache.james.mailbox.postgres.PostgresMailboxManagerProvider;
import org.apache.james.mailbox.postgres.quota.PostgresCurrentQuotaManager;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasService;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasServiceContract;
import org.apache.james.mailbox.quota.task.RecomputeMailboxCurrentQuotasService;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.postgres.PostgresUserModule;
import org.apache.james.user.postgres.PostgresUsersDAO;
import org.apache.james.user.postgres.PostgresUsersRepository;
import org.apache.james.user.postgres.PostgresUsersRepositoryConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

class PostgresRecomputeCurrentQuotasServiceTest implements RecomputeCurrentQuotasServiceContract {

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(PostgresModule.aggregateModules(
        PostgresMailboxAggregateModule.MODULE,
        PostgresQuotaModule.MODULE,
        PostgresUserModule.MODULE));

    static final DomainList NO_DOMAIN_LIST = null;

    PostgresUsersRepository usersRepository;
    StoreMailboxManager mailboxManager;
    SessionProvider sessionProvider;
    CurrentQuotaManager currentQuotaManager;
    UserQuotaRootResolver userQuotaRootResolver;
    RecomputeCurrentQuotasService testee;

    @BeforeEach
    void setUp() throws Exception {
        MailboxSessionMapperFactory mapperFactory = PostgresMailboxManagerProvider.provideMailboxSessionMapperFactory(postgresExtension);

        PostgresUsersDAO usersDAO = new PostgresUsersDAO(postgresExtension.getPostgresExecutor(),
            PostgresUsersRepositoryConfiguration.DEFAULT);

        usersRepository = new PostgresUsersRepository(NO_DOMAIN_LIST, usersDAO);
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("enableVirtualHosting", "false");
        usersRepository.configure(configuration);

        mailboxManager = PostgresMailboxManagerProvider.provideMailboxManager(postgresExtension);
        sessionProvider = mailboxManager.getSessionProvider();
        currentQuotaManager = new PostgresCurrentQuotaManager(new PostgresQuotaCurrentValueDAO(postgresExtension.getPostgresExecutor()));

        userQuotaRootResolver = new DefaultUserQuotaRootResolver(sessionProvider, mapperFactory);

        CurrentQuotaCalculator currentQuotaCalculator = new CurrentQuotaCalculator(mapperFactory, userQuotaRootResolver);

        testee = new RecomputeCurrentQuotasService(usersRepository,
            ImmutableSet.of(new RecomputeMailboxCurrentQuotasService(currentQuotaManager,
                    currentQuotaCalculator,
                    userQuotaRootResolver,
                    sessionProvider,
                    mailboxManager),
                RECOMPUTE_JMAP_UPLOAD_CURRENT_QUOTAS_SERVICE));
    }

    @Override
    public UsersRepository usersRepository() {
        return usersRepository;
    }

    @Override
    public SessionProvider sessionProvider() {
        return sessionProvider;
    }

    @Override
    public MailboxManager mailboxManager() {
        return mailboxManager;
    }

    @Override
    public CurrentQuotaManager currentQuotaManager() {
        return currentQuotaManager;
    }

    @Override
    public UserQuotaRootResolver userQuotaRootResolver() {
        return userQuotaRootResolver;
    }

    @Override
    public RecomputeCurrentQuotasService testee() {
        return testee;
    }
}
