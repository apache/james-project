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

package org.apache.james.adapter.mailbox;

import static org.apache.james.mailbox.MailboxManager.MailboxSearchFetchType.Minimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class ACLUsernameChangeTaskStepTest {
    private static final Username ALICE = Username.of("alice");
    private static final Username BOB = Username.of("bob");
    private static final Username CEDRIC = Username.of("cedric");

    private InMemoryMailboxManager mailboxManager;
    private ACLUsernameChangeTaskStep testee;
    private StoreSubscriptionManager subscriptionManager;

    @BeforeEach
    void setUp() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        subscriptionManager = new StoreSubscriptionManager(resources.getMailboxManager().getMapperFactory(), resources.getMailboxManager().getMapperFactory(),
            resources.getEventBus());
        testee = new ACLUsernameChangeTaskStep(mailboxManager, subscriptionManager);
    }

    @Test
    void shouldMigrateACLs() throws Exception {
        MailboxSession cedricSession = mailboxManager.createSystemSession(CEDRIC);
        mailboxManager.createMailbox(MailboxPath.inbox(CEDRIC), MailboxManager.CreateOption.NONE, cedricSession);
        mailboxManager.applyRightsCommand(MailboxPath.inbox(CEDRIC),
            MailboxACL.command().forUser(ALICE).rights(MailboxACL.FULL_RIGHTS).asAddition(),
            cedricSession);

        Mono.from(testee.changeUsername(ALICE, BOB)).block();

        MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
        MailboxQuery allMailboxes = MailboxQuery.builder().matchesAllMailboxNames().build();
        // Bob sees the migrated mailbox
        assertThat(mailboxManager.search(allMailboxes, Minimal, bobSession)
            .map(MailboxMetaData::getPath)
            .collectList()
            .block())
            .containsOnly(MailboxPath.inbox(CEDRIC));
        // Bob can access the migrated mailbox
        assertThatCode(() -> mailboxManager.getMailbox(MailboxPath.inbox(CEDRIC), bobSession))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldMigrateSubscriptionsOnDelegatedUsers() throws Exception {
        MailboxSession cedricSession = mailboxManager.createSystemSession(CEDRIC);
        mailboxManager.createMailbox(MailboxPath.inbox(CEDRIC), MailboxManager.CreateOption.NONE, cedricSession);
        mailboxManager.applyRightsCommand(MailboxPath.inbox(CEDRIC),
            MailboxACL.command().forUser(ALICE).rights(MailboxACL.FULL_RIGHTS).asAddition(),
            cedricSession);
        subscriptionManager.subscribe(mailboxManager.createSystemSession(ALICE), MailboxPath.inbox(CEDRIC));

        Mono.from(testee.changeUsername(ALICE, BOB)).block();

        MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
        assertThat(subscriptionManager.subscriptions(bobSession)).containsOnly(MailboxPath.inbox(CEDRIC));
    }

    @Test
    void shouldDeleteDelegatedSubscriptionsForBaseUsers() throws Exception {
        MailboxSession cedricSession = mailboxManager.createSystemSession(CEDRIC);
        mailboxManager.createMailbox(MailboxPath.inbox(CEDRIC), MailboxManager.CreateOption.NONE, cedricSession);
        mailboxManager.applyRightsCommand(MailboxPath.inbox(CEDRIC),
            MailboxACL.command().forUser(ALICE).rights(MailboxACL.FULL_RIGHTS).asAddition(),
            cedricSession);
        subscriptionManager.subscribe(mailboxManager.createSystemSession(ALICE), MailboxPath.inbox(CEDRIC));

        Mono.from(testee.changeUsername(ALICE, BOB)).block();

        assertThat(subscriptionManager.subscriptions(mailboxManager.createSystemSession(ALICE))).isEmpty();
    }

    @Test
    void shouldDeleteRightsOnOriginalAccount() throws Exception {
        MailboxSession cedricSession = mailboxManager.createSystemSession(CEDRIC);
        mailboxManager.createMailbox(MailboxPath.inbox(CEDRIC), MailboxManager.CreateOption.NONE, cedricSession);
        mailboxManager.applyRightsCommand(MailboxPath.inbox(CEDRIC),
            MailboxACL.command().forUser(ALICE).rights(MailboxACL.FULL_RIGHTS).asAddition(),
            cedricSession);

        Mono.from(testee.changeUsername(ALICE, BOB)).block();

        MailboxSession aliceSession = mailboxManager.createSystemSession(ALICE);
        MailboxQuery allMailboxes = MailboxQuery.builder().matchesAllMailboxNames().build();
        // Alice no longer sees the migrated mailbox
        assertThat(mailboxManager.search(allMailboxes, Minimal, aliceSession)
            .map(MailboxMetaData::getPath)
            .collectList()
            .block())
            .isEmpty();
    }

    // todo Alice subscribes to smb else mailbox
}