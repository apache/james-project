package org.apache.james.imapserver.netty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class ReactiveThrottlerTest {
    @Test
    void throttleShouldExecuteSubmittedTasks() {
        // Given a throttler
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 2);

        // When I submit a task
        AtomicBoolean executed = new AtomicBoolean(false);
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then(Mono.fromRunnable(() -> executed.getAndSet(true))))).block();

        // Then that task is executed
        assertThat(executed.get()).isTrue();
    }

    @Test
    void throttleShouldNotExecuteQueuedTasksLogicRightAway() {
        // Given a throttler
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 2);

        // When I submit many tasks task
        AtomicBoolean executed = new AtomicBoolean(false);
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(200)).then())).subscribeOn(Schedulers.elastic()).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(200)).then())).subscribeOn(Schedulers.elastic()).subscribe();
        Mono.from(testee.throttle(Mono.fromRunnable(() -> executed.getAndSet(true)))).subscribeOn(Schedulers.elastic()).subscribe();

        // Then that task is not executed straight away
        assertThat(executed.get()).isFalse();
    }

    @Test
    void throttleShouldEventuallyExecuteQueuedTasks() {
        // Given a throttler
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 2);

        // When I submit many tasks task
        AtomicBoolean executed = new AtomicBoolean(false);
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then())).subscribeOn(Schedulers.elastic()).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then())).subscribeOn(Schedulers.elastic()).subscribe();
        Mono.from(testee.throttle(Mono.fromRunnable(() -> executed.getAndSet(true)))).subscribeOn(Schedulers.elastic()).subscribe();

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
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then())).subscribeOn(Schedulers.elastic()).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then())).subscribeOn(Schedulers.elastic()).subscribe();
        Mono.from(testee.throttle(Mono.fromRunnable(() -> executed.getAndSet(true)))).block();

        // Then when done that task have been executed
        assertThat(executed.get()).isTrue();
    }

    @Test
    void throttleShouldRejectTasksWhenTheQueueIsFull() {
        // Given a throttler
        ReactiveThrottler testee = new ReactiveThrottler(new NoopGaugeRegistry(), 2, 2);

        // When I submit too many tasks task
        AtomicBoolean executed = new AtomicBoolean(false);
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then())).subscribeOn(Schedulers.elastic()).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then())).subscribeOn(Schedulers.elastic()).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then())).subscribeOn(Schedulers.elastic()).subscribe();
        Mono.from(testee.throttle(Mono.delay(Duration.ofMillis(50)).then())).subscribeOn(Schedulers.elastic()).subscribe();

        // Then extra tasks are rejected
        assertThatThrownBy(() -> Mono.from(testee.throttle(Mono.fromRunnable(() -> executed.getAndSet(true)))).block())
            .isInstanceOf(ReactiveThrottler.RejectedException.class);
        // And the task is not executed
        assertThat(executed.get()).isFalse();
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
        Mono.from(testee.throttle(operation)).subscribeOn(Schedulers.elastic()).subscribe();
        Mono.from(testee.throttle(operation)).subscribeOn(Schedulers.elastic()).subscribe();
        Mono.from(testee.throttle(operation)).subscribeOn(Schedulers.elastic()).subscribe();
        Mono.from(testee.throttle(operation)).subscribeOn(Schedulers.elastic()).subscribe();

        // Then maximum parallelism is not exceeded
        Awaitility.await().untilAsserted(() -> assertThat(concurrentTasksCountSnapshots.size()).isEqualTo(8));
        assertThat(concurrentTasksCountSnapshots)
            .allSatisfy(i -> assertThat(i).isBetween(0, 2));
    }
}