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

import javax.persistence.EntityManagerFactory;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.backends.jpa.JPAConfiguration;
import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.utils.SimpleJamesPostgresConnectionFactory;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.jpa.model.JPADomain;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.postgres.JPAMailboxFixture;
import org.apache.james.mailbox.postgres.PostgresMailboxSessionMapperFactory;
import org.apache.james.mailbox.postgres.JpaMailboxManagerProvider;
import org.apache.james.mailbox.postgres.mail.JPAModSeqProvider;
import org.apache.james.mailbox.postgres.mail.JPAUidProvider;
import org.apache.james.mailbox.postgres.quota.JpaCurrentQuotaManager;
import org.apache.james.mailbox.postgres.user.PostgresSubscriptionModule;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasService;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasServiceContract;
import org.apache.james.mailbox.quota.task.RecomputeMailboxCurrentQuotasService;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.jpa.JPAUsersRepository;
import org.apache.james.user.jpa.model.JPAUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

class JPARecomputeCurrentQuotasServiceTest implements RecomputeCurrentQuotasServiceContract {

    @RegisterExtension
    static PostgresExtension postgresExtension = new PostgresExtension(PostgresSubscriptionModule.MODULE);

    static final DomainList NO_DOMAIN_LIST = null;

    static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(ImmutableList.<Class<?>>builder()
        .addAll(JPAMailboxFixture.MAILBOX_PERSISTANCE_CLASSES)
        .addAll(JPAMailboxFixture.QUOTA_PERSISTANCE_CLASSES)
        .add(JPAUser.class)
        .add(JPADomain.class)
        .build());

    JPAUsersRepository usersRepository;
    StoreMailboxManager mailboxManager;
    SessionProvider sessionProvider;
    CurrentQuotaManager currentQuotaManager;
    UserQuotaRootResolver userQuotaRootResolver;
    RecomputeCurrentQuotasService testee;

    @BeforeEach
    void setUp() throws Exception {
        EntityManagerFactory entityManagerFactory = JPA_TEST_CLUSTER.getEntityManagerFactory();

        JPAConfiguration jpaConfiguration = JPAConfiguration.builder()
            .driverName("driverName")
            .driverURL("driverUrl")
            .build();

        PostgresMailboxSessionMapperFactory mapperFactory = new PostgresMailboxSessionMapperFactory(entityManagerFactory,
            new JPAUidProvider(entityManagerFactory),
            new JPAModSeqProvider(entityManagerFactory),
            jpaConfiguration,
            new SimpleJamesPostgresConnectionFactory(postgresExtension.getConnectionFactory()));

        usersRepository = new JPAUsersRepository(NO_DOMAIN_LIST);
        usersRepository.setEntityManagerFactory(JPA_TEST_CLUSTER.getEntityManagerFactory());
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("enableVirtualHosting", "false");
        usersRepository.configure(configuration);

        mailboxManager = JpaMailboxManagerProvider.provideMailboxManager(JPA_TEST_CLUSTER, postgresExtension);
        sessionProvider = mailboxManager.getSessionProvider();
        currentQuotaManager = new JpaCurrentQuotaManager(entityManagerFactory);

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

    @AfterEach
    void tearDownJpa() {
        JPA_TEST_CLUSTER.clear(ImmutableList.<String>builder()
            .addAll(JPAMailboxFixture.MAILBOX_TABLE_NAMES)
            .addAll(JPAMailboxFixture.QUOTA_TABLES_NAMES)
            .add("JAMES_USER")
            .add("JAMES_DOMAIN")
            .build());
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
