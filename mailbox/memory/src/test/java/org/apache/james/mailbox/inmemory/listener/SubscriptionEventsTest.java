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

package org.apache.james.mailbox.inmemory.listener;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.events.MailboxEvents.MailboxSubscribedEvent;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.util.EventCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubscriptionEventsTest {
    private static final Username ALICE = Username.of("alice");
    private static final Username BOB = Username.of("bob");
    private static final MailboxPath ALICE_SHARED = MailboxPath.forUser(ALICE, "shared");

    private MailboxManager mailboxManager;
    private StoreSubscriptionManager subscriptionManager;
    private EventCollector listener;
    private MailboxSession aliceSession;
    private MailboxSession bobSession;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        subscriptionManager = new StoreSubscriptionManager(resources.getMailboxManager().getMapperFactory(), resources.getMailboxManager().getMapperFactory(),
            resources.getEventBus());
        listener = new EventCollector();
        resources.getEventBus().register(listener);
        aliceSession = mailboxManager.createSystemSession(ALICE);
        bobSession = mailboxManager.createSystemSession(BOB);
        mailboxManager.createMailbox(ALICE_SHARED, aliceSession);
    }

    @Test
    void subscribeShouldNotDispatchAnEventForAMailboxNotVisibleToTheUser() throws Exception {
        subscriptionManager.subscribe(bobSession, ALICE_SHARED);

        assertThat(listener.getEvents()).noneMatch(MailboxSubscribedEvent.class::isInstance);
        assertThat(subscriptionManager.subscriptions(bobSession)).containsOnly(ALICE_SHARED);
    }

    @Test
    void subscribeShouldDispatchAnEventForAMailboxSharedWithLookup() throws Exception {
        mailboxManager.applyRightsCommand(ALICE_SHARED, MailboxACL.command().forUser(BOB).rights(MailboxACL.Right.Lookup).asAddition(), aliceSession);

        subscriptionManager.subscribe(bobSession, ALICE_SHARED);

        assertThat(listener.getEvents()).anyMatch(MailboxSubscribedEvent.class::isInstance);
    }

    @Test
    void subscribeShouldDispatchAnEventForAMailboxAnyoneCanLookUp() throws Exception {
        mailboxManager.applyRightsCommand(ALICE_SHARED, MailboxACL.command().key(MailboxACL.ANYONE_KEY)
            .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read).asAddition(), aliceSession);

        subscriptionManager.subscribe(bobSession, ALICE_SHARED);

        assertThat(listener.getEvents()).anyMatch(MailboxSubscribedEvent.class::isInstance);
    }
}
