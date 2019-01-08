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
import static org.apache.james.mailbox.events.EventBusTestFixture.FIVE_HUNDRED_MS;
import static org.apache.james.mailbox.events.EventBusTestFixture.KEY_1;
import static org.apache.james.mailbox.events.EventBusTestFixture.KEY_2;
import static org.apache.james.mailbox.events.EventBusTestFixture.NO_KEYS;
import static org.apache.james.mailbox.events.EventBusTestFixture.ONE_SECOND;
import static org.apache.james.mailbox.events.EventBusTestFixture.newListener;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import org.apache.james.core.User;
import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;

public interface KeyContract extends EventBusContract {

    interface SingleEventBusKeyContract extends EventBusContract {
        @Test
        default void registeredListenersShouldNotReceiveNoopEvents() {
            MailboxListener listener = newListener();

            eventBus().register(listener, KEY_1);

            MailboxListener.Added noopEvent = new MailboxListener.Added(MailboxSession.SessionId.of(18), User.fromUsername("bob"), MailboxPath.forUser("bob", "mailbox"), TestId.of(58), ImmutableSortedMap.of(), Event.EventId.random());
            eventBus().dispatch(noopEvent, KEY_1).block();

            verify(listener, after(FIVE_HUNDRED_MS).never())
                .event(any());
        }

        @Test
        default void dispatchShouldNotThrowWhenARegisteredListenerFails() {
            MailboxListener listener = newListener();
            doThrow(new RuntimeException()).when(listener).event(any());

            eventBus().register(listener, KEY_1);

            assertThatCode(() -> eventBus().dispatch(EVENT, NO_KEYS).block())
                .doesNotThrowAnyException();
        }

        @Test
        default void dispatchShouldNotNotifyRegisteredListenerWhenEmptyKeySet() {
            MailboxListener listener = newListener();
            eventBus().register(listener, KEY_1);

            eventBus().dispatch(EVENT, NO_KEYS).block();

            verify(listener, after(FIVE_HUNDRED_MS).never())
                .event(any());
        }

        @Test
        default void dispatchShouldNotNotifyListenerRegisteredOnOtherKeys() {
            MailboxListener listener = newListener();
            eventBus().register(listener, KEY_1);

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_2)).block();

            verify(listener, after(FIVE_HUNDRED_MS).never())
                .event(any());
        }

        @Test
        default void dispatchShouldNotifyRegisteredListeners() {
            MailboxListener listener = newListener();
            eventBus().register(listener, KEY_1);

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, timeout(ONE_SECOND).times(1)).event(any());
        }

        @Test
        default void dispatchShouldNotifyOnlyRegisteredListener() {
            MailboxListener listener = newListener();
            MailboxListener listener2 = newListener();
            eventBus().register(listener, KEY_1);
            eventBus().register(listener2, KEY_2);

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, timeout(ONE_SECOND).times(1)).event(any());
            verify(listener2, after(FIVE_HUNDRED_MS).never())
                .event(any());
        }

        @Test
        default void dispatchShouldNotifyAllListenersRegisteredOnAKey() {
            MailboxListener listener = newListener();
            MailboxListener listener2 = newListener();
            eventBus().register(listener, KEY_1);
            eventBus().register(listener2, KEY_1);

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, timeout(ONE_SECOND).times(1)).event(any());
            verify(listener2, timeout(ONE_SECOND).times(1)).event(any());
        }

        @Test
        default void registerShouldAllowDuplicatedRegistration() {
            MailboxListener listener = newListener();
            eventBus().register(listener, KEY_1);
            eventBus().register(listener, KEY_1);

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, timeout(ONE_SECOND).times(1)).event(any());
        }

        @Test
        default void unregisterShouldRemoveDoubleRegisteredListener() {
            MailboxListener listener = newListener();
            eventBus().register(listener, KEY_1);
            eventBus().register(listener, KEY_1).unregister();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, after(FIVE_HUNDRED_MS).never())
                .event(any());
        }

        @Test
        default void callingAllUnregisterMethodShouldUnregisterTheListener() {
            MailboxListener listener = newListener();
            Registration registration = eventBus().register(listener, KEY_1);
            eventBus().register(listener, KEY_1).unregister();
            registration.unregister();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, after(FIVE_HUNDRED_MS).never())
                .event(any());
        }

        @Test
        default void unregisterShouldHaveNotNotifyWhenCalledOnDifferentKeys() {
            MailboxListener listener = newListener();
            eventBus().register(listener, KEY_1);
            eventBus().register(listener, KEY_2).unregister();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, timeout(ONE_SECOND).times(1)).event(any());
        }

        @Test
        default void unregisterShouldBeIdempotentForKeyRegistrations() {
            MailboxListener listener = newListener();

            Registration registration = eventBus().register(listener, KEY_1);
            registration.unregister();

            assertThatCode(registration::unregister)
                .doesNotThrowAnyException();
        }

        @Test
        default void dispatchShouldAcceptSeveralKeys() {
            MailboxListener listener = newListener();
            eventBus().register(listener, KEY_1);

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1, KEY_2)).block();

            verify(listener, timeout(ONE_SECOND).times(1)).event(any());
        }

        @Test
        default void dispatchShouldCallListenerOnceWhenSeveralKeysMatching() {
            MailboxListener listener = newListener();
            eventBus().register(listener, KEY_1);
            eventBus().register(listener, KEY_2);

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1, KEY_2)).block();

            verify(listener, timeout(ONE_SECOND).times(1)).event(any());
        }

        @Test
        default void dispatchShouldNotNotifyUnregisteredListener() {
            MailboxListener listener = newListener();
            eventBus().register(listener, KEY_1).unregister();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, after(FIVE_HUNDRED_MS).never())
                .event(any());
        }

        @Test
        default void dispatchShouldNotBlockAsynchronousListener() {
            MailboxListener listener = newListener();
            when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.ASYNCHRONOUS);
            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(invocation -> {
                latch.await();
                return null;
            }).when(listener).event(EVENT);

            assertTimeout(Duration.ofSeconds(2),
                () -> {
                    eventBus().dispatch(EVENT, NO_KEYS).block();
                    latch.countDown();
                });
        }
    }
}
