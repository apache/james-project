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
package org.apache.james.jmap.draft;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.apache.james.core.Username;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.Before;
import org.junit.Test;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;

public class DefaultMailboxesProvisioningFilterTest {

    public static final Username USERNAME = Username.of("username");
    private DefaultMailboxesProvisioningFilter testee;
    private MailboxSession session;
    private InMemoryMailboxManager mailboxManager;
    private StoreSubscriptionManager subscriptionManager;

    @Before
    public void before() throws Exception {
        session = MailboxSessionUtil.create(USERNAME);

        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        subscriptionManager = new StoreSubscriptionManager(mailboxManager.getMapperFactory());
        testee = new DefaultMailboxesProvisioningFilter(mailboxManager, subscriptionManager, new RecordingMetricFactory());
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
    public void createMailboxesIfNeededShouldCreateSpamWhenOtherSystemMailboxesExist() throws Exception {
        DefaultMailboxes.DEFAULT_MAILBOXES
            .stream()
            .filter(mailbox -> !DefaultMailboxes.SPAM.equals(mailbox))
            .forEach(Throwing.consumer(mailbox -> mailboxManager.createMailbox(MailboxPath.forUser(USERNAME, mailbox), session)));

        testee.createMailboxesIfNeeded(session);

        assertThat(mailboxManager.list(session)).contains(MailboxPath.forUser(USERNAME, DefaultMailboxes.SPAM));
    }

    @Test
    public void createMailboxesIfNeededShouldSubscribeMailboxes() throws Exception {
        testee.createMailboxesIfNeeded(session);

        assertThat(subscriptionManager.subscriptions(session))
            .containsOnlyElementsOf(DefaultMailboxes.DEFAULT_MAILBOXES);
    }

    @Test
    public void createMailboxesIfNeededShouldNotGenerateExceptionsInConcurrentEnvironment() throws Exception {
        ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> testee.createMailboxesIfNeeded(session))
            .threadCount(10)
            .runSuccessfullyWithin(Duration.ofSeconds(10));

        assertThat(mailboxManager.list(session))
            .containsOnlyElementsOf(DefaultMailboxes.DEFAULT_MAILBOXES
                .stream()
                .map(mailboxName -> MailboxPath.forUser(USERNAME, mailboxName))
                .collect(Guavate.toImmutableList()));
    }

}

