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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class MailboxUsernameChangeTaskStepTest {
    private static final Username ALICE = Username.of("alice");
    private static final Username BOB = Username.of("bob");

    private InMemoryMailboxManager mailboxManager;
    private StoreSubscriptionManager storeSubscriptionManager;
    private MailboxUsernameChangeTaskStep testee;

    @BeforeEach
    void setUp() {
        final InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        storeSubscriptionManager = new StoreSubscriptionManager(resources.getMailboxManager().getMapperFactory(), resources.getMailboxManager().getMapperFactory(),
            resources.getEventBus());
        testee = new MailboxUsernameChangeTaskStep(mailboxManager);
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

        assertThat(storeSubscriptionManager.subscriptions(fromSession)).isEmpty();
    }

    @Test
    void shouldTransferSubscriptionToNewUser() throws Exception {
        MailboxSession fromSession = mailboxManager.createSystemSession(ALICE);
        mailboxManager.createMailbox(MailboxPath.inbox(ALICE), MailboxManager.CreateOption.NONE, fromSession);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "subscribed"), MailboxManager.CreateOption.CREATE_SUBSCRIPTION, fromSession);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "unsubscribed"), MailboxManager.CreateOption.NONE, fromSession);

        Mono.from(testee.changeUsername(ALICE, BOB)).block();

        assertThat(storeSubscriptionManager.subscriptions(mailboxManager.createSystemSession(BOB)))
            .containsOnly(MailboxPath.forUser(BOB, "subscribed"));
    }
}