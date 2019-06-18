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
import static org.apache.james.mailbox.events.EventBusTestFixture.EVENT_UNSUPPORTED_BY_LISTENER;
import static org.apache.james.mailbox.events.EventBusTestFixture.FIVE_HUNDRED_MS;
import static org.apache.james.mailbox.events.EventBusTestFixture.KEY_1;
import static org.apache.james.mailbox.events.EventBusTestFixture.KEY_2;
import static org.apache.james.mailbox.events.EventBusTestFixture.NO_KEYS;
import static org.apache.james.mailbox.events.EventBusTestFixture.ONE_SECOND;
import static org.apache.james.mailbox.events.EventBusTestFixture.WAIT_CONDITION;
import static org.apache.james.mailbox.events.EventBusTestFixture.newListener;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.apache.james.core.User;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;

public interface KeyContract extends EventBusContract {

    interface SingleEventBusKeyContract extends EventBusContract {
        @Test
        default void notificationShouldNotExceedRate() {
            int eventCount = 50;
            AtomicInteger nbCalls = new AtomicInteger(0);
            AtomicInteger finishedExecutions = new AtomicInteger(0);
            AtomicBoolean rateExceeded = new AtomicBoolean(false);

            eventBus().register(event -> {
                if (nbCalls.get() - finishedExecutions.get() > EventBus.EXECUTION_RATE) {
                    rateExceeded.set(true);
                }
                nbCalls.incrementAndGet();
                Thread.sleep(Duration.ofMillis(200).toMillis());
                finishedExecutions.incrementAndGet();

            }, KEY_1);

            IntStream.range(0, eventCount)
                .forEach(i -> eventBus().dispatch(EVENT, KEY_1).block());

            WAIT_CONDITION.atMost(com.jayway.awaitility.Duration.TEN_MINUTES).until(() -> finishedExecutions.get() == eventCount);
            assertThat(rateExceeded).isFalse();
        }

        @Test
        default void registeredListenersShouldNotReceiveNoopEvents() throws Exception {
            MailboxListener listener = newListener();

            eventBus().register(listener, KEY_1);

            MailboxListener.Added noopEvent = new MailboxListener.Added(MailboxSession.SessionId.of(18), User.fromUsername("bob"), MailboxPath.forUser("bob", "mailbox"), TestId.of(58), ImmutableSortedMap.of(), Event.EventId.random());
            eventBus().dispatch(noopEvent, KEY_1).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

       @Test
        default void registeredListenersShouldReceiveOnlyHandledEvents() throws Exception {
            MailboxListener listener = newListener();

            eventBus().register(listener, KEY_1);

            eventBus().dispatch(EVENT_UNSUPPORTED_BY_LISTENER, KEY_1).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void dispatchShouldNotThrowWhenARegisteredListenerFails() throws Exception {
            MailboxListener listener = newListener();
            doThrow(new RuntimeException()).when(listener).event(any());

            eventBus().register(listener, KEY_1);

            assertThatCode(() -> eventBus().dispatch(EVENT, NO_KEYS).block())
                .doesNotThrowAnyException();
        }

        @Test
        default void dispatchShouldNotNotifyRegisteredListenerWhenEmptyKeySet() throws Exception {
            MailboxListener listener = newListener();
            eventBus().register(listener, KEY_1);

            eventBus().dispatch(EVENT, NO_KEYS).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void dispatchShouldNotNotifyListenerRegisteredOnOtherKeys() throws Exception {
            MailboxListener listener = newListener();
            eventBus().register(listener, KEY_1);

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_2)).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void dispatchShouldNotifyRegisteredListeners() throws Exception {
            MailboxListener listener = newListener();
            eventBus().register(listener, KEY_1);

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void dispatchShouldNotifyLocalRegisteredListenerWithoutDelay() throws Exception {
            MailboxListener listener = newListener();
            eventBus().register(listener, KEY_1);

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, times(1)).event(any());
        }

        @Test
        default void dispatchShouldNotifyOnlyRegisteredListener() throws Exception {
            MailboxListener listener = newListener();
            MailboxListener listener2 = newListener();
            eventBus().register(listener, KEY_1);
            eventBus().register(listener2, KEY_2);

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
            verify(listener2, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void dispatchShouldNotifyAllListenersRegisteredOnAKey() throws Exception {
            MailboxListener listener = newListener();
            MailboxListener listener2 = newListener();
            eventBus().register(listener, KEY_1);
            eventBus().register(listener2, KEY_1);

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
            verify(listener2, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void registerShouldAllowDuplicatedRegistration() throws Exception {
            MailboxListener listener = newListener();
            eventBus().register(listener, KEY_1);
            eventBus().register(listener, KEY_1);

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void unregisterShouldRemoveDoubleRegisteredListener() throws Exception {
            MailboxListener listener = newListener();
            eventBus().register(listener, KEY_1);
            eventBus().register(listener, KEY_1).unregister();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void registerShouldNotDispatchPastEvents() throws Exception {
            MailboxListener listener = newListener();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            eventBus().register(listener, KEY_1);

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void callingAllUnregisterMethodShouldUnregisterTheListener() throws Exception {
            MailboxListener listener = newListener();
            Registration registration = eventBus().register(listener, KEY_1);
            eventBus().register(listener, KEY_1).unregister();
            registration.unregister();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void unregisterShouldHaveNotNotifyWhenCalledOnDifferentKeys() throws Exception {
            MailboxListener listener = newListener();
            eventBus().register(listener, KEY_1);
            eventBus().register(listener, KEY_2).unregister();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
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
        default void dispatchShouldAcceptSeveralKeys() throws Exception {
            MailboxListener listener = newListener();
            eventBus().register(listener, KEY_1);

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1, KEY_2)).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void dispatchShouldCallListenerOnceWhenSeveralKeysMatching() throws Exception {
            MailboxListener listener = newListener();
            eventBus().register(listener, KEY_1);
            eventBus().register(listener, KEY_2);

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1, KEY_2)).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void dispatchShouldNotNotifyUnregisteredListener() throws Exception {
            MailboxListener listener = newListener();
            eventBus().register(listener, KEY_1).unregister();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void dispatchShouldNotBlockAsynchronousListener() throws Exception {
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

        @Test
        default void failingRegisteredListenersShouldNotAbortRegisteredDelivery() {
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution listener = new EventBusTestFixture.EventMatcherThrowingListener(ImmutableSet.of(EVENT));
            eventBus().register(listener, KEY_1);

            eventBus().dispatch(EVENT, KEY_1).block();
            eventBus().dispatch(EVENT_2, KEY_1).block();

            WAIT_CONDITION
                .until(() -> assertThat(listener.numberOfEventCalls()).isEqualTo(1));
        }

        @Test
        default void allRegisteredListenersShouldBeExecutedWhenARegisteredListenerFails() throws Exception {
            MailboxListener listener = newListener();

            MailboxListener failingListener = mock(MailboxListener.class);
            when(failingListener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.SYNCHRONOUS);
            doThrow(new RuntimeException()).when(failingListener).event(any());

            eventBus().register(failingListener, KEY_1);
            eventBus().register(listener, KEY_1);

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }
    }

    interface MultipleEventBusKeyContract extends MultipleEventBusContract {

        @Test
        default void crossEventBusRegistrationShouldBeAllowed() throws Exception {
            MailboxListener mailboxListener = newListener();

            eventBus().register(mailboxListener, KEY_1);

            eventBus2().dispatch(EVENT, KEY_1).block();

            verify(mailboxListener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void unregisteredDistantListenersShouldNotBeNotified() throws Exception {
            MailboxListener mailboxListener = newListener();

            eventBus().register(mailboxListener, KEY_1).unregister();

            eventBus2().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(mailboxListener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void allRegisteredListenersShouldBeDispatched() throws Exception {
            MailboxListener mailboxListener1 = newListener();
            MailboxListener mailboxListener2 = newListener();

            eventBus().register(mailboxListener1, KEY_1);
            eventBus2().register(mailboxListener2, KEY_1);

            eventBus2().dispatch(EVENT, KEY_1).block();

            verify(mailboxListener1, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
            verify(mailboxListener2, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void registerShouldNotDispatchPastEventsInDistributedContext() throws Exception {
            MailboxListener listener = newListener();

            eventBus2().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            eventBus().register(listener, KEY_1);

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void localDispatchedListenersShouldBeDispatchedWithoutDelay() throws Exception {
            MailboxListener mailboxListener1 = newListener();
            MailboxListener mailboxListener2 = newListener();

            eventBus().register(mailboxListener1, KEY_1);
            eventBus2().register(mailboxListener2, KEY_1);

            eventBus2().dispatch(EVENT, KEY_1).block();

            verify(mailboxListener2, times(1)).event(any());
            verify(mailboxListener1, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

    }
}
