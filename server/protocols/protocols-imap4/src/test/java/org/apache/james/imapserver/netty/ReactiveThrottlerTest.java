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

package org.apache.james.imapserver.netty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;

class ReactiveThrottlerTest {

    private static final ImapMessage NO_IMAP_MESSAGE = null;

    @Test
    void throttleShouldExecuteSubmittedTasks() {
        // Given a throttler
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 2);

        // When I submit a task
        AtomicBoolean executed = new AtomicBoolean(false);
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(Mono.fromRunnable(() -> executed.getAndSet(true))), NO_IMAP_MESSAGE)).block();

        // Then that task is executed
        assertThat(executed.get()).isTrue();
    }

    @Test
    void throttleShouldNotExecuteQueuedTasksLogicRightAway() {
        // Given a throttler
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 2);

        // When I submit many tasks task
        AtomicBoolean executed = new AtomicBoolean(false);
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(200)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(200)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.fromRunnable(() -> executed.getAndSet(true)), NO_IMAP_MESSAGE)).subscribe();

        // Then that task is not executed straight away
        assertThat(executed.get()).isFalse();
    }

    @Test
    void throttleShouldEventuallyExecuteQueuedTasks() {
        // Given a throttler
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 2);

        // When I submit many tasks task
        AtomicBoolean executed = new AtomicBoolean(false);
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.fromRunnable(() -> executed.getAndSet(true)), NO_IMAP_MESSAGE)).subscribe();

        // Then that task is eventually executed
        Awaitility.await().atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> assertThat(executed.get()).isTrue());
    }

    @Test
    void throttleShouldCompleteWhenSubmittedTaskCompletes() {
        // Given a throttler
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 2);

        // When I await a submitted task execution and it is queued
        AtomicBoolean executed = new AtomicBoolean(false);
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.fromRunnable(() -> executed.getAndSet(true)), NO_IMAP_MESSAGE)).block();

        // Then when done that task have been executed
        assertThat(executed.get()).isTrue();
    }

    @Test
    void throttleShouldRejectTasksWhenTheQueueIsFull() {
        // Given a throttler
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 2);

        // When I submit too many tasks task
        AtomicBoolean executed = new AtomicBoolean(false);
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();

        // Then extra tasks are rejected
        assertThatThrownBy(() -> Mono.from(testee.throttle(Mono.fromRunnable(() -> executed.getAndSet(true)), NO_IMAP_MESSAGE)).block())
            .isInstanceOf(ReactiveThrottler.RejectedException.class);
        // And the task is not executed
        assertThat(executed.get()).isFalse();
    }
    @Test
    void throttleShouldRecoverFromABurst() throws Exception {
        // Given a throttler
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 2);

        // When I submit too many tasks task
        AtomicBoolean executed = new AtomicBoolean(false);
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();

        Thread.sleep(500);
        Mono.from(testee.throttle(Mono.fromRunnable(() -> executed.getAndSet(true)), NO_IMAP_MESSAGE)).block();
        // And the task is executed
        assertThat(executed.get()).isTrue();
    }

    @Test
    void throttleShouldHandleDisposal() throws Exception {
        // Given a throttler
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 2);

        // When I submit too many tasks task
        AtomicBoolean executed = new AtomicBoolean(false);
        Disposable subscribe1 = Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Disposable subscribe2 = Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Disposable subscribe3 = Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Disposable subscribe4 = Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Disposable subscribe5 = Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Disposable subscribe6 = Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Disposable subscribe7 = Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        Disposable subscribe8 = Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE)).subscribe();
        subscribe1.dispose();
        subscribe2.dispose();
        subscribe3.dispose();
        subscribe4.dispose();
        subscribe5.dispose();
        subscribe6.dispose();
        subscribe7.dispose();
        subscribe8.dispose();

        Thread.sleep(100);

        Mono.from(testee.throttle(Mono.fromRunnable(() -> executed.getAndSet(true)), NO_IMAP_MESSAGE)).block();
        // And the task is executed
        assertThat(executed.get()).isTrue();
    }

    @RepeatedTest(10)
    void throttleShouldBeConcurrentFriendly() throws Exception {
        // Given a throttler
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 2);

        ConcurrentTestRunner.builder()
            .operation((a, b) -> Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(), NO_IMAP_MESSAGE))
                .onErrorResume(ReactiveThrottler.RejectedException.class, e -> Mono.empty())
                .block())
            .threadCount(20)
            .operationCount(5)
            .runSuccessfullyWithin(Duration.ofSeconds(10));

        Thread.sleep(100);

        AtomicBoolean executed = new AtomicBoolean(false);
        Mono.from(testee.throttle(Mono.fromRunnable(() -> executed.getAndSet(true)), NO_IMAP_MESSAGE)).block();
        // And the task is executed
        assertThat(executed.get()).isTrue();
    }

    @Test
    void throttleShouldNotAwaitOtherTasks() throws Exception {
        // Given a throttler
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 2);

        // When I submit a short and a long task
        AtomicBoolean executed = new AtomicBoolean(false);
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(100)).then(), NO_IMAP_MESSAGE))
            .then(Mono.fromRunnable(() -> executed.getAndSet(true)))

            .subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofSeconds(2)).then(), NO_IMAP_MESSAGE)).subscribe();

        // Then extra tasks are rejected
        Thread.sleep(200);
        assertThat(executed.get()).isTrue();
    }

    @Test
    void throttleShouldNotExceedItsConcurrency() {
        // Given a throttler
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 2);

        // When I submit many tasks task
        AtomicInteger concurrentTasks = new AtomicInteger(0);
        ConcurrentLinkedDeque<Integer> concurrentTasksCountSnapshots = new ConcurrentLinkedDeque<>();

        Mono<Void> operation = Mono.fromRunnable(() -> {
            int i = concurrentTasks.incrementAndGet();
            concurrentTasksCountSnapshots.add(i);
        }).then(Mono.delay(Duration.ofMillis(50)))
            .then(Mono.fromRunnable(() -> {
                int i = concurrentTasks.getAndDecrement();
                concurrentTasksCountSnapshots.add(i);
            }));
        Mono.from(testee.throttle(operation, NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(operation, NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(operation, NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(operation, NO_IMAP_MESSAGE)).subscribe();

        // Then maximum parallelism is not exceeded
        Awaitility.await().untilAsserted(() -> assertThat(concurrentTasksCountSnapshots.size()).isEqualTo(8));
        assertThat(concurrentTasksCountSnapshots)
            .allSatisfy(i -> assertThat(i).isBetween(0, 2));
    }
}