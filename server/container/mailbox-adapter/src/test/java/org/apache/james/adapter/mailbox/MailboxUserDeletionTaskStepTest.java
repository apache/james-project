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
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class MailboxUserDeletionTaskStepTest {
    private static final Username ALICE = Username.of("alice");
    private static final Username BOB = Username.of("bob");

    private InMemoryMailboxManager mailboxManager;
    private StoreSubscriptionManager subscriptionManager;
    private MailboxUserDeletionTaskStep testee;

    @BeforeEach
    void setUp() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        subscriptionManager = new StoreSubscriptionManager(resources.getMailboxManager().getMapperFactory(), resources.getMailboxManager().getMapperFactory(),
            resources.getEventBus());
        testee = new MailboxUserDeletionTaskStep(mailboxManager, subscriptionManager);
    }

    @Test
    void shouldRemoveSubscriptionsOnMailboxes() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(ALICE);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "subscribed1"), MailboxManager.CreateOption.CREATE_SUBSCRIPTION, session);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "subscribed2"), MailboxManager.CreateOption.CREATE_SUBSCRIPTION, session);

        Mono.from(testee.deleteUserData(ALICE)).block();

        assertThat(subscriptionManager.subscriptions(session)).isEmpty();
    }

    @Test
    void shouldRemoveSubscriptionsOnSubMailboxes() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(ALICE);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "parent"), MailboxManager.CreateOption.CREATE_SUBSCRIPTION, session);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "parent.child1"), MailboxManager.CreateOption.CREATE_SUBSCRIPTION, session);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "parent.child2"), MailboxManager.CreateOption.CREATE_SUBSCRIPTION, session);

        Mono.from(testee.deleteUserData(ALICE)).block();

        assertThat(subscriptionManager.subscriptions(session)).isEmpty();
    }

    @Test
    void shouldRemoveSubscriptionsOnSharedMailboxes() throws Exception {
        // BOB shares his Inbox access to ALICE
        MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
        mailboxManager.createMailbox(MailboxPath.inbox(BOB), MailboxManager.CreateOption.NONE, bobSession);
        mailboxManager.applyRightsCommand(MailboxPath.inbox(BOB),
            MailboxACL.command().forUser(ALICE).rights(MailboxACL.FULL_RIGHTS).asAddition(),
            bobSession);

        // ALICE subscribes to BOB Inbox
        subscriptionManager.subscribe(mailboxManager.createSystemSession(ALICE), MailboxPath.inbox(BOB));

        Mono.from(testee.deleteUserData(ALICE)).block();

        // ALICE subscription on the shared mailbox should be deleted
        assertThat(subscriptionManager.subscriptions(mailboxManager.createSystemSession(ALICE)))
            .isEmpty();
    }

    @Test
    void shouldRemoveMailboxes() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(ALICE);
        mailboxManager.createMailbox(MailboxPath.inbox(ALICE), MailboxManager.CreateOption.NONE, session);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test"), MailboxManager.CreateOption.NONE, session);

        Mono.from(testee.deleteUserData(ALICE)).block();

        assertThat(mailboxManager.list(session))
            .isEmpty();
    }

    @Test
    void shouldRemoveMessagesInMailboxes() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(ALICE);
        mailboxManager.createMailbox(MailboxPath.inbox(ALICE), MailboxManager.CreateOption.NONE, session);
        mailboxManager.getMailbox(MailboxPath.inbox(ALICE), session)
            .appendMessage(MessageManager.AppendCommand.builder()
                .build("message content"), session);

        Mono.from(testee.deleteUserData(ALICE)).block();

        assertThat(mailboxManager.search(MultimailboxesSearchQuery.from(SearchQuery.matchAll()).build(), session, 100L)
            .collectList().block())
            .isEmpty();
    }

    @Test
    void shouldRemoveSubMailboxes() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(ALICE);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "parent"), MailboxManager.CreateOption.NONE, session);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "parent.child1"), MailboxManager.CreateOption.NONE, session);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "parent.child2"), MailboxManager.CreateOption.NONE, session);

        Mono.from(testee.deleteUserData(ALICE)).block();

        assertThat(mailboxManager.list(session))
            .isEmpty();
    }

    @Test
    void shouldRemoveMessagesInSubMailboxes() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(ALICE);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "parent.child1"), MailboxManager.CreateOption.NONE, session);
        mailboxManager.getMailbox(MailboxPath.forUser(ALICE, "parent.child1"), session)
            .appendMessage(MessageManager.AppendCommand.builder()
                .build("message content"), session);

        Mono.from(testee.deleteUserData(ALICE)).block();

        assertThat(mailboxManager.search(MultimailboxesSearchQuery.from(SearchQuery.matchAll()).build(), session, 100L)
            .collectList().block())
            .isEmpty();
    }

    @Test
    void shouldNotRemoveMailboxesOfOtherUsers() throws Exception {
        MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
        mailboxManager.createMailbox(MailboxPath.inbox(BOB), MailboxManager.CreateOption.NONE, bobSession);

        Mono.from(testee.deleteUserData(ALICE)).block();

        assertThat(mailboxManager.list(bobSession))
            .containsOnly(MailboxPath.inbox(BOB));
    }

    @Test
    void shouldNotRemoveSubscriptionsOnMailboxesOfOtherUsers() throws Exception {
        MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "subscribed"), MailboxManager.CreateOption.CREATE_SUBSCRIPTION, bobSession);

        Mono.from(testee.deleteUserData(ALICE)).block();

        assertThat(subscriptionManager.subscriptions(bobSession))
            .containsOnly(MailboxPath.forUser(BOB, "subscribed"));
    }

    @Test
    void shouldRevokeRightsOnSharedMailboxes() throws Exception {
        // BOB creates Inbox
        MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
        mailboxManager.createMailbox(MailboxPath.inbox(BOB), MailboxManager.CreateOption.NONE, bobSession);

        // BOB shares his Inbox access to ALICE
        mailboxManager.applyRightsCommand(MailboxPath.inbox(BOB),
            MailboxACL.command().forUser(ALICE).rights(MailboxACL.FULL_RIGHTS).asAddition(),
            bobSession);

        Mono.from(testee.deleteUserData(ALICE)).block();

        // Alice ACL on the shared mailbox should be no longer existed
        MailboxSession aliceSession = mailboxManager.createSystemSession(ALICE);

        assertThat(mailboxManager.search(MailboxQuery.builder().matchesAllMailboxNames().build(), Minimal, aliceSession)
            .map(MailboxMetaData::getPath)
            .collectList()
            .block())
            .isEmpty();
        assertThat(mailboxManager.hasRight(MailboxPath.inbox(BOB), MailboxACL.Right.Read, aliceSession))
            .isFalse();
    }

    @Test
    void shouldBeIdempotent() {
        Mono.from(testee.deleteUserData(ALICE)).block();

        assertThatCode(() -> Mono.from(testee.deleteUserData(ALICE)).block())
            .doesNotThrowAnyException();
    }
}
