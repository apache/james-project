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

import static org.apache.james.events.EventListener.wrapReactive;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.mailbox.model.TestId;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class LocalListenerRegistryTest {
    private static final MailboxIdRegistrationKey KEY_1 = new MailboxIdRegistrationKey(TestId.of(42));

    private LocalListenerRegistry testee;

    @BeforeEach
    void setUp() {
        testee = new LocalListenerRegistry();
    }

    @Test
    void getLocalMailboxListenersShouldReturnEmptyWhenNone() {
        assertThat(testee.getLocalMailboxListeners(KEY_1).collectList().block())
            .isEmpty();
    }

    @Test
    void getLocalMailboxListenersShouldReturnPreviouslyAddedListener() {
        EventListener listener = event -> { };
        testee.addListener(KEY_1, listener);

        assertThat(testee.getLocalMailboxListeners(KEY_1).collectList().block())
            .containsOnly(wrapReactive(listener));
    }

    @Test
    void getLocalMailboxListenersShouldReturnPreviouslyAddedListeners() {
        EventListener listener1 = event -> { };
        EventListener listener2 = event -> { };
        testee.addListener(KEY_1, listener1);
        testee.addListener(KEY_1, listener2);

        assertThat(testee.getLocalMailboxListeners(KEY_1).collectList().block())
            .containsOnly(wrapReactive(listener1), wrapReactive(listener2));
    }

    @Test
    void getLocalMailboxListenersShouldNotReturnRemovedListeners() {
        EventListener listener1 = event -> { };
        EventListener listener2 = event -> { };
        testee.addListener(KEY_1, listener1);
        LocalListenerRegistry.LocalRegistration registration = testee.addListener(KEY_1, listener2);

        registration.unregister();

        assertThat(testee.getLocalMailboxListeners(KEY_1).collectList().block())
            .containsOnly(wrapReactive(listener1));
    }

    @Test
    void addListenerShouldReturnFirstListenerWhenNoPreviouslyRegisteredListeners() {
        EventListener listener = event -> { };

        assertThat(testee.addListener(KEY_1, listener).isFirstListener()).isTrue();
    }

    @Test
    void addListenerShouldNotReturnFirstListenerWhenPreviouslyRegisteredListeners() {
        EventListener listener = event -> { };
        EventListener listener2 = event -> { };

        testee.addListener(KEY_1, listener);

        assertThat(testee.addListener(KEY_1, listener2).isFirstListener()).isFalse();
    }

    @Test
    void removeListenerShouldNotReturnLastListenerRemovedWhenSeveralListener() {
        EventListener listener = event -> { };
        EventListener listener2 = event -> { };

        LocalListenerRegistry.LocalRegistration registration = testee.addListener(KEY_1, listener);
        testee.addListener(KEY_1, listener2);

        assertThat(registration.unregister().lastListenerRemoved()).isFalse();
    }

    @Test
    void removeListenerShouldReturnLastListenerRemovedWhenOneListener() {
        EventListener listener = event -> { };


        LocalListenerRegistry.LocalRegistration registration = testee.addListener(KEY_1, listener);

        assertThat(registration.unregister().lastListenerRemoved()).isTrue();
    }

    @Nested
    class ConcurrentTest {
        private final Duration oneSecond = Duration.ofSeconds(1);

        @Test
        void getLocalMailboxListenersShouldReturnPreviousAddedListener() throws Exception {
            EventListener listener = event -> { };

            ConcurrentTestRunner.builder()
                .operation((threadNumber, operationNumber) -> testee.addListener(KEY_1, listener))
                .threadCount(10)
                .operationCount(10)
                .runSuccessfullyWithin(oneSecond);

            assertThat(testee.getLocalMailboxListeners(KEY_1).collectList().block())
                .containsOnly(wrapReactive(listener));
        }

        @Test
        void getLocalMailboxListenersShouldReturnAllPreviousAddedListeners() throws Exception {
            EventListener listener1 = event -> { };
            EventListener listener2 = event -> { };
            EventListener listener3 = event -> { };

            ConcurrentTestRunner.builder()
                .randomlyDistributedOperations(
                    (threadNumber, operationNumber) -> testee.addListener(KEY_1, listener1),
                    (threadNumber, operationNumber) -> testee.addListener(KEY_1, listener2),
                    (threadNumber, operationNumber) -> testee.addListener(KEY_1, listener3))
                .threadCount(6)
                .operationCount(10)
                .runSuccessfullyWithin(oneSecond);

            assertThat(testee.getLocalMailboxListeners(KEY_1).collectList().block())
                .containsOnly(wrapReactive(listener1), wrapReactive(listener2), wrapReactive(listener3));
        }

        @Test
        void getLocalMailboxListenersShouldReturnEmptyWhenRemoveAddedListener() throws Exception {
            EventListener listener1 = event -> { };

            LocalListenerRegistry.LocalRegistration registration = testee.addListener(KEY_1, listener1);

            ConcurrentTestRunner.builder()
                .operation(((threadNumber, operationNumber) -> registration.unregister()))
                .threadCount(10)
                .operationCount(10)
                .runSuccessfullyWithin(oneSecond);

            assertThat(testee.getLocalMailboxListeners(KEY_1).collectList().block())
                .isEmpty();
        }

        @Test
        void addListenerOnlyReturnIsFirstListenerForEmptyRegistry() throws Exception {
            EventListener listener1 = event -> { };
            EventListener listener2 = event -> { };
            EventListener listener3 = event -> { };

            AtomicInteger firstListenerCount = new AtomicInteger(0);

            ConcurrentTestRunner.builder()
                .randomlyDistributedOperations((threadNumber, operationNumber) -> {
                        LocalListenerRegistry.LocalRegistration registration = testee.addListener(KEY_1, listener1);
                        if (registration.isFirstListener()) {
                            firstListenerCount.incrementAndGet();
                        }
                    },
                    (threadNumber, operationNumber) -> {
                        LocalListenerRegistry.LocalRegistration registration = testee.addListener(KEY_1, listener2);
                        if (registration.isFirstListener()) {
                            firstListenerCount.incrementAndGet();
                        }
                    },
                    (threadNumber, operationNumber) -> {
                        LocalListenerRegistry.LocalRegistration registration = testee.addListener(KEY_1, listener3);
                        if (registration.isFirstListener()) {
                            firstListenerCount.incrementAndGet();
                        }
                    })
                .threadCount(6)
                .operationCount(10)
                .runSuccessfullyWithin(oneSecond);

            assertThat(firstListenerCount.get()).isEqualTo(1);
        }

        @Test
        void removeListenerOnlyReturnLastListenerRemovedForEmptyRegistry() throws Exception {
            EventListener listener1 = event -> { };
            AtomicInteger lastListenerRemoved = new AtomicInteger(0);

            LocalListenerRegistry.LocalRegistration registration = testee.addListener(KEY_1, listener1);
            ConcurrentTestRunner.builder()
                .operation(((threadNumber, operationNumber) -> {
                    if (registration.unregister().lastListenerRemoved()) {
                        lastListenerRemoved.incrementAndGet();
                    }
                }))
                .threadCount(10)
                .operationCount(10)
                .runSuccessfullyWithin(oneSecond);

            assertThat(lastListenerRemoved.get()).isEqualTo(1);
        }

        @Test
        void iterationShouldPerformOnASnapshotOfListenersSet() {
            EventListener listener1 = event -> { };
            EventListener listener2 = event -> { };
            EventListener listener3 = event -> { };
            EventListener listener4 = event -> { };
            EventListener listener5 = event -> { };

            testee.addListener(KEY_1, listener1);
            testee.addListener(KEY_1, listener2);
            testee.addListener(KEY_1, listener3);
            testee.addListener(KEY_1, listener4);
            LocalListenerRegistry.LocalRegistration registration5 = testee.addListener(KEY_1, listener5);

            Mono<List<EventListener.ReactiveEventListener>> listeners = testee.getLocalMailboxListeners(KEY_1)
                .publishOn(Schedulers.elastic())
                .delayElements(Duration.ofMillis(100))
                .collectList();

            registration5.unregister();

            assertThat(listeners.block(Duration.ofSeconds(10))).hasSize(5);
        }
    }
}