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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.metrics.api.Gauge;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

    @RepeatedTest(10)
    void shouldPropagateCancel() throws Exception {
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 5);
        CountDownLatch latch = new CountDownLatch(1);

        // Given a throttler

        // When I submit many tasks task - they will get queued
        AtomicBoolean executed = new AtomicBoolean(false);
        Disposable disposable1 = Mono.from(testee.throttle(Mono.fromRunnable(Throwing.runnable(latch::await)).subscribeOn(Schedulers.boundedElastic()).then(), NO_IMAP_MESSAGE)).subscribe();
        Disposable disposable2 = Mono.from(testee.throttle(Mono.fromRunnable(Throwing.runnable(latch::await)).subscribeOn(Schedulers.boundedElastic()).then(), NO_IMAP_MESSAGE)).subscribe();
        Disposable disposable3 = Mono.from(testee.throttle(Mono.fromRunnable(Throwing.runnable(latch::await)).subscribeOn(Schedulers.boundedElastic()).then(), NO_IMAP_MESSAGE)).subscribe();
        Disposable disposable4 = Mono.from(testee.throttle(Mono.fromRunnable(Throwing.runnable(latch::await)).subscribeOn(Schedulers.boundedElastic()).then(), NO_IMAP_MESSAGE)).subscribe();
        Disposable disposable5 = Mono.from(testee.throttle(Mono.fromRunnable(Throwing.runnable(latch::await)).subscribeOn(Schedulers.boundedElastic()).then(), NO_IMAP_MESSAGE)).subscribe();
        Disposable disposable6 = Mono.from(testee.throttle(Mono.fromRunnable(Throwing.runnable(latch::await)).subscribeOn(Schedulers.boundedElastic()).then(), NO_IMAP_MESSAGE)).subscribe();
        Disposable disposable7 = Mono.from(testee.throttle(Mono.fromRunnable(Throwing.runnable(latch::await)).subscribeOn(Schedulers.boundedElastic()).then(), NO_IMAP_MESSAGE)).subscribe();

        disposable7.dispose();
        disposable6.dispose();
        disposable5.dispose();
        disposable4.dispose();
        disposable3.dispose();
        disposable2.dispose();
        disposable1.dispose();
        Thread.sleep(200);

        Mono.from(testee.throttle(Mono.fromRunnable(() -> executed.getAndSet(true)), NO_IMAP_MESSAGE)).block();

        // Then that task is not executed straight away
        assertThat(executed.get()).isTrue();
    }

    @RepeatedTest(10)
    void shouldPropagateCancelInReverseOrder() throws Exception {
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 5);
        CountDownLatch latch = new CountDownLatch(1);

        // Given a throttler

        // When I submit many tasks task - they will get queued
        AtomicBoolean executed = new AtomicBoolean(false);
        Disposable disposable1 = Mono.from(testee.throttle(Mono.fromRunnable(Throwing.runnable(latch::await)).subscribeOn(Schedulers.boundedElastic()).then(), NO_IMAP_MESSAGE)).subscribe();
        Disposable disposable2 = Mono.from(testee.throttle(Mono.fromRunnable(Throwing.runnable(latch::await)).subscribeOn(Schedulers.boundedElastic()).then(), NO_IMAP_MESSAGE)).subscribe();
        Disposable disposable3 = Mono.from(testee.throttle(Mono.fromRunnable(Throwing.runnable(latch::await)).subscribeOn(Schedulers.boundedElastic()).then(), NO_IMAP_MESSAGE)).subscribe();
        Disposable disposable4 = Mono.from(testee.throttle(Mono.fromRunnable(Throwing.runnable(latch::await)).subscribeOn(Schedulers.boundedElastic()).then(), NO_IMAP_MESSAGE)).subscribe();
        Disposable disposable5 = Mono.from(testee.throttle(Mono.fromRunnable(Throwing.runnable(latch::await)).subscribeOn(Schedulers.boundedElastic()).then(), NO_IMAP_MESSAGE)).subscribe();
        Disposable disposable6 = Mono.from(testee.throttle(Mono.fromRunnable(Throwing.runnable(latch::await)).subscribeOn(Schedulers.boundedElastic()).then(), NO_IMAP_MESSAGE)).subscribe();
        Disposable disposable7 = Mono.from(testee.throttle(Mono.fromRunnable(Throwing.runnable(latch::await)).subscribeOn(Schedulers.boundedElastic()).then(), NO_IMAP_MESSAGE)).subscribe();

        disposable1.dispose();
        disposable2.dispose();
        disposable3.dispose();
        disposable4.dispose();
        disposable5.dispose();
        disposable6.dispose();
        disposable7.dispose();
        Thread.sleep(200);

        Mono.from(testee.throttle(Mono.fromRunnable(() -> executed.getAndSet(true)), NO_IMAP_MESSAGE)).block();

        // Then that task is not executed straight away
        assertThat(executed.get()).isTrue();
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

    @RepeatedTest(10)
    void concurrencyLimitShouldBeRespectedAfterCancellingQueuedTasks() throws Exception {
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 10);
        CountDownLatch blocker = new CountDownLatch(1);

        // Fill both concurrent slots with tasks that block until we say so
        Mono.from(testee.throttle(
            Mono.fromRunnable(Throwing.runnable(blocker::await)).subscribeOn(Schedulers.boundedElastic()).then(),
            NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(
            Mono.fromRunnable(Throwing.runnable(blocker::await)).subscribeOn(Schedulers.boundedElastic()).then(),
            NO_IMAP_MESSAGE)).subscribe();

        // Queue 5 tasks then cancel each one before it is dispatched
        for (int i = 0; i < 5; i++) {
            Mono.from(testee.throttle(Mono.delay(Duration.ofSeconds(10)).then(), NO_IMAP_MESSAGE))
                .subscribe()
                .dispose();
        }
        Thread.sleep(100); // Let cancellation callbacks propagate

        // Release the blocking tasks
        blocker.countDown();
        Thread.sleep(200);

        // Submit new tasks and verify parallelism never exceeds maxConcurrentRequests = 2
        AtomicInteger concurrent = new AtomicInteger(0);
        ConcurrentLinkedDeque<Integer> snapshots = new ConcurrentLinkedDeque<>();
        Mono<Void> measured = Mono.fromRunnable(() -> snapshots.add(concurrent.incrementAndGet()))
            .then(Mono.delay(Duration.ofMillis(50)))
            .then(Mono.fromRunnable(() -> snapshots.add(concurrent.getAndDecrement())));

        for (int i = 0; i < 6; i++) {
            Mono.from(testee.throttle(measured, NO_IMAP_MESSAGE))
                .onErrorResume(ReactiveThrottler.RejectedException.class, e -> Mono.empty())
                .subscribe();
        }

        Awaitility.await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(snapshots.size()).isGreaterThanOrEqualTo(8));

        assertThat(snapshots).allSatisfy(count -> assertThat(count).isBetween(0, 2));
    }

    @Test
    void queuedTaskErrorShouldPropagateToOuterSubscriber() {
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 5);

        // Fill both concurrent slots so the next task is queued
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(200)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(200)).then(), NO_IMAP_MESSAGE)).subscribe();

        AtomicBoolean signalReceived = new AtomicBoolean(false);
        Mono.from(testee.throttle(
                Mono.error(new RuntimeException("simulated task failure")),
                NO_IMAP_MESSAGE))
            .doOnError(e -> signalReceived.set(true))
            .doOnSuccess(v -> signalReceived.set(true))
            .onErrorResume(e -> Mono.empty())
            .subscribe();

        // The outer subscriber must receive either onComplete or onError within a reasonable time
        Awaitility.await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(signalReceived.get()).isTrue());
    }

    @Test
    void queuedTaskErrorShouldFreeThrottlerSlotForSubsequentTasks() {
        // A queued task that errors must still release its concurrency slot via doFinally,
        // so the tasks behind it in the queue are not permanently stalled.
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 5);

        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(100)).then(), NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(100)).then(), NO_IMAP_MESSAGE)).subscribe();

        // A queued task that will fail when dispatched
        Mono.from(testee.throttle(
                Mono.error(new RuntimeException("task error")),
                NO_IMAP_MESSAGE))
            .onErrorResume(e -> Mono.empty())
            .subscribe();

        // A normal task queued after the failing one
        AtomicBoolean executed = new AtomicBoolean(false);
        Mono.from(testee.throttle(Mono.fromRunnable(() -> executed.set(true)), NO_IMAP_MESSAGE))
            .subscribe();

        Awaitility.await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(executed.get()).isTrue());
    }

    @Test
    void cancelledQueuedTaskShouldNotPreventSubsequentTaskExecution() throws Exception {
        // Verifies that a mix of cancelled and completed queued tasks does not leave
        // the throttler in a state where later tasks are permanently stalled.
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 20);
        CountDownLatch blocker = new CountDownLatch(1);

        Mono.from(testee.throttle(
            Mono.fromRunnable(Throwing.runnable(blocker::await)).subscribeOn(Schedulers.boundedElastic()).then(),
            NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(
            Mono.fromRunnable(Throwing.runnable(blocker::await)).subscribeOn(Schedulers.boundedElastic()).then(),
            NO_IMAP_MESSAGE)).subscribe();

        // Mix: some queued tasks cancelled, some left to run normally.
        // Short delay so non-cancelled tasks complete well within the test timeout.
        for (int i = 0; i < 8; i++) {
            Disposable d = Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(100)).then(), NO_IMAP_MESSAGE))
                .subscribe();
            if (i % 2 == 0) {
                d.dispose();
            }
        }

        blocker.countDown();
        Thread.sleep(1000); // Enough for all 4 non-cancelled 100ms tasks to drain (2 concurrent max → 200ms min)

        AtomicInteger executionCount = new AtomicInteger(0);
        for (int i = 0; i < 4; i++) {
            Mono.from(testee.throttle(Mono.fromRunnable(executionCount::incrementAndGet), NO_IMAP_MESSAGE))
                .onErrorResume(ReactiveThrottler.RejectedException.class, e -> Mono.empty())
                .subscribe();
        }

        Awaitility.await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(executionCount.get()).isEqualTo(4));
    }

    @Test
    void concurrentRequestsGaugeShouldBeZeroWhenIdle() throws Exception {
        AtomicReference<Gauge<Integer>> concurrentCountGauge = new AtomicReference<>();
        GaugeRegistry capturingRegistry = new GaugeRegistry() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> GaugeRegistry register(String name, Gauge<T> gauge) {
                if (name.equals("imap.request.concurrent.count")) {
                    concurrentCountGauge.set((Gauge<Integer>) gauge);
                }
                return this;
            }

            @Override
            public <T> GaugeRegistry.SettableGauge<T> settableGauge(String name) {
                return value -> { };
            }
        };
        ReactiveThrottler testee = new ReactiveThrottler(capturingRegistry, 2, 10);

        // Stress the counter: fill slots, queue tasks, cancel them all
        CountDownLatch blocker = new CountDownLatch(1);
        Mono.from(testee.throttle(
            Mono.fromRunnable(Throwing.runnable(blocker::await)).subscribeOn(Schedulers.boundedElastic()).then(),
            NO_IMAP_MESSAGE)).subscribe();
        Mono.from(testee.throttle(
            Mono.fromRunnable(Throwing.runnable(blocker::await)).subscribeOn(Schedulers.boundedElastic()).then(),
            NO_IMAP_MESSAGE)).subscribe();
        for (int i = 0; i < 5; i++) {
            Mono.from(testee.throttle(Mono.delay(Duration.ofSeconds(10)).then(), NO_IMAP_MESSAGE))
                .subscribe()
                .dispose();
        }
        Thread.sleep(100);
        blocker.countDown();

        // Wait for the throttler to fully drain
        Awaitility.await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(concurrentCountGauge.get().get()).isZero());
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