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

package org.apache.james.mailbox.cassandra.mail.task;

import static org.apache.james.backends.cassandra.Scenario.Builder.fail;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.cassandra.CassandraDomainListModule;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.CassandraTestSystemFixture;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasService;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasService.RunningOptions;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasServiceContract;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.apache.james.sieve.cassandra.CassandraSieveRepositoryModule;
import org.apache.james.task.Task;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.cassandra.CassandraUsersDAO;
import org.apache.james.user.cassandra.CassandraUsersRepositoryModule;
import org.apache.james.user.lib.UsersRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

public class CassandraRecomputeCurrentQuotasServiceTest implements RecomputeCurrentQuotasServiceContract {
    static final DomainList NO_DOMAIN_LIST = null;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(
        MailboxAggregateModule.MODULE_WITH_QUOTA,
        CassandraDomainListModule.MODULE,
        CassandraSieveRepositoryModule.MODULE,
        CassandraUsersRepositoryModule.MODULE));

    UsersRepositoryImpl usersRepository;
    StoreMailboxManager mailboxManager;
    SessionProvider sessionProvider;
    CurrentQuotaManager currentQuotaManager;
    UserQuotaRootResolver userQuotaRootResolver;
    RecomputeCurrentQuotasService testee;

    @BeforeEach
    void setUp() {
        CassandraCluster cassandra = cassandraCluster.getCassandraCluster();
        CassandraMailboxSessionMapperFactory mapperFactory = CassandraTestSystemFixture.createMapperFactory(cassandra);

        CassandraUsersDAO usersDAO = new CassandraUsersDAO(cassandra.getConf());
        usersRepository = new UsersRepositoryImpl(NO_DOMAIN_LIST, usersDAO);
        usersRepository.setEnableVirtualHosting(false);

        mailboxManager = CassandraTestSystemFixture.createMailboxManager(mapperFactory);
        sessionProvider  = mailboxManager.getSessionProvider();
        currentQuotaManager = CassandraTestSystemFixture.createCurrentQuotaManager(cassandra);
        userQuotaRootResolver = new DefaultUserQuotaRootResolver(sessionProvider, mapperFactory);
        CurrentQuotaCalculator currentQuotaCalculator = new CurrentQuotaCalculator(mapperFactory, userQuotaRootResolver);

        testee = new RecomputeCurrentQuotasService(usersRepository, currentQuotaManager, currentQuotaCalculator, userQuotaRootResolver, sessionProvider, mailboxManager);
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

    @Disabled("JAMES-3926: new CassandraQuotaCurrentValueDao does not crash at failure on increase and decrease operations, thus we can't simulate the recompute task to fail by failing those cassandra queries")
    @Test
    void recomputeCurrentQuotasShouldReturnPartialWhenFailureAtReset() throws Exception {
        usersRepository().addUser(USER_1, PASSWORD);

        MailboxSession session = sessionProvider().createSystemSession(USER_1);
        mailboxManager().createMailbox(MAILBOX_PATH, session);

        MessageManager messageManager = mailboxManager().getMailbox(MAILBOX_PATH, session);
        appendAMessageForUser(messageManager, session);

        cassandraCluster.getCassandraCluster().getConf().registerScenario(fail()
            .times(1)
            .whenQueryStartsWith("UPDATE quotacurrentvalue SET"));

        assertThat(testee().recomputeCurrentQuotas(new RecomputeCurrentQuotasService.Context(), RunningOptions.DEFAULT).block())
            .isEqualTo(Task.Result.PARTIAL);
    }

    @Disabled("JAMES-3926: new CassandraQuotaCurrentValueDao does not crash at failure on increase and decrease operations, thus we can't simulate the recompute task to fail by failing those cassandra queries")
    @Test
    void recomputeCurrentQuotasShouldUpdateContextWhenFailureAtReset() throws Exception {
        usersRepository().addUser(USER_1, PASSWORD);

        MailboxSession session = sessionProvider().createSystemSession(USER_1);
        mailboxManager().createMailbox(MAILBOX_PATH, session);

        MessageManager messageManager = mailboxManager().getMailbox(MAILBOX_PATH, session);
        appendAMessageForUser(messageManager, session);

        cassandraCluster.getCassandraCluster().getConf().registerScenario(fail()
            .times(1)
            .whenQueryStartsWith("UPDATE quotacurrentvalue SET"));

        RecomputeCurrentQuotasService.Context context = new RecomputeCurrentQuotasService.Context();
        testee().recomputeCurrentQuotas(context, RunningOptions.DEFAULT).block();

        assertThat(context.snapshot())
            .isEqualTo(new RecomputeCurrentQuotasService.Context(0L,
                ImmutableList.of(userQuotaRootResolver.forUser(USER_1))).snapshot());
    }
}
