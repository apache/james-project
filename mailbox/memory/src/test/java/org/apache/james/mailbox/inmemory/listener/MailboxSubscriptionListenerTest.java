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
import org.apache.james.events.Event;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.event.MailboxSubscriptionListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MailboxSubscriptionListenerTest {
    private static final Username BOB = Username.of("bob");
    private static final MailboxPath PARENT1 = new MailboxPath("namespace", BOB, "parent1");
    private static final MailboxPath PARENT1_CHILD1 = new MailboxPath("namespace", BOB, "parent1.child1");
    private static final MailboxPath PARENT1_CHILD1_CHILD1 = new MailboxPath("namespace", BOB, "parent1.child1.child1");
    private static final MailboxPath PARENT1_CHILD1_CHILD2 = new MailboxPath("namespace", BOB, "parent1.child1.child2");
    private static final TestId MAILBOX_ID = TestId.of(45);

    private SubscriptionManager subscriptionManager;
    private MailboxManager mailboxManager;
    private MailboxSession bobSession;
    private MailboxSubscriptionListener testee;

    @BeforeEach
    void setup() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        subscriptionManager = new StoreSubscriptionManager(resources.getMailboxManager().getMapperFactory(), resources.getMailboxManager().getMapperFactory(),
            resources.getEventBus());
        mailboxManager = resources.getMailboxManager();
        bobSession = mailboxManager.createSystemSession(BOB);

        testee = new MailboxSubscriptionListener(subscriptionManager, resources.getMailboxManager().getSessionProvider(), resources.getMailboxManager().getMapperFactory());
    }

    @Test
    void deserializeMailboxSubscriptionListenerGroupTest() throws Exception {
        assertThat(Group.deserialize("org.apache.james.mailbox.store.event.MailboxSubscriptionListener$MailboxSubscriptionListenerGroup"))
            .isEqualTo(new MailboxSubscriptionListener.MailboxSubscriptionListenerGroup());
    }

    @Test
    void shouldNotHandleNotMatchEvents() {
        MailboxEvents.MailboxEvent mailboxAddedEvent = new MailboxEvents.MailboxAdded(null, BOB, PARENT1, MAILBOX_ID, Event.EventId.random());
        testee.isHandling(mailboxAddedEvent);

        assertThat(testee.isHandling(mailboxAddedEvent)).isFalse();
    }

    @Test
    void shouldHandleMailboxSubscribedEvent() {
        MailboxEvents.MailboxSubscribedEvent mailboxSubscribedEvent = new MailboxEvents.MailboxSubscribedEvent(null, BOB, PARENT1, MAILBOX_ID, Event.EventId.random());
        testee.isHandling(mailboxSubscribedEvent);

        assertThat(testee.isHandling(mailboxSubscribedEvent)).isTrue();
    }

    @Test
    void shouldHandleMailboxUnsubscribedEvent() {
        MailboxEvents.MailboxUnsubscribedEvent mailboxUnsubscribedEvent = new MailboxEvents.MailboxUnsubscribedEvent(null, BOB, PARENT1, MAILBOX_ID, Event.EventId.random());
        testee.isHandling(mailboxUnsubscribedEvent);

        assertThat(testee.isHandling(mailboxUnsubscribedEvent)).isTrue();
    }

    @Test
    void shouldNotPropagateSubscriptionToNonParentMailboxes() throws Exception {
        MailboxEvents.MailboxSubscribedEvent parent1Child1Child1MailboxSubscribedEvent = new MailboxEvents.MailboxSubscribedEvent(null, BOB, PARENT1_CHILD1_CHILD1, MAILBOX_ID, Event.EventId.random());

        mailboxManager.createMailbox(PARENT1_CHILD1_CHILD1, bobSession);
        mailboxManager.createMailbox(PARENT1_CHILD1_CHILD2, bobSession);

        subscriptionManager.subscribe(bobSession, PARENT1_CHILD1_CHILD1);
        testee.event(parent1Child1Child1MailboxSubscribedEvent);

        assertThat(subscriptionManager.subscriptions(bobSession)).doesNotContain(PARENT1_CHILD1_CHILD2);
    }

    @Test
    void shouldPropagateSubscriptionToParentMailboxesWhenMailboxHasMultipleParentMailboxes() throws Exception {
        MailboxEvents.MailboxSubscribedEvent parent1Child1Child1MailboxSubscribedEvent = new MailboxEvents.MailboxSubscribedEvent(null, BOB, PARENT1_CHILD1_CHILD1, MAILBOX_ID, Event.EventId.random());

        mailboxManager.createMailbox(PARENT1, bobSession);
        mailboxManager.createMailbox(PARENT1_CHILD1, bobSession);
        mailboxManager.createMailbox(PARENT1_CHILD1_CHILD1, bobSession);

        subscriptionManager.subscribe(bobSession, PARENT1_CHILD1_CHILD1);
        testee.event(parent1Child1Child1MailboxSubscribedEvent);

        assertThat(subscriptionManager.subscriptions(bobSession)).containsExactlyInAnyOrder(PARENT1_CHILD1_CHILD1, PARENT1_CHILD1, PARENT1);
    }

    @Test
    void shouldNotPropagateUnsubscriptionToNonChildrenMailboxes() throws Exception {
        MailboxEvents.MailboxUnsubscribedEvent parent1Child1Child1MailboxUnsubscribedEvent = new MailboxEvents.MailboxUnsubscribedEvent(null, BOB, PARENT1_CHILD1_CHILD1, MAILBOX_ID, Event.EventId.random());

        mailboxManager.createMailbox(PARENT1_CHILD1_CHILD1, MailboxManager.CreateOption.CREATE_SUBSCRIPTION, bobSession);
        mailboxManager.createMailbox(PARENT1_CHILD1_CHILD2, MailboxManager.CreateOption.CREATE_SUBSCRIPTION, bobSession);

        subscriptionManager.unsubscribe(bobSession, PARENT1_CHILD1_CHILD1);
        testee.event(parent1Child1Child1MailboxUnsubscribedEvent);

        assertThat(subscriptionManager.subscriptions(bobSession)).containsOnly(PARENT1_CHILD1_CHILD2);
    }

    @Test
    void shouldPropagateUnsubscriptionToChildrenMailboxesWhenMailboxHasMultipleChildrenMailboxes() throws Exception {
        MailboxEvents.MailboxUnsubscribedEvent parent1MailboxUnsubscribedEvent = new MailboxEvents.MailboxUnsubscribedEvent(null, BOB, PARENT1, MAILBOX_ID, Event.EventId.random());

        mailboxManager.createMailbox(PARENT1, MailboxManager.CreateOption.CREATE_SUBSCRIPTION, bobSession);
        mailboxManager.createMailbox(PARENT1_CHILD1, MailboxManager.CreateOption.CREATE_SUBSCRIPTION, bobSession);
        mailboxManager.createMailbox(PARENT1_CHILD1_CHILD1, MailboxManager.CreateOption.CREATE_SUBSCRIPTION, bobSession);
        mailboxManager.createMailbox(PARENT1_CHILD1_CHILD2, MailboxManager.CreateOption.CREATE_SUBSCRIPTION, bobSession);

        subscriptionManager.unsubscribe(bobSession, PARENT1);
        testee.event(parent1MailboxUnsubscribedEvent);

        assertThat(subscriptionManager.subscriptions(bobSession)).isEmpty();
    }

}
