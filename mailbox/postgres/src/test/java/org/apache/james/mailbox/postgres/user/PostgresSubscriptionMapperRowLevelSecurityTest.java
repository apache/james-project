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

package org.apache.james.mailbox.postgres.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.utils.DomainImplPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.store.user.SubscriptionMapperFactory;
import org.apache.james.mailbox.store.user.model.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresSubscriptionMapperRowLevelSecurityTest {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(PostgresSubscriptionModule.MODULE);

    private SubscriptionMapperFactory subscriptionMapperFactory;

    @BeforeEach
    public void setUp() {
        PostgresExecutor.Factory executorFactory = new PostgresExecutor.Factory(new DomainImplPostgresConnectionFactory(postgresExtension.getConnectionFactory()),
            postgresExtension.getPostgresConfiguration());
        subscriptionMapperFactory = session -> new PostgresSubscriptionMapper(new PostgresSubscriptionDAO(executorFactory.create(session.getUser().getDomainPart())));
    }

    @Test
    void subscriptionsCanBeAccessedAtTheDataLevelByMembersOfTheSameDomain() throws Exception {
        Username username = Username.of("bob@domain1");
        Username username2 = Username.of("alice@domain1");
        MailboxSession session = MailboxSessionUtil.create(username);
        MailboxSession session2 = MailboxSessionUtil.create(username2);

        Subscription subscription = new Subscription(username, "mailbox1");
        subscriptionMapperFactory.getSubscriptionMapper(session)
            .save(subscription);

        assertThat(subscriptionMapperFactory.getSubscriptionMapper(session2)
            .findSubscriptionsForUser(username))
            .containsOnly(subscription);
    }

    @Test
    void subscriptionsShouldBeIsolatedByDomain() throws Exception {
        Username username = Username.of("bob@domain1");
        Username username2 = Username.of("alice@domain2");
        MailboxSession session = MailboxSessionUtil.create(username);
        MailboxSession session2 = MailboxSessionUtil.create(username2);

        Subscription subscription = new Subscription(username, "mailbox1");
        subscriptionMapperFactory.getSubscriptionMapper(session)
            .save(subscription);

        assertThat(subscriptionMapperFactory.getSubscriptionMapper(session2)
            .findSubscriptionsForUser(username))
            .isEmpty();
    }
}
