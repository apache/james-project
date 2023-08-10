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

package org.apache.james.mailbox.store.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class MailboxSubscriptionListenerTest {
    private static final Username BOB = Username.of("bob");
    private static final MailboxSession BOB_SESSION = MailboxSessionUtil.create(BOB);
    private static final MailboxPath PARENT1 = new MailboxPath("namespace", BOB, "parent1");
    private static final MailboxPath PARENT1_CHILD1 = new MailboxPath("namespace", BOB, "parent1.child1");
    private static final MailboxPath PARENT1_CHILD1_CHILD1 = new MailboxPath("namespace", BOB, "parent1.child1.child1");
    private static final MailboxPath PARENT1_CHILD1_CHILD2 = new MailboxPath("namespace", BOB, "parent1.child2");
    private static final TestId MAILBOX_ID = TestId.of(45);

    @Mock
    SessionProvider sessionProvider;

    @Mock
    SubscriptionManager subscriptionManager;

    @Mock
    MailboxMapper mailboxMapper;

    MailboxSubscriptionListener testee;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        when(subscriptionManager.subscribeReactive(any(), any()))
            .thenReturn(Mono.empty());
        when(subscriptionManager.unsubscribeReactive(any(), any()))
            .thenReturn(Mono.empty());

        testee = new MailboxSubscriptionListener(subscriptionManager, sessionProvider, mailboxMapper);
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

    @Nested
    class MailboxSubscribedEvent {
        @Test
        void shouldHandleMailboxSubscribedEvent() {
            MailboxEvents.MailboxSubscribedEvent mailboxSubscribedEvent = new MailboxEvents.MailboxSubscribedEvent(null, BOB, PARENT1, MAILBOX_ID, Event.EventId.random());
            testee.isHandling(mailboxSubscribedEvent);

            assertThat(testee.isHandling(mailboxSubscribedEvent)).isTrue();
        }

        @Test
        void shouldDoNothingWhenMailboxHasNoParentMailboxes() throws Exception {
            MailboxEvents.MailboxSubscribedEvent mailboxSubscribedEvent = new MailboxEvents.MailboxSubscribedEvent(null, BOB, PARENT1, MAILBOX_ID, Event.EventId.random());
            when(sessionProvider.createSystemSession(mailboxSubscribedEvent.getUsername()))
                .thenReturn(BOB_SESSION);

            testee.event(mailboxSubscribedEvent);

            verifyNoMoreInteractions(subscriptionManager);
        }

        @Test
        void shouldPropagateSubscriptionToParentMailboxesWhenMailboxHasMultipleParentMailboxes() throws Exception {
            MailboxEvents.MailboxSubscribedEvent mailboxSubscribedEvent = new MailboxEvents.MailboxSubscribedEvent(null, BOB, PARENT1_CHILD1_CHILD1, MAILBOX_ID, Event.EventId.random());
            when(sessionProvider.createSystemSession(mailboxSubscribedEvent.getUsername()))
                .thenReturn(BOB_SESSION);

            testee.event(mailboxSubscribedEvent);

            verify(subscriptionManager).subscribeReactive(PARENT1, BOB_SESSION);
            verify(subscriptionManager).subscribeReactive(PARENT1_CHILD1, BOB_SESSION);
            verifyNoMoreInteractions(subscriptionManager);
        }
    }

    @Nested
    class MailboxUnsubscribedEvent {
        @Test
        void shouldHandleMailboxUnsubscribedEvent() {
            MailboxEvents.MailboxUnsubscribedEvent mailboxUnsubscribedEvent = new MailboxEvents.MailboxUnsubscribedEvent(null, BOB, PARENT1, MAILBOX_ID, Event.EventId.random());
            testee.isHandling(mailboxUnsubscribedEvent);

            assertThat(testee.isHandling(mailboxUnsubscribedEvent)).isTrue();
        }

        @Test
        void shouldDoNothingWhenMailboxHasNoChildrenMailboxes() throws Exception {
            MailboxEvents.MailboxUnsubscribedEvent mailboxUnsubscribedEvent = new MailboxEvents.MailboxUnsubscribedEvent(null, BOB, PARENT1_CHILD1_CHILD1, MAILBOX_ID, Event.EventId.random());
            when(sessionProvider.createSystemSession(mailboxUnsubscribedEvent.getUsername()))
                .thenReturn(BOB_SESSION);
            when(mailboxMapper.findMailboxWithPathLike(any()))
                .thenReturn(Flux.empty());

            testee.event(mailboxUnsubscribedEvent);

            verifyNoMoreInteractions(subscriptionManager);
        }

        @Test
        void shouldPropagateUnsubscriptionToChildrenMailboxesWhenMailboxHasMultipleChildrenMailboxes() throws Exception {
            MailboxEvents.MailboxUnsubscribedEvent mailboxUnsubscribedEvent = new MailboxEvents.MailboxUnsubscribedEvent(null, BOB, PARENT1, MAILBOX_ID, Event.EventId.random());
            when(sessionProvider.createSystemSession(mailboxUnsubscribedEvent.getUsername()))
                .thenReturn(BOB_SESSION);

            Mailbox parent1Child1 = mock(Mailbox.class);
            Mailbox parent1Child1Child1 = mock(Mailbox.class);
            Mailbox parent1Child1Child2 = mock(Mailbox.class);
            when(parent1Child1.generateAssociatedPath()).thenReturn(PARENT1_CHILD1);
            when(parent1Child1Child1.generateAssociatedPath()).thenReturn(PARENT1_CHILD1_CHILD1);
            when(parent1Child1Child2.generateAssociatedPath()).thenReturn(PARENT1_CHILD1_CHILD2);
            when(mailboxMapper.findMailboxWithPathLike(any()))
                .thenReturn(Flux.just(parent1Child1, parent1Child1Child1, parent1Child1Child2));

            testee.event(mailboxUnsubscribedEvent);

            verify(subscriptionManager).unsubscribeReactive(PARENT1_CHILD1, BOB_SESSION);
            verify(subscriptionManager).unsubscribeReactive(PARENT1_CHILD1_CHILD1, BOB_SESSION);
            verify(subscriptionManager).unsubscribeReactive(PARENT1_CHILD1_CHILD2, BOB_SESSION);
            verifyNoMoreInteractions(subscriptionManager);
        }
    }

}
