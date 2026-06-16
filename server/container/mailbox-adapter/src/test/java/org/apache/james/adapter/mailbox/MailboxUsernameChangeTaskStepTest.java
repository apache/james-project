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

import java.nio.charset.StandardCharsets;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mime4j.dom.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class MailboxUsernameChangeTaskStepTest {
    private static final Username ALICE = Username.of("alice");
    private static final Username BOB = Username.of("bob");
    private static final Username CEDRIC = Username.of("cedric");

    private InMemoryMailboxManager mailboxManager;
    private StoreSubscriptionManager subscriptionManager;
    private MailboxUsernameChangeTaskStep testee;

    @BeforeEach
    void setUp() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        subscriptionManager = new StoreSubscriptionManager(resources.getMailboxManager().getMapperFactory(), resources.getMailboxManager().getMapperFactory(),
            resources.getEventBus());
        testee = new MailboxUsernameChangeTaskStep(mailboxManager, subscriptionManager);
    }

    @Test
    void shouldMigrateMailboxes() throws Exception {
        MailboxSession fromSession = mailboxManager.createSystemSession(ALICE);
        mailboxManager.createMailbox(MailboxPath.inbox(ALICE), MailboxManager.CreateOption.NONE, fromSession);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test"), MailboxManager.CreateOption.NONE, fromSession);

        Mono.from(testee.changeUsername(ALICE, BOB)).block();

        assertThat(mailboxManager.list(mailboxManager.createSystemSession(BOB)))
            .containsOnly(MailboxPath.inbox(BOB),
                MailboxPath.forUser(BOB, "test"));
    }

    @Test
    void shouldMigrateACLsForOtherUsers() throws Exception {
        MailboxSession fromSession = mailboxManager.createSystemSession(ALICE);
        mailboxManager.createMailbox(MailboxPath.inbox(ALICE), MailboxManager.CreateOption.NONE, fromSession);
        mailboxManager.applyRightsCommand(MailboxPath.inbox(ALICE),
            MailboxACL.command().forUser(CEDRIC).rights(MailboxACL.FULL_RIGHTS).asAddition(),
            fromSession);

        Mono.from(testee.changeUsername(ALICE, BOB)).block();

        MailboxSession cedricSession = mailboxManager.createSystemSession(CEDRIC);
        MailboxQuery allMailboxes = MailboxQuery.builder().matchesAllMailboxNames().build();
        // Cedric sees the migrated mailbox
        assertThat(mailboxManager.search(allMailboxes, Minimal, cedricSession)
            .map(MailboxMetaData::getPath)
            .collectList()
            .block())
            .containsOnly(MailboxPath.inbox(BOB));
        // Cedric can access the migrated mailbox
        assertThatCode(() -> mailboxManager.getMailbox(MailboxPath.inbox(BOB), cedricSession))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldSubscriptionsForDelegatedUsers() throws Exception {
        MailboxSession fromSession = mailboxManager.createSystemSession(ALICE);
        mailboxManager.createMailbox(MailboxPath.inbox(ALICE), MailboxManager.CreateOption.NONE, fromSession);
        mailboxManager.applyRightsCommand(MailboxPath.inbox(ALICE),
            MailboxACL.command().forUser(CEDRIC).rights(MailboxACL.FULL_RIGHTS).asAddition(),
            fromSession);

        MailboxSession cedricSession = mailboxManager.createSystemSession(CEDRIC);
        subscriptionManager.subscribe(cedricSession, MailboxPath.inbox(ALICE));

        Mono.from(testee.changeUsername(ALICE, BOB)).block();


        assertThat(subscriptionManager.subscriptions(cedricSession))
            .containsOnly(MailboxPath.inbox(BOB));
    }

    @Test
    void shouldMigrateSubMailboxes() throws Exception {
        MailboxSession fromSession = mailboxManager.createSystemSession(ALICE);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test"), MailboxManager.CreateOption.NONE, fromSession);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test.child"), MailboxManager.CreateOption.NONE, fromSession);

        Mono.from(testee.changeUsername(ALICE, BOB)).block();

        assertThat(mailboxManager.list(mailboxManager.createSystemSession(BOB)))
            .containsOnly(MailboxPath.forUser(BOB, "test"),
                MailboxPath.forUser(BOB, "test.child"));
    }

    @Test
    void shouldRemoveSubscriptionForOldUser() throws Exception {
        MailboxSession fromSession = mailboxManager.createSystemSession(ALICE);
        mailboxManager.createMailbox(MailboxPath.inbox(ALICE), MailboxManager.CreateOption.NONE, fromSession);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "subscribed"), MailboxManager.CreateOption.CREATE_SUBSCRIPTION, fromSession);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "unsubscribed"), MailboxManager.CreateOption.NONE, fromSession);

        Mono.from(testee.changeUsername(ALICE, BOB)).block();

        assertThat(subscriptionManager.subscriptions(fromSession)).isEmpty();
    }

    @Test
    void shouldTransferSubscriptionToNewUser() throws Exception {
        MailboxSession fromSession = mailboxManager.createSystemSession(ALICE);
        mailboxManager.createMailbox(MailboxPath.inbox(ALICE), MailboxManager.CreateOption.NONE, fromSession);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "subscribed"), MailboxManager.CreateOption.CREATE_SUBSCRIPTION, fromSession);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "unsubscribed"), MailboxManager.CreateOption.NONE, fromSession);

        Mono.from(testee.changeUsername(ALICE, BOB)).block();

        assertThat(subscriptionManager.subscriptions(mailboxManager.createSystemSession(BOB)))
            .containsOnly(MailboxPath.forUser(BOB, "subscribed"));
    }

    @Test
    void shouldMigrateMailboxesWhenNewUserHasAlreadyMailbox() throws Exception {
        MailboxSession aliceSession = mailboxManager.createSystemSession(ALICE);
        MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
        MailboxPath aliceInbox = MailboxPath.inbox(ALICE);
        MailboxPath bobInbox = MailboxPath.inbox(BOB);
        mailboxManager.createMailbox(aliceInbox, MailboxManager.CreateOption.NONE, aliceSession);
        mailboxManager.createMailbox(bobInbox, MailboxManager.CreateOption.NONE, bobSession);

        mailboxManager.getMailbox(aliceInbox, aliceSession)
            .appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
                .setSubject("toto")
                .setBody("alice33333", StandardCharsets.UTF_8)
                .build()), aliceSession);

        mailboxManager.getMailbox(bobInbox, bobSession)
            .appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
                .setSubject("toto")
                .setBody("bob", StandardCharsets.UTF_8)
                .build()), bobSession);

        Mono.from(testee.changeUsername(ALICE, BOB)).block();

        assertThat(mailboxManager.getMailbox(bobInbox, bobSession).getMessageCount(bobSession))
            .isEqualTo(2);

        assertThat(mailboxManager.list(bobSession)).containsOnly(bobInbox);
    }

    @Test
    void shouldMigrateMailboxesWhenNewUserHasAlreadyOtherMailboxes() throws Exception {
        MailboxSession fromSession = mailboxManager.createSystemSession(ALICE);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test"), MailboxManager.CreateOption.NONE, fromSession);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test.child"), MailboxManager.CreateOption.NONE, fromSession);

        MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
        MailboxPath bobInbox = MailboxPath.inbox(BOB);
        mailboxManager.createMailbox(bobInbox, MailboxManager.CreateOption.NONE, bobSession);

        Mono.from(testee.changeUsername(ALICE, BOB)).block();

        assertThat(mailboxManager.list(mailboxManager.createSystemSession(BOB)))
            .containsOnly(MailboxPath.forUser(BOB, "test"),
                MailboxPath.forUser(BOB, "test.child"),
                bobInbox);
    }

    private void appendMessage(MailboxPath path, MailboxSession session, String body) throws Exception {
        mailboxManager.getMailbox(path, session)
            .appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
                .setSubject("subject")
                .setBody(body, StandardCharsets.UTF_8)
                .build()), session);
    }

    private long messageCount(MailboxPath path, MailboxSession session) throws Exception {
        return mailboxManager.getMailbox(path, session).getMessageCount(session);
    }

    @Nested
    class WhenDestinationParentMailboxAlreadyExists {
        @Test
        void shouldMigrateSubMailboxesWhenDestinationParentAlreadyExists() throws Exception {
            MailboxSession aliceSession = mailboxManager.createSystemSession(ALICE);
            MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test.child"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(BOB, "test"), MailboxManager.CreateOption.NONE, bobSession);

            Mono.from(testee.changeUsername(ALICE, BOB)).block();

            assertThat(mailboxManager.list(mailboxManager.createSystemSession(BOB)))
                .containsOnly(MailboxPath.forUser(BOB, "test"),
                    MailboxPath.forUser(BOB, "test.child"));
        }

        @Test
        void shouldNotLeaveTemporaryMailboxesWhenDestinationParentAlreadyExists() throws Exception {
            MailboxSession aliceSession = mailboxManager.createSystemSession(ALICE);
            MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test.child"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(BOB, "test"), MailboxManager.CreateOption.NONE, bobSession);

            Mono.from(testee.changeUsername(ALICE, BOB)).block();

            assertThat(mailboxManager.list(mailboxManager.createSystemSession(BOB)))
                .noneMatch(path -> path.getName().contains("-tmp-"));
        }

        @Test
        void shouldNotLooseMessagesOfSourceOnlyTopLevelMailboxWhenDestinationParentAlreadyExists() throws Exception {
            MailboxSession aliceSession = mailboxManager.createSystemSession(ALICE);
            MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(BOB, "test"), MailboxManager.CreateOption.NONE, bobSession);
            appendMessage(MailboxPath.forUser(ALICE, "test"), aliceSession, "alice");
            appendMessage(MailboxPath.forUser(BOB, "test"), bobSession, "bob");

            Mono.from(testee.changeUsername(ALICE, BOB)).block();

            assertThat(messageCount(MailboxPath.forUser(BOB, "test"), bobSession)).isEqualTo(2);
        }

        @Test
        void shouldMoveSubMailboxMessagesWhenDestinationParentAlreadyExists() throws Exception {
            MailboxSession aliceSession = mailboxManager.createSystemSession(ALICE);
            MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test.child"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(BOB, "test"), MailboxManager.CreateOption.NONE, bobSession);
            appendMessage(MailboxPath.forUser(ALICE, "test.child"), aliceSession, "alice-child");

            Mono.from(testee.changeUsername(ALICE, BOB)).block();

            assertThat(messageCount(MailboxPath.forUser(BOB, "test.child"), bobSession)).isEqualTo(1);
        }

        @Test
        void shouldMergeSubMailboxMessagesWhenBothUsersHaveTheSameSubMailbox() throws Exception {
            MailboxSession aliceSession = mailboxManager.createSystemSession(ALICE);
            MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test.child"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(BOB, "test"), MailboxManager.CreateOption.NONE, bobSession);
            mailboxManager.createMailbox(MailboxPath.forUser(BOB, "test.child"), MailboxManager.CreateOption.NONE, bobSession);
            appendMessage(MailboxPath.forUser(ALICE, "test"), aliceSession, "alice-parent");
            appendMessage(MailboxPath.forUser(ALICE, "test.child"), aliceSession, "alice-child");
            appendMessage(MailboxPath.forUser(BOB, "test"), bobSession, "bob-parent");
            appendMessage(MailboxPath.forUser(BOB, "test.child"), bobSession, "bob-child");

            Mono.from(testee.changeUsername(ALICE, BOB)).block();

            assertThat(mailboxManager.list(mailboxManager.createSystemSession(BOB)))
                .containsOnly(MailboxPath.forUser(BOB, "test"),
                    MailboxPath.forUser(BOB, "test.child"));
            assertThat(messageCount(MailboxPath.forUser(BOB, "test"), bobSession)).isEqualTo(2);
            assertThat(messageCount(MailboxPath.forUser(BOB, "test.child"), bobSession)).isEqualTo(2);
        }

        @Test
        void shouldMigrateDeepHierarchyWhenDestinationParentAlreadyExists() throws Exception {
            MailboxSession aliceSession = mailboxManager.createSystemSession(ALICE);
            MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test.child"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test.child.grandchild"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(BOB, "test"), MailboxManager.CreateOption.NONE, bobSession);

            Mono.from(testee.changeUsername(ALICE, BOB)).block();

            assertThat(mailboxManager.list(mailboxManager.createSystemSession(BOB)))
                .containsOnly(MailboxPath.forUser(BOB, "test"),
                    MailboxPath.forUser(BOB, "test.child"),
                    MailboxPath.forUser(BOB, "test.child.grandchild"));
        }

        @Test
        void shouldMigrateSubMailboxesWhenOnlyDeepestChildConflicts() throws Exception {
            MailboxSession aliceSession = mailboxManager.createSystemSession(ALICE);
            MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test.child"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test.other"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(BOB, "test"), MailboxManager.CreateOption.NONE, bobSession);
            mailboxManager.createMailbox(MailboxPath.forUser(BOB, "test.child"), MailboxManager.CreateOption.NONE, bobSession);

            Mono.from(testee.changeUsername(ALICE, BOB)).block();

            assertThat(mailboxManager.list(mailboxManager.createSystemSession(BOB)))
                .containsOnly(MailboxPath.forUser(BOB, "test"),
                    MailboxPath.forUser(BOB, "test.child"),
                    MailboxPath.forUser(BOB, "test.other"));
        }

        @Test
        void shouldRemoveSourceMailboxesWhenDestinationParentAlreadyExists() throws Exception {
            MailboxSession aliceSession = mailboxManager.createSystemSession(ALICE);
            MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test.child"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(BOB, "test"), MailboxManager.CreateOption.NONE, bobSession);

            Mono.from(testee.changeUsername(ALICE, BOB)).block();

            assertThat(mailboxManager.list(mailboxManager.createSystemSession(BOB)))
                .filteredOn(path -> ALICE.equals(path.getUser()))
                .isEmpty();
        }

        @Test
        void shouldTransferSubscriptionOfSubMailboxWhenDestinationParentAlreadyExists() throws Exception {
            MailboxSession aliceSession = mailboxManager.createSystemSession(ALICE);
            MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test.child"), MailboxManager.CreateOption.CREATE_SUBSCRIPTION, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(BOB, "test"), MailboxManager.CreateOption.NONE, bobSession);

            Mono.from(testee.changeUsername(ALICE, BOB)).block();

            assertThat(subscriptionManager.subscriptions(mailboxManager.createSystemSession(BOB)))
                .containsOnly(MailboxPath.forUser(BOB, "test.child"));
        }

        @Test
        void shouldMigrateDelegationAclOfSubMailboxWhenDestinationParentAlreadyExists() throws Exception {
            MailboxSession aliceSession = mailboxManager.createSystemSession(ALICE);
            MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "test.child"), MailboxManager.CreateOption.NONE, aliceSession);
            mailboxManager.createMailbox(MailboxPath.forUser(BOB, "test"), MailboxManager.CreateOption.NONE, bobSession);
            mailboxManager.applyRightsCommand(MailboxPath.forUser(ALICE, "test.child"),
                MailboxACL.command().forUser(CEDRIC).rights(MailboxACL.FULL_RIGHTS).asAddition(),
                aliceSession);

            Mono.from(testee.changeUsername(ALICE, BOB)).block();

            MailboxSession cedricSession = mailboxManager.createSystemSession(CEDRIC);
            assertThatCode(() -> mailboxManager.getMailbox(MailboxPath.forUser(BOB, "test.child"), cedricSession))
                .doesNotThrowAnyException();
        }
    }
}