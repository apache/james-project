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

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.apache.james.core.Username;
import org.apache.james.events.MailboxEvents.Added;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.events.EventBusTestFixture.TestEvent;
import org.apache.james.mailbox.events.GenericGroup;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;

import reactor.core.scheduler.Schedulers;

public interface GroupContract {

    interface SingleEventBusGroupContract extends EventBusContract {

        @Test
        default void groupDeliveryShouldNotExceedRate() {
            int eventCount = 50;
            AtomicInteger nbCalls = new AtomicInteger(0);
            AtomicInteger finishedExecutions = new AtomicInteger(0);
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
                    if (nbCalls.get() - finishedExecutions.get() > EventBus.EXECUTION_RATE) {
                        rateExceeded.set(true);
                    }
                    nbCalls.incrementAndGet();
                    Thread.sleep(Duration.ofMillis(20).toMillis());
                    finishedExecutions.incrementAndGet();

                }
            }, EventBusTestFixture.GROUP_A);

            IntStream.range(0, eventCount)
                .forEach(i -> eventBus().dispatch(EventBusTestFixture.EVENT, EventBusTestFixture.NO_KEYS).block());

            getSpeedProfile().shortWaitCondition().atMost(org.awaitility.Duration.TEN_MINUTES)
                .untilAsserted(() -> assertThat(finishedExecutions.get()).isEqualTo(eventCount));
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
                }, EventBusTestFixture.GROUP_A);
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
                }, EventBusTestFixture.GROUP_B);
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

                eventBus().dispatch(EventBusTestFixture.EVENT, EventBusTestFixture.NO_KEYS).subscribeOn(Schedulers.elastic()).subscribe();


                getSpeedProfile().shortWaitCondition().atMost(org.awaitility.Duration.TEN_SECONDS)
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
                if (event.getEventId().equals(EventBusTestFixture.EVENT_ID)) {
                    eventBus().dispatch(EventBusTestFixture.EVENT_2, EventBusTestFixture.NO_KEYS)
                        .subscribeOn(Schedulers.elastic())
                        .block();
                    successfulRetry.set(true);
                }
            };

            eventBus().register(listener, EventBusTestFixture.GROUP_A);
            eventBus().dispatch(EventBusTestFixture.EVENT, EventBusTestFixture.NO_KEYS).block();

            getSpeedProfile().shortWaitCondition().until(successfulRetry::get);
        }

        @Test
        default void registerShouldNotDispatchPastEventsForGroups() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().dispatch(EventBusTestFixture.EVENT, EventBusTestFixture.NO_KEYS).block();

            eventBus().register(listener, EventBusTestFixture.GROUP_A);

            verify(listener, after(EventBusTestFixture.FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void listenerGroupShouldReceiveEvents() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().register(listener, EventBusTestFixture.GROUP_A);

            eventBus().dispatch(EventBusTestFixture.EVENT, EventBusTestFixture.NO_KEYS).block();

            verify(listener, timeout(EventBusTestFixture.ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void groupListenersShouldNotReceiveNoopEvents() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().register(listener, EventBusTestFixture.GROUP_A);

            Username bob = Username.of("bob");
            Added noopEvent = new Added(MailboxSession.SessionId.of(18), bob, MailboxPath.forUser(bob, "mailbox"), TestId.of(58), ImmutableSortedMap.of(), Event.EventId.random());
            eventBus().dispatch(noopEvent, EventBusTestFixture.NO_KEYS).block();

            verify(listener, after(EventBusTestFixture.FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void groupListenersShouldReceiveOnlyHandledEvents() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().register(listener, EventBusTestFixture.GROUP_A);

            eventBus().dispatch(EventBusTestFixture.EVENT_UNSUPPORTED_BY_LISTENER, EventBusTestFixture.NO_KEYS).block();

            verify(listener, after(EventBusTestFixture.FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void dispatchShouldNotThrowWhenAGroupListenerFails() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            doThrow(new RuntimeException()).when(listener).event(any());

            eventBus().register(listener, EventBusTestFixture.GROUP_A);

            assertThatCode(() -> eventBus().dispatch(EventBusTestFixture.EVENT, EventBusTestFixture.NO_KEYS).block())
                .doesNotThrowAnyException();
        }

        @Test
        default void eachListenerGroupShouldReceiveEvents() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            EventListener listener2 = EventBusTestFixture.newListener();
            eventBus().register(listener, EventBusTestFixture.GROUP_A);
            eventBus().register(listener2, EventBusTestFixture.GROUP_B);

            eventBus().dispatch(EventBusTestFixture.EVENT, EventBusTestFixture.NO_KEYS).block();

            verify(listener, timeout(EventBusTestFixture.ONE_SECOND.toMillis()).times(1)).event(any());
            verify(listener2, timeout(EventBusTestFixture.ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void unregisteredGroupListenerShouldNotReceiveEvents() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            Registration registration = eventBus().register(listener, EventBusTestFixture.GROUP_A);

            registration.unregister();

            eventBus().dispatch(EventBusTestFixture.EVENT, EventBusTestFixture.NO_KEYS).block();
            verify(listener, after(EventBusTestFixture.FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void registerShouldThrowWhenAGroupIsAlreadyUsed() {
            EventListener listener = EventBusTestFixture.newListener();
            EventListener listener2 = EventBusTestFixture.newListener();

            eventBus().register(listener, EventBusTestFixture.GROUP_A);

            assertThatThrownBy(() -> eventBus().register(listener2, EventBusTestFixture.GROUP_A))
                .isInstanceOf(GroupAlreadyRegistered.class);
        }

        @Test
        default void registerShouldNotThrowOnAnUnregisteredGroup() {
            EventListener listener = EventBusTestFixture.newListener();
            EventListener listener2 = EventBusTestFixture.newListener();

            eventBus().register(listener, EventBusTestFixture.GROUP_A).unregister();

            assertThatCode(() -> eventBus().register(listener2, EventBusTestFixture.GROUP_A))
                .doesNotThrowAnyException();
        }

        @Test
        default void unregisterShouldBeIdempotentForGroups() {
            EventListener listener = EventBusTestFixture.newListener();

            Registration registration = eventBus().register(listener, EventBusTestFixture.GROUP_A);
            registration.unregister();

            assertThatCode(registration::unregister)
                .doesNotThrowAnyException();
        }

        @Test
        default void registerShouldAcceptAlreadyUnregisteredGroups() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().register(listener, EventBusTestFixture.GROUP_A).unregister();
            eventBus().register(listener, EventBusTestFixture.GROUP_A);

            eventBus().dispatch(EventBusTestFixture.EVENT, EventBusTestFixture.NO_KEYS).block();

            verify(listener, timeout(EventBusTestFixture.ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void dispatchShouldCallSynchronousListener() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().register(listener, EventBusTestFixture.GROUP_A);

            eventBus().dispatch(EventBusTestFixture.EVENT, EventBusTestFixture.NO_KEYS).block();

            verify(listener, timeout(EventBusTestFixture.ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void failingGroupListenersShouldNotAbortGroupDelivery() {
            EventBusTestFixture.EventListenerCountingSuccessfulExecution listener = new EventBusTestFixture.EventMatcherThrowingListener(ImmutableSet.of(EventBusTestFixture.EVENT));
            eventBus().register(listener, EventBusTestFixture.GROUP_A);

            eventBus().dispatch(EventBusTestFixture.EVENT, EventBusTestFixture.NO_KEYS).block();
            eventBus().dispatch(EventBusTestFixture.EVENT_2, EventBusTestFixture.NO_KEYS).block();

            getSpeedProfile().shortWaitCondition()
                .untilAsserted(() -> assertThat(listener.numberOfEventCalls()).isEqualTo(1));
        }

        @Test
        default void allGroupListenersShouldBeExecutedWhenAGroupListenerFails() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            EventListener failingListener = mock(EventListener.class);
            when(failingListener.getExecutionMode()).thenReturn(EventListener.ExecutionMode.SYNCHRONOUS);
            doThrow(new RuntimeException()).when(failingListener).event(any());

            eventBus().register(failingListener, EventBusTestFixture.GROUP_A);
            eventBus().register(listener, EventBusTestFixture.GROUP_B);

            eventBus().dispatch(EventBusTestFixture.EVENT, EventBusTestFixture.NO_KEYS).block();

            verify(listener, timeout(EventBusTestFixture.ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void allGroupListenersShouldBeExecutedWhenGenericGroups() throws Exception {
            EventListener listener1 = EventBusTestFixture.newListener();
            EventListener listener2 = EventBusTestFixture.newListener();

            eventBus().register(listener1, new GenericGroup("a"));
            eventBus().register(listener2, new GenericGroup("b"));

            eventBus().dispatch(EventBusTestFixture.EVENT, EventBusTestFixture.NO_KEYS).block();

            verify(listener1, timeout(EventBusTestFixture.ONE_SECOND.toMillis()).times(1)).event(any());
            verify(listener2, timeout(EventBusTestFixture.ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void groupListenerShouldReceiveEventWhenRedeliver() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().register(listener, EventBusTestFixture.GROUP_A);

            eventBus().reDeliver(EventBusTestFixture.GROUP_A, EventBusTestFixture.EVENT).block();

            verify(listener, timeout(EventBusTestFixture.ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void redeliverShouldNotThrowWhenAGroupListenerFails() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            doThrow(new RuntimeException()).when(listener).event(any());

            eventBus().register(listener, EventBusTestFixture.GROUP_A);

            assertThatCode(() -> eventBus().reDeliver(EventBusTestFixture.GROUP_A, EventBusTestFixture.EVENT).block())
                .doesNotThrowAnyException();
        }

        @Test
        default void redeliverShouldThrowWhenGroupNotRegistered() {
            assertThatThrownBy(() -> eventBus().reDeliver(EventBusTestFixture.GROUP_A, EventBusTestFixture.EVENT).block())
                .isInstanceOf(GroupRegistrationNotFound.class);
        }

        @Test
        default void redeliverShouldThrowAfterGroupIsUnregistered() {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().register(listener, EventBusTestFixture.GROUP_A).unregister();

            assertThatThrownBy(() -> eventBus().reDeliver(EventBusTestFixture.GROUP_A, EventBusTestFixture.EVENT).block())
                .isInstanceOf(GroupRegistrationNotFound.class);
        }

        @Test
        default void redeliverShouldOnlySendEventToDefinedGroup() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();
            EventListener listener2 = EventBusTestFixture.newListener();
            eventBus().register(listener, EventBusTestFixture.GROUP_A);
            eventBus().register(listener2, EventBusTestFixture.GROUP_B);

            eventBus().reDeliver(EventBusTestFixture.GROUP_A, EventBusTestFixture.EVENT).block();

            verify(listener, timeout(EventBusTestFixture.ONE_SECOND.toMillis()).times(1)).event(any());
            verify(listener2, after(EventBusTestFixture.FIVE_HUNDRED_MS.toMillis()).never()).event(any());
        }

        @Test
        default void groupListenersShouldNotReceiveNoopRedeliveredEvents() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().register(listener, EventBusTestFixture.GROUP_A);

            Username bob = Username.of("bob");
            Added noopEvent = new Added(MailboxSession.SessionId.of(18), bob, MailboxPath.forUser(bob, "mailbox"), TestId.of(58), ImmutableSortedMap.of(), Event.EventId.random());
            eventBus().reDeliver(EventBusTestFixture.GROUP_A, noopEvent).block();

            verify(listener, after(EventBusTestFixture.FIVE_HUNDRED_MS.toMillis()).never()).event(any());
        }
    }

    interface MultipleEventBusGroupContract extends EventBusContract.MultipleEventBusContract {

        @Test
        default void groupsDefinedOnlyOnSomeNodesShouldBeNotifiedWhenDispatch() throws Exception {
            EventListener mailboxListener = EventBusTestFixture.newListener();

            eventBus().register(mailboxListener, EventBusTestFixture.GROUP_A);

            eventBus2().dispatch(EventBusTestFixture.EVENT, EventBusTestFixture.NO_KEYS).block();

            verify(mailboxListener, timeout(EventBusTestFixture.ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void groupsDefinedOnlyOnSomeNodesShouldNotBeNotifiedWhenRedeliver() {
            EventListener mailboxListener = EventBusTestFixture.newListener();

            eventBus().register(mailboxListener, EventBusTestFixture.GROUP_A);

            assertThatThrownBy(() -> eventBus2().reDeliver(EventBusTestFixture.GROUP_A, EventBusTestFixture.EVENT).block())
                .isInstanceOf(GroupRegistrationNotFound.class);
        }

        @Test
        default void groupListenersShouldBeExecutedOnceWhenRedeliverInADistributedEnvironment() throws Exception {
            EventListener mailboxListener = EventBusTestFixture.newListener();

            eventBus().register(mailboxListener, EventBusTestFixture.GROUP_A);
            eventBus2().register(mailboxListener, EventBusTestFixture.GROUP_A);

            eventBus2().reDeliver(EventBusTestFixture.GROUP_A, EventBusTestFixture.EVENT).block();

            verify(mailboxListener, timeout(EventBusTestFixture.ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void groupListenersShouldBeExecutedOnceInAControlledEnvironment() throws Exception {
            EventListener mailboxListener = EventBusTestFixture.newListener();

            eventBus().register(mailboxListener, EventBusTestFixture.GROUP_A);
            eventBus2().register(mailboxListener, EventBusTestFixture.GROUP_A);

            eventBus2().dispatch(EventBusTestFixture.EVENT, EventBusTestFixture.NO_KEYS).block();

            verify(mailboxListener, timeout(EventBusTestFixture.ONE_SECOND.toMillis()).times(1)).event(any());
        }

        @Test
        default void unregisterShouldStopNotificationForDistantGroups() throws Exception {
            EventListener mailboxListener = EventBusTestFixture.newListener();

            eventBus().register(mailboxListener, EventBusTestFixture.GROUP_A).unregister();

            eventBus2().dispatch(EventBusTestFixture.EVENT, EventBusTestFixture.NO_KEYS).block();


            verify(mailboxListener, after(EventBusTestFixture.FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }

        @Test
        default void registerShouldNotDispatchPastEventsForGroupsInADistributedContext() throws Exception {
            EventListener listener = EventBusTestFixture.newListener();

            eventBus().dispatch(EventBusTestFixture.EVENT, EventBusTestFixture.NO_KEYS).block();

            eventBus2().register(listener, EventBusTestFixture.GROUP_A);

            verify(listener, after(EventBusTestFixture.FIVE_HUNDRED_MS.toMillis()).never())
                .event(any());
        }
    }
}
