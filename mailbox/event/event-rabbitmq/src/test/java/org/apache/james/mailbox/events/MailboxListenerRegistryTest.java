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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.mailbox.model.TestId;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MailboxListenerRegistryTest {
    private static final MailboxIdRegistrationKey KEY_1 = new MailboxIdRegistrationKey(TestId.of(42));
    private static final Runnable NOOP = () -> {
    };

    private MailboxListenerRegistry testee;

    @BeforeEach
    void setUp() {
        testee = new MailboxListenerRegistry();
    }

    @Test
    void getLocalMailboxListenersShouldReturnEmptyWhenNone() {
        assertThat(testee.getLocalMailboxListeners(KEY_1).collectList().block())
            .isEmpty();
    }

    @Test
    void getLocalMailboxListenersShouldReturnPreviouslyAddedListener() {
        MailboxListener listener = mock(MailboxListener.class);
        testee.addListener(KEY_1, listener, NOOP);

        assertThat(testee.getLocalMailboxListeners(KEY_1).collectList().block())
            .containsOnly(listener);
    }

    @Test
    void getLocalMailboxListenersShouldReturnPreviouslyAddedListeners() {
        MailboxListener listener1 = mock(MailboxListener.class);
        MailboxListener listener2 = mock(MailboxListener.class);
        testee.addListener(KEY_1, listener1, NOOP);
        testee.addListener(KEY_1, listener2, NOOP);

        assertThat(testee.getLocalMailboxListeners(KEY_1).collectList().block())
            .containsOnly(listener1, listener2);
    }

    @Test
    void getLocalMailboxListenersShouldNotReturnRemovedListeners() {
        MailboxListener listener1 = mock(MailboxListener.class);
        MailboxListener listener2 = mock(MailboxListener.class);
        testee.addListener(KEY_1, listener1, NOOP);
        testee.addListener(KEY_1, listener2, NOOP);

        testee.removeListener(KEY_1, listener2, NOOP);

        assertThat(testee.getLocalMailboxListeners(KEY_1).collectList().block())
            .containsOnly(listener1);
    }

    @Test
    void addListenerShouldRunTaskWhenNoPreviouslyRegisteredListeners() {
        MailboxListener listener = mock(MailboxListener.class);

        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        testee.addListener(KEY_1, listener, () -> atomicBoolean.set(true));

        assertThat(atomicBoolean).isTrue();
    }

    @Test
    void addListenerShouldNotRunTaskWhenPreviouslyRegisteredListeners() {
        MailboxListener listener = mock(MailboxListener.class);

        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        testee.addListener(KEY_1, listener, NOOP);
        testee.addListener(KEY_1, listener, () -> atomicBoolean.set(true));

        assertThat(atomicBoolean).isFalse();
    }

    @Test
    void removeListenerShouldNotRunTaskWhenNoListener() {
        MailboxListener listener = mock(MailboxListener.class);

        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        testee.removeListener(KEY_1, listener, () -> atomicBoolean.set(true));

        assertThat(atomicBoolean).isFalse();
    }

    @Test
    void removeListenerShouldNotRunTaskWhenSeveralListener() {
        MailboxListener listener = mock(MailboxListener.class);
        MailboxListener listener2 = mock(MailboxListener.class);

        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        testee.addListener(KEY_1, listener, NOOP);
        testee.addListener(KEY_1, listener2, NOOP);
        testee.removeListener(KEY_1, listener, () -> atomicBoolean.set(true));

        assertThat(atomicBoolean).isFalse();
    }

    @Test
    void removeListenerShouldRunTaskWhenOneListener() {
        MailboxListener listener = mock(MailboxListener.class);

        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        testee.addListener(KEY_1, listener, NOOP);
        testee.removeListener(KEY_1, listener, () -> atomicBoolean.set(true));

        assertThat(atomicBoolean).isTrue();
    }

    @Nested
    class ConcurrentTest {
        private final Duration ONE_SECOND = Duration.ofSeconds(1);

        @Test
        void getLocalMailboxListenersShouldReturnPreviousAddedListener() throws Exception {
            MailboxListener listener = mock(MailboxListener.class);

            ConcurrentTestRunner.builder()
                .operation((threadNumber, operationNumber) -> testee.addListener(KEY_1, listener, NOOP))
                .threadCount(10)
                .operationCount(10)
                .runSuccessfullyWithin(ONE_SECOND);

            assertThat(testee.getLocalMailboxListeners(KEY_1).collectList().block())
                .containsOnly(listener);
        }

        @Test
        void getLocalMailboxListenersShouldReturnAllPreviousAddedListeners() throws Exception {
            MailboxListener listener1 = mock(MailboxListener.class);
            MailboxListener listener2 = mock(MailboxListener.class);
            MailboxListener listener3 = mock(MailboxListener.class);

            ConcurrentTestRunner.builder()
                .operation((threadNumber, operationNumber) -> {
                    if (threadNumber % 3 == 0) {
                        testee.addListener(KEY_1, listener1, NOOP);
                    } else if (threadNumber % 3 == 1) {
                        testee.addListener(KEY_1, listener2, NOOP);
                    } else if (threadNumber % 3 == 2) {
                        testee.addListener(KEY_1, listener3, NOOP);
                    }
                })
                .threadCount(6)
                .operationCount(10)
                .runSuccessfullyWithin(ONE_SECOND);

            assertThat(testee.getLocalMailboxListeners(KEY_1).collectList().block())
                .containsOnly(listener1, listener2, listener3);
        }

        @Test
        void getLocalMailboxListenersShouldReturnEmptyWhenRemoveAddedListener() throws Exception {
            MailboxListener listener1 = mock(MailboxListener.class);

            testee.addListener(KEY_1, listener1, NOOP);

            ConcurrentTestRunner.builder()
                .operation(((threadNumber, operationNumber) ->
                    testee.removeListener(KEY_1, listener1, NOOP)))
                .threadCount(10)
                .operationCount(10)
                .runSuccessfullyWithin(ONE_SECOND);

            assertThat(testee.getLocalMailboxListeners(KEY_1).collectList().block())
                .isEmpty();
        }

        @Test
        void addListenerOnlyRunTaskOnceForEmptyRegistry() throws Exception {
            MailboxListener listener1 = mock(MailboxListener.class);
            MailboxListener listener2 = mock(MailboxListener.class);
            MailboxListener listener3 = mock(MailboxListener.class);

            AtomicInteger runIfEmptyCount = new AtomicInteger(0);

            ConcurrentTestRunner.builder()
                .operation((threadNumber, operationNumber) -> {
                    if (threadNumber % 3 == 0) {
                        testee.addListener(KEY_1, listener1, runIfEmptyCount::incrementAndGet);
                    } else if (threadNumber % 3 == 1) {
                        testee.addListener(KEY_1, listener2, runIfEmptyCount::incrementAndGet);
                    } else if (threadNumber % 3 == 2) {
                        testee.addListener(KEY_1, listener3, runIfEmptyCount::incrementAndGet);
                    }
                })
                .threadCount(6)
                .operationCount(10)
                .runSuccessfullyWithin(ONE_SECOND);

            assertThat(runIfEmptyCount.get()).isEqualTo(1);
        }

        @Test
        void removeListenerOnlyRunTaskOnceForEmptyRegistry() throws Exception {
            MailboxListener listener1 = mock(MailboxListener.class);
            AtomicInteger runIfEmptyCount = new AtomicInteger(0);

            testee.addListener(KEY_1, listener1, NOOP);
            ConcurrentTestRunner.builder()
                .operation(((threadNumber, operationNumber) -> testee.removeListener(KEY_1, listener1, runIfEmptyCount::incrementAndGet)))
                .threadCount(10)
                .operationCount(10)
                .runSuccessfullyWithin(ONE_SECOND);

            assertThat(runIfEmptyCount.get()).isEqualTo(1);
        }
    }
}