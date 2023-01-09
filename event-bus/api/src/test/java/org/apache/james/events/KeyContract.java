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

package org.apache.james.events;

import static org.apache.james.events.EventBusTestFixture.EVENT;
import static org.apache.james.events.EventBusTestFixture.EVENT_ID;
import static org.apache.james.events.EventBusTestFixture.EVENT_UNSUPPORTED_BY_LISTENER;
import static org.apache.james.events.EventBusTestFixture.FIVE_HUNDRED_MS;
import static org.apache.james.events.EventBusTestFixture.KEY_1;
import static org.apache.james.events.EventBusTestFixture.KEY_2;
import static org.apache.james.events.EventBusTestFixture.NO_KEYS;
import static org.apache.james.events.EventBusTestFixture.ONE_SECOND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Durations.TEN_MINUTES;
import static org.awaitility.Durations.TEN_SECONDS;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.apache.james.core.Username;
import org.apache.james.events.EventListener.ExecutionMode;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public interface KeyContract extends EventBusContract {

    interface SingleEventBusKeyContract extends EventBusContract {
        @Test
        default void notificationShouldNotExceedRate() {
            int eventCount = 50;
            AtomicInteger nbCalls = new AtomicInteger(0);
            AtomicInteger finishedExecutions = new AtomicInteger(0);
            AtomicBoolean rateExceeded = new AtomicBoolean(false);

            Mono.from(eventBus().register(event -> {
                if (nbCalls.get() - finishedExecutions.get() > EventBus.EXECUTION_RATE) {
                    rateExceeded.set(true);
                }
                nbCalls.incrementAndGet();
                Thread.sleep(Duration.ofMillis(20).toMillis());
                finishedExecutions.incrementAndGet();

            }, KEY_1)).block();

            IntStream.range(0, eventCount)
                .forEach(i -> eventBus().dispatch(EVENT, KEY_1).block());

            getSpeedProfile().shortWaitCondition().atMost(TEN_MINUTES)
                .untilAsserted(() -> assertThat(finishedExecutions.get()).isEqualTo(eventCount));
            assertThat(rateExceeded).isFalse();
        }

        @Test
        default void notificationShouldDeliverASingleEventToAllListenersAtTheSameTime() {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            try {
                ConcurrentLinkedQueue<String> threads = new ConcurrentLinkedQueue<>();
                Mono.from(eventBus().register(event -> {
                    threads.add(Thread.currentThread().getName());
                    countDownLatch.await();
                }, KEY_1)).block();
                Mono.from(eventBus().register(event -> {
                    threads.add(Thread.currentThread().getName());
                    countDownLatch.await();
                }, KEY_1)).block();
                Mono.from(eventBus().register(event -> {
                    threads.add(Thread.currentThread().getName());
                    countDownLatch.await();
                }, KEY_1)).block();

                eventBus().dispatch(EVENT, KEY_1).subscribeOn(Schedulers.newSingle("test")).subscribe();


                getSpeedProfile().shortWaitCondition().atMost(TEN_SECONDS)
                    .untilAsserted(() -> assertThat(threads).hasSize(3));
                assertThat(threads).doesNotHaveDuplicates();
            } finally {
                countDownLatch.countDown();
            }
        }


        @Test
        default void registeredListenersShouldNotReceiveNoopEvents() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            Mono.from(eventBus().register(listener, KEY_1)).block();

            Event noopEvent = new EventBusTestFixture.TestEvent(EVENT_ID, Username.of("noop"));
            eventBus().dispatch(noopEvent, KEY_1).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

       @Test
        default void registeredListenersShouldReceiveOnlyHandledEvents() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            Mono.from(eventBus().register(listener, KEY_1)).block();

            eventBus().dispatch(EVENT_UNSUPPORTED_BY_LISTENER, KEY_1).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void dispatchShouldNotThrowWhenARegisteredListenerFails() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            doThrow(new RuntimeException()).when(listener).event(any());

            Mono.from(eventBus().register(listener, KEY_1)).block();

            assertThatCode(() -> eventBus().dispatch(EVENT, NO_KEYS).block())
                .doesNotThrowAnyException();
        }

        @Test
        default void dispatchShouldNotNotifyRegisteredListenerWhenEmptyKeySet() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            Mono.from(eventBus().register(listener, KEY_1)).block();

            eventBus().dispatch(EVENT, NO_KEYS).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void dispatchShouldNotNotifyListenerRegisteredOnOtherKeys() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            Mono.from(eventBus().register(listener, KEY_1)).block();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_2)).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void dispatchShouldNotifyRegisteredListeners() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            Mono.from(eventBus().register(listener, KEY_1)).block();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void dispatchShouldNotifyLocalRegisteredListenerWithoutDelay() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            Mono.from(eventBus().register(listener, KEY_1)).block();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, times(1)).event(any());
        }

        @Test
        default void dispatchShouldNotifyOnlyRegisteredListener() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            EventListener listener2 = EventBusTestFixture.newListener();
            Mono.from(eventBus().register(listener, KEY_1)).block();
            Mono.from(eventBus().register(listener2, KEY_2)).block();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
            verify(listener2, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void dispatchShouldNotifyAllListenersRegisteredOnAKey() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            EventListener listener2 = EventBusTestFixture.newListener();
            Mono.from(eventBus().register(listener, KEY_1)).block();
            Mono.from(eventBus().register(listener2, KEY_1)).block();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
            verify(listener2, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void registerShouldAllowDuplicatedRegistration() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            Mono.from(eventBus().register(listener, KEY_1)).block();
            Mono.from(eventBus().register(listener, KEY_1)).block();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void unregisterShouldRemoveDoubleRegisteredListener() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            Mono.from(eventBus().register(listener, KEY_1)).block();
            Mono.from(Mono.from(eventBus().register(listener, KEY_1)).block().unregister()).block();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void registerShouldNotDispatchPastEvents() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            Mono.from(eventBus().register(listener, KEY_1)).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void callingAllUnregisterMethodShouldUnregisterTheListener() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            Registration registration = Mono.from(eventBus().register(listener, KEY_1)).block();
            Mono.from(Mono.from(eventBus().register(listener, KEY_1)).block().unregister()).block();
            Mono.from(registration.unregister()).block();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void unregisterShouldHaveNotNotifyWhenCalledOnDifferentKeys() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            Mono.from(eventBus().register(listener, KEY_1)).block();
            Mono.from(Mono.from(eventBus().register(listener, KEY_2)).block().unregister()).block();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void unregisterShouldBeIdempotentForKeyRegistrations() {
            EventListener listener = EventBusTestFixture.newListener();

            Registration registration = Mono.from(eventBus().register(listener, KEY_1)).block();
            Mono.from(registration.unregister()).block();

            assertThatCode(() -> Mono.from(registration.unregister()).block())
                .doesNotThrowAnyException();
        }

        @Test
        default void dispatchShouldAcceptSeveralKeys() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            Mono.from(eventBus().register(listener, KEY_1)).block();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1, KEY_2)).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void dispatchShouldCallListenerOnceWhenSeveralKeysMatching() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            Mono.from(eventBus().register(listener, KEY_1)).block();
            Mono.from(eventBus().register(listener, KEY_2)).block();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1, KEY_2)).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void dispatchShouldNotNotifyUnregisteredListener() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            Mono.from(Mono.from(eventBus().register(listener, KEY_1)).block().unregister()).block();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }


        @Test
        default void dispatchShouldNotifyAsynchronousListener() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            when(listener.getExecutionMode()).thenReturn(ExecutionMode.ASYNCHRONOUS);
            Mono.from(eventBus().register(listener, KEY_1)).block();

            eventBus().dispatch(EVENT, KEY_1).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis())).event(EVENT);
        }

        @Test
        default void dispatchShouldNotBlockAsynchronousListener() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            when(listener.getExecutionMode()).thenReturn(ExecutionMode.ASYNCHRONOUS);
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
            EventBusTestFixture.EventListenerCountingSuccessfulExecution listener = new EventBusTestFixture.EventMatcherThrowingListener(ImmutableSet.of(EVENT));
            Mono.from(eventBus().register(listener, KEY_1)).block();

            eventBus().dispatch(EVENT, KEY_1).block();
            eventBus().dispatch(EventBusTestFixture.EVENT_2, KEY_1).block();

            getSpeedProfile().shortWaitCondition()
                .untilAsserted(() -> assertThat(listener.numberOfEventCalls()).isEqualTo(1));
        }

        @Test
        default void allRegisteredListenersShouldBeExecutedWhenARegisteredListenerFails() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            EventListener failingListener = mock(EventListener.class);
            when(failingListener.getExecutionMode()).thenReturn(ExecutionMode.SYNCHRONOUS);
            doThrow(new RuntimeException()).when(failingListener).event(any());

            Mono.from(eventBus().register(failingListener, KEY_1)).block();
            Mono.from(eventBus().register(listener, KEY_1)).block();

            eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }
    }

    interface MultipleEventBusKeyContract extends MultipleEventBusContract {

        @Test
        default void crossEventBusRegistrationShouldBeAllowed() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            Mono.from(eventBus().register(listener, KEY_1)).block();

            eventBus2().dispatch(EVENT, KEY_1).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void unregisteredDistantListenersShouldNotBeNotified() throws Exception {
            EventListener eventListener = EventBusTestFixture.newListener();

            Mono.from(Mono.from(eventBus().register(eventListener, KEY_1)).block().unregister()).block();

            eventBus2().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            verify(eventListener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void allRegisteredListenersShouldBeDispatched() throws Exception {
            EventListener listener1 = EventBusTestFixture.newListener();
            EventListener listener2 = EventBusTestFixture.newListener();

            Mono.from(eventBus().register(listener1, KEY_1)).block();
            Mono.from(eventBus2().register(listener2, KEY_1)).block();

            eventBus2().dispatch(EVENT, KEY_1).block();

            verify(listener1, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
            verify(listener2, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void registerShouldNotDispatchPastEventsInDistributedContext() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus2().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            Mono.from(eventBus().register(listener, KEY_1)).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void localDispatchedListenersShouldBeDispatchedWithoutDelay() throws Exception {
            EventListener listener1 = EventBusTestFixture.newListener();
            EventListener listener2 = EventBusTestFixture.newListener();

            Mono.from(eventBus().register(listener1, KEY_1)).block();
            Mono.from(eventBus2().register(listener2, KEY_1)).block();

            eventBus2().dispatch(EVENT, KEY_1).block();

            verify(listener2, times(1)).event(any());
            verify(listener1, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

    }
}
