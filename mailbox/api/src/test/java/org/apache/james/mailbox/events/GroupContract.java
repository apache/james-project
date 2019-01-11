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

package org.apache.james.mailbox.events;

import static org.apache.james.mailbox.events.EventBusTestFixture.EVENT;
import static org.apache.james.mailbox.events.EventBusTestFixture.EVENT_2;
import static org.apache.james.mailbox.events.EventBusTestFixture.FIVE_HUNDRED_MS;
import static org.apache.james.mailbox.events.EventBusTestFixture.GroupA;
import static org.apache.james.mailbox.events.EventBusTestFixture.GroupB;
import static org.apache.james.mailbox.events.EventBusTestFixture.NO_KEYS;
import static org.apache.james.mailbox.events.EventBusTestFixture.ONE_SECOND;
import static org.apache.james.mailbox.events.EventBusTestFixture.WAIT_CONDITION;
import static org.apache.james.mailbox.events.EventBusTestFixture.newListener;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.james.core.User;
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;

public interface GroupContract {

    interface SingleEventBusGroupContract extends EventBusContract {

        @Test
        default void listenerGroupShouldReceiveEvents() {
            MailboxListener listener = newListener();

            eventBus().register(listener, new GroupA());

            eventBus().dispatch(EVENT, NO_KEYS).block();

            verify(listener, timeout(ONE_SECOND).times(1)).event(any());
        }

        @Test
        default void groupListenersShouldNotReceiveNoopEvents() {
            MailboxListener listener = newListener();

            eventBus().register(listener, new GroupA());

            MailboxListener.Added noopEvent = new MailboxListener.Added(MailboxSession.SessionId.of(18), User.fromUsername("bob"), MailboxPath.forUser("bob", "mailbox"), TestId.of(58), ImmutableSortedMap.of(), Event.EventId.random());
            eventBus().dispatch(noopEvent, NO_KEYS).block();

            verify(listener, after(FIVE_HUNDRED_MS).never())
                .event(any());
        }

        @Test
        default void dispatchShouldNotThrowWhenAGroupListenerFails() {
            MailboxListener listener = newListener();
            doThrow(new RuntimeException()).when(listener).event(any());

            eventBus().register(listener, new GroupA());

            assertThatCode(() -> eventBus().dispatch(EVENT, NO_KEYS).block())
                .doesNotThrowAnyException();
        }

        @Test
        default void eachListenerGroupShouldReceiveEvents() {
            MailboxListener listener = newListener();
            MailboxListener listener2 = newListener();
            eventBus().register(listener, new GroupA());
            eventBus().register(listener2, new GroupB());

            eventBus().dispatch(EVENT, NO_KEYS).block();

            verify(listener, timeout(ONE_SECOND).times(1)).event(any());
            verify(listener2, timeout(ONE_SECOND).times(1)).event(any());
        }

        @Test
        default void unregisteredGroupListenerShouldNotReceiveEvents() {
            MailboxListener listener = newListener();
            Registration registration = eventBus().register(listener, new GroupA());

            registration.unregister();

            eventBus().dispatch(EVENT, NO_KEYS).block();
            verify(listener, after(FIVE_HUNDRED_MS).never())
                .event(any());
        }

        @Test
        default void registerShouldThrowWhenAGroupIsAlreadyUsed() {
            MailboxListener listener = newListener();
            MailboxListener listener2 = newListener();

            eventBus().register(listener, new GroupA());

            assertThatThrownBy(() -> eventBus().register(listener2, new GroupA()))
                .isInstanceOf(GroupAlreadyRegistered.class);
        }

        @Test
        default void registerShouldNotThrowOnAnUnregisteredGroup() {
            MailboxListener listener = newListener();
            MailboxListener listener2 = newListener();

            eventBus().register(listener, new GroupA()).unregister();

            assertThatCode(() -> eventBus().register(listener2, new GroupA()))
                .doesNotThrowAnyException();
        }

        @Test
        default void unregisterShouldBeIdempotentForGroups() {
            MailboxListener listener = newListener();

            Registration registration = eventBus().register(listener, new GroupA());
            registration.unregister();

            assertThatCode(registration::unregister)
                .doesNotThrowAnyException();
        }

        @Test
        default void registerShouldAcceptAlreadyUnregisteredGroups() {
            MailboxListener listener = newListener();

            eventBus().register(listener, new GroupA()).unregister();
            eventBus().register(listener, new GroupA());

            eventBus().dispatch(EVENT, NO_KEYS).block();

            verify(listener, timeout(ONE_SECOND).times(1)).event(any());
        }

        @Test
        default void dispatchShouldCallSynchronousListener() {
            MailboxListener listener = newListener();

            eventBus().register(listener, new GroupA());

            eventBus().dispatch(EVENT, NO_KEYS).block();

            verify(listener, timeout(ONE_SECOND).times(1)).event(any());
        }

        @Test
        default void failingGroupListenersShouldNotAbortGroupDelivery() {
            EventBusTestFixture.EventMatcherThrowingListener listener = new EventBusTestFixture.EventMatcherThrowingListener(ImmutableSet.of(EVENT));
            eventBus().register(listener, new GroupA());

            eventBus().dispatch(EVENT, NO_KEYS).block();
            eventBus().dispatch(EVENT_2, NO_KEYS).block();

            WAIT_CONDITION
                .until(() -> assertThat(listener.numberOfEventCalls()).isEqualTo(1));
        }

        @Test
        default void allGroupListenersShouldBeExecutedWhenAGroupListenerFails() {
            MailboxListener listener = newListener();

            MailboxListener failingListener = mock(MailboxListener.class);
            when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.SYNCHRONOUS);
            doThrow(new RuntimeException()).when(failingListener).event(any());

            eventBus().register(failingListener, new GroupA());
            eventBus().register(listener, new GroupB());

            eventBus().dispatch(EVENT, NO_KEYS).block();

            verify(listener, timeout(ONE_SECOND).times(1)).event(any());
        }
    }

    interface MultipleEventBusGroupContract extends EventBusContract.MultipleEventBusContract {

        @Test
        default void groupsDefinedOnlyOnSomeNodesShouldBeNotified() {
            MailboxListener mailboxListener = newListener();

            eventBus().register(mailboxListener, new GroupA());

            eventBus2().dispatch(EVENT, NO_KEYS).block();

            verify(mailboxListener, timeout(ONE_SECOND).times(1)).event(any());
        }

        @Test
        default void groupListenersShouldBeExecutedOnceInAControlledEnvironment() {
            MailboxListener mailboxListener = newListener();

            eventBus().register(mailboxListener, new GroupA());
            eventBus2().register(mailboxListener, new GroupA());

            eventBus2().dispatch(EVENT, NO_KEYS).block();

            verify(mailboxListener, timeout(ONE_SECOND).times(1)).event(any());
        }

        @Test
        default void unregisterShouldStopNotificationForDistantGroups() {
            MailboxListener mailboxListener = newListener();

            eventBus().register(mailboxListener, new GroupA()).unregister();

            eventBus2().dispatch(EVENT, NO_KEYS).block();


            verify(mailboxListener, after(FIVE_HUNDRED_MS).never())
                .event(any());
        }
    }
}
