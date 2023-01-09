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
import static org.apache.james.events.EventBusTestFixture.EVENT_2;
import static org.apache.james.events.EventBusTestFixture.EVENT_ID;
import static org.apache.james.events.EventBusTestFixture.EVENT_UNSUPPORTED_BY_LISTENER;
import static org.apache.james.events.EventBusTestFixture.FIVE_HUNDRED_MS;
import static org.apache.james.events.EventBusTestFixture.GROUP_A;
import static org.apache.james.events.EventBusTestFixture.GROUP_B;
import static org.apache.james.events.EventBusTestFixture.NO_KEYS;
import static org.apache.james.events.EventBusTestFixture.ONE_SECOND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Durations.TEN_MINUTES;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.apache.james.core.Username;
import org.apache.james.events.EventBusTestFixture.TestEvent;
import org.apache.james.mailbox.events.GenericGroup;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public interface GroupContract {

    interface SingleEventBusGroupContract extends EventBusContract {

        @Test
        default void groupDeliveryShouldNotExceedRate() {
            int eventCount = 50;
            AtomicInteger nbCalls = new AtomicInteger(0);
            AtomicInteger inFlight = new AtomicInteger(0);
            AtomicBoolean rateExceeded = new AtomicBoolean(false);

            eventBus().register(new EventListener.GroupEventListener() {
                @Override
                public Group getDefaultGroup() {
                    return new GenericGroup("group");
                }

                @Override
                public boolean isHandling(Event event) {
                    return true;
                }

                @Override
                public void event(Event event) throws Exception {
                    if (inFlight.incrementAndGet() > EventBus.EXECUTION_RATE) {
                        rateExceeded.set(true);
                    }
                    nbCalls.incrementAndGet();
                    Thread.sleep(Duration.ofMillis(20).toMillis());
                    inFlight.decrementAndGet();
                }
            }, GROUP_A);

            IntStream.range(0, eventCount)
                .forEach(i -> eventBus().dispatch(EVENT, NO_KEYS).block());

            getSpeedProfile().shortWaitCondition().atMost(TEN_MINUTES)
                .untilAsserted(() -> assertThat(nbCalls.get()).isEqualTo(eventCount));
            assertThat(rateExceeded).isFalse();
        }

        @Test
        default void groupNotificationShouldDeliverASingleEventToAllListenersAtTheSameTime() {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            try {
                ConcurrentLinkedQueue<String> threads = new ConcurrentLinkedQueue<>();
                eventBus().register(new EventListener.GroupEventListener() {
                    @Override
                    public Group getDefaultGroup() {
                        return new GenericGroup("groupA");
                    }

                    @Override
                    public void event(Event event) throws Exception {
                        threads.add(Thread.currentThread().getName());
                        countDownLatch.await();
                    }
                }, GROUP_A);
                eventBus().register(new EventListener.GroupEventListener() {
                    @Override
                    public Group getDefaultGroup() {
                        return new GenericGroup("groupB");
                    }

                    @Override
                    public void event(Event event) throws Exception {
                        threads.add(Thread.currentThread().getName());
                        countDownLatch.await();
                    }
                }, GROUP_B);
                eventBus().register(new EventListener.GroupEventListener() {
                    @Override
                    public Group getDefaultGroup() {
                        return new GenericGroup("groupC");
                    }

                    @Override
                    public void event(Event event) throws Exception {
                        threads.add(Thread.currentThread().getName());
                        countDownLatch.await();
                    }
                }, EventBusTestFixture.GROUP_C);

                eventBus().dispatch(EVENT, NO_KEYS).subscribeOn(Schedulers.newSingle("test")).subscribe();


                getSpeedProfile().shortWaitCondition().atMost(TEN_SECONDS)
                    .untilAsserted(() -> assertThat(threads).hasSize(3));
                assertThat(threads).doesNotHaveDuplicates();
            } finally {
                countDownLatch.countDown();
            }
        }

        @Test
        default void listenersShouldBeAbleToDispatch() {
            AtomicBoolean successfulRetry = new AtomicBoolean(false);
            EventListener listener = event -> {
                if (event.getEventId().equals(EVENT_ID)) {
                    eventBus().dispatch(EVENT_2, NO_KEYS)
                        .subscribeOn(Schedulers.newSingle("test"))
                        .block();
                    successfulRetry.set(true);
                }
            };

            eventBus().register(listener, GROUP_A);
            eventBus().dispatch(EVENT, NO_KEYS).block();

            getSpeedProfile().shortWaitCondition().until(successfulRetry::get);
        }

        @Test
        default void registerShouldNotDispatchPastEventsForGroups() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().dispatch(EVENT, NO_KEYS).block();
            Thread.sleep(100);

            eventBus().register(listener, GROUP_A);

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void listenerGroupShouldReceiveEvents() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().register(listener, GROUP_A);

            eventBus().dispatch(EVENT, NO_KEYS).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void groupListenersShouldNotReceiveNoopEvents() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().register(listener, GROUP_A);

            Event noopEvent = new TestEvent(EVENT_ID, Username.of("noop"));
            eventBus().dispatch(noopEvent, NO_KEYS).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void groupListenersShouldReceiveOnlyHandledEvents() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().register(listener, GROUP_A);

            eventBus().dispatch(EVENT_UNSUPPORTED_BY_LISTENER, NO_KEYS).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void dispatchShouldNotThrowWhenAGroupListenerFails() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            doThrow(new RuntimeException()).when(listener).event(any());

            eventBus().register(listener, GROUP_A);

            assertThatCode(() -> eventBus().dispatch(EVENT, NO_KEYS).block())
                .doesNotThrowAnyException();
        }

        @Test
        default void eachListenerGroupShouldReceiveEvents() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            EventListener listener2 = EventBusTestFixture.newListener();
            eventBus().register(listener, GROUP_A);
            eventBus().register(listener2, GROUP_B);

            eventBus().dispatch(EVENT, NO_KEYS).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
            verify(listener2, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void unregisteredGroupListenerShouldNotReceiveEvents() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            Registration registration = eventBus().register(listener, GROUP_A);

            Mono.from(registration.unregister()).block();

            eventBus().dispatch(EVENT, NO_KEYS).block();
            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void registerShouldThrowWhenAGroupIsAlreadyUsed() {
            EventListener listener = EventBusTestFixture.newListener();
            EventListener listener2 = EventBusTestFixture.newListener();

            eventBus().register(listener, GROUP_A);

            assertThatThrownBy(() -> eventBus().register(listener2, GROUP_A))
                .isInstanceOf(GroupAlreadyRegistered.class);
        }

        @Test
        default void registerShouldNotThrowOnAnUnregisteredGroup() {
            EventListener listener = EventBusTestFixture.newListener();
            EventListener listener2 = EventBusTestFixture.newListener();

            Mono.from(eventBus().register(listener, GROUP_A).unregister()).block();

            assertThatCode(() -> eventBus().register(listener2, GROUP_A))
                .doesNotThrowAnyException();
        }

        @Test
        default void unregisterShouldBeIdempotentForGroups() {
            EventListener listener = EventBusTestFixture.newListener();

            Registration registration = eventBus().register(listener, GROUP_A);
            Mono.from(registration.unregister()).block();

            assertThatCode(() -> Mono.from(registration.unregister()).block())
                .doesNotThrowAnyException();
        }

        @Test
        default void registerShouldAcceptAlreadyUnregisteredGroups() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            Mono.from(eventBus().register(listener, GROUP_A).unregister()).block();
            eventBus().register(listener, GROUP_A);

            eventBus().dispatch(EVENT, NO_KEYS).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void dispatchShouldCallSynchronousListener() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().register(listener, GROUP_A);

            eventBus().dispatch(EVENT, NO_KEYS).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void failingGroupListenersShouldNotAbortGroupDelivery() {
            EventBusTestFixture.EventListenerCountingSuccessfulExecution listener = new EventBusTestFixture.EventMatcherThrowingListener(ImmutableSet.of(EVENT));
            eventBus().register(listener, GROUP_A);

            eventBus().dispatch(EVENT, NO_KEYS).block();
            eventBus().dispatch(EVENT_2, NO_KEYS).block();

            getSpeedProfile().shortWaitCondition()
                .untilAsserted(() -> assertThat(listener.numberOfEventCalls()).isEqualTo(1));
        }

        @Test
        default void allGroupListenersShouldBeExecutedWhenAGroupListenerFails() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            EventListener failingListener = mock(EventListener.class);
            when(failingListener.getExecutionMode()).thenReturn(EventListener.ExecutionMode.SYNCHRONOUS);
            doThrow(new RuntimeException()).when(failingListener).event(any());

            eventBus().register(failingListener, GROUP_A);
            eventBus().register(listener, GROUP_B);

            eventBus().dispatch(EVENT, NO_KEYS).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void allGroupListenersShouldBeExecutedWhenGenericGroups() throws Exception {
            EventListener listener1 = EventBusTestFixture.newListener();
            EventListener listener2 = EventBusTestFixture.newListener();

            eventBus().register(listener1, new GenericGroup("a"));
            eventBus().register(listener2, new GenericGroup("b"));

            eventBus().dispatch(EVENT, NO_KEYS).block();

            verify(listener1, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
            verify(listener2, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void groupListenerShouldReceiveEventWhenRedeliver() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().register(listener, GROUP_A);

            eventBus().reDeliver(GROUP_A, EVENT).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void redeliverShouldNotThrowWhenAGroupListenerFails() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            doThrow(new RuntimeException()).when(listener).event(any());

            eventBus().register(listener, GROUP_A);

            assertThatCode(() -> eventBus().reDeliver(GROUP_A, EVENT).block())
                .doesNotThrowAnyException();
        }

        @Test
        default void redeliverShouldThrowWhenGroupNotRegistered() {
            assertThatThrownBy(() -> eventBus().reDeliver(GROUP_A, EVENT).block())
                .isInstanceOf(GroupRegistrationNotFound.class);
        }

        @Test
        default void redeliverShouldThrowAfterGroupIsUnregistered() {
            EventListener listener = EventBusTestFixture.newListener();

            Mono.from(eventBus().register(listener, GROUP_A).unregister()).block();

            assertThatThrownBy(() -> eventBus().reDeliver(GROUP_A, EVENT).block())
                .isInstanceOf(GroupRegistrationNotFound.class);
        }

        @Test
        default void redeliverShouldOnlySendEventToDefinedGroup() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            EventListener listener2 = EventBusTestFixture.newListener();
            eventBus().register(listener, GROUP_A);
            eventBus().register(listener2, GROUP_B);

            eventBus().reDeliver(GROUP_A, EVENT).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
            verify(listener2, after(FIVE_HUNDRED_MS.toMillis()).never()).event(any());
        }

        @Test
        default void groupListenersShouldNotReceiveNoopRedeliveredEvents() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().register(listener, GROUP_A);

            Event noopEvent = new TestEvent(EVENT_ID, Username.of("noop"));
            eventBus().reDeliver(GROUP_A, noopEvent).block();

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never()).event(any());
        }
    }

    interface MultipleEventBusGroupContract extends EventBusContract.MultipleEventBusContract {

        @Test
        default void groupsDefinedOnlyOnSomeNodesShouldBeNotifiedWhenDispatch() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().register(listener, GROUP_A);

            eventBus2().dispatch(EVENT, NO_KEYS).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void groupsDefinedOnlyOnSomeNodesShouldNotBeNotifiedWhenRedeliver() {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().register(listener, GROUP_A);

            assertThatThrownBy(() -> eventBus2().reDeliver(GROUP_A, EVENT).block())
                .isInstanceOf(GroupRegistrationNotFound.class);
        }

        @Test
        default void groupListenersShouldBeExecutedOnceWhenRedeliverInADistributedEnvironment() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().register(listener, GROUP_A);
            eventBus2().register(listener, GROUP_A);

            eventBus2().reDeliver(GROUP_A, EVENT).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void groupListenersShouldBeExecutedOnceInAControlledEnvironment() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().register(listener, GROUP_A);
            eventBus2().register(listener, GROUP_A);

            eventBus2().dispatch(EVENT, NO_KEYS).block();

            verify(listener, timeout(ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void unregisterShouldStopNotificationForDistantGroups() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            Mono.from(eventBus().register(listener, GROUP_A).unregister()).block();

            eventBus2().dispatch(EVENT, NO_KEYS).block();


            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void registerShouldNotDispatchPastEventsForGroupsInADistributedContext() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().dispatch(EVENT, NO_KEYS).block();

            eventBus2().register(listener, GROUP_A);

            verify(listener, after(FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }
    }
}
