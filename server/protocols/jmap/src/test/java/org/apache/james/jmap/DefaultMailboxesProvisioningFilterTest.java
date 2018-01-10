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
package org.apache.james.jmap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.Before;
import org.junit.Test;

import com.github.steveash.guavate.Guavate;

public class DefaultMailboxesProvisioningFilterTest {

    public static final String USERNAME = "username";
    private DefaultMailboxesProvisioningFilter testee;
    private MailboxSession session;
    private InMemoryMailboxManager mailboxManager;
    private StoreSubscriptionManager subscriptionManager;

    @Before
    public void before() throws Exception {
        session = new MockMailboxSession(USERNAME);

        InMemoryIntegrationResources inMemoryIntegrationResources = new InMemoryIntegrationResources();
        mailboxManager = inMemoryIntegrationResources.createMailboxManager(new SimpleGroupMembershipResolver());
        subscriptionManager = new StoreSubscriptionManager(mailboxManager.getMapperFactory());
        testee = new DefaultMailboxesProvisioningFilter(mailboxManager, subscriptionManager, new NoopMetricFactory());
    }

    @Test
    public void createMailboxesIfNeededShouldCreateSystemMailboxes() throws Exception {
        testee.createMailboxesIfNeeded(session);

        assertThat(mailboxManager.list(session))
            .containsOnlyElementsOf(DefaultMailboxes.DEFAULT_MAILBOXES
                .stream()
                .map(mailboxName -> MailboxPath.forUser(USERNAME, mailboxName))
                .collect(Guavate.toImmutableList()));
    }

    @Test
    public void createMailboxesIfNeededShouldSubscribeMailboxes() throws Exception {
        testee.createMailboxesIfNeeded(session);

        assertThat(subscriptionManager.subscriptions(session))
            .containsOnlyElementsOf(DefaultMailboxes.DEFAULT_MAILBOXES);
    }

    @Test
    public void createMailboxesIfNeededShouldNotGenerateExceptionsInConcurrentEnvironment() throws Exception {
        int threadCount = 10;
        int operationCount = 1;
        new ConcurrentTestRunner(threadCount, operationCount,
            (threadNumber, step) -> testee.createMailboxesIfNeeded(session))
            .run()
            .assertNoException()
            .awaitTermination(10, TimeUnit.SECONDS);

        assertThat(mailboxManager.list(session))
            .containsOnlyElementsOf(DefaultMailboxes.DEFAULT_MAILBOXES
                .stream()
                .map(mailboxName -> MailboxPath.forUser(USERNAME, mailboxName))
                .collect(Guavate.toImmutableList()));
    }

}

