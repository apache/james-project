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

package org.apache.james.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

class TimeoutOnPendingDemandTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration LONGER_THAN_TIMEOUT = TIMEOUT.multipliedBy(3);
    private static final long NO_INITIAL_REQUEST = 0;
    private static final long ONE = 1;
    private static final long UNBOUNDED = Long.MAX_VALUE;

    // The default deferred virtual time scheduler considers tasks scheduled after an idle time advance as already due.
    // A non deferred scheduler is required to schedule timers upon late requests.
    private static <T> StepVerifier.FirstStep<T> withVirtualTime(Supplier<Flux<T>> scenario, long initialRequest) {
        return StepVerifier.withVirtualTime(scenario, () -> VirtualTimeScheduler.getOrSet(false), initialRequest);
    }

    @Test
    void shouldPropagateItemsAndCompletion() {
        withVirtualTime(() -> Flux.range(1, 3).transform(TimeoutOnPendingDemand.of(TIMEOUT)), UNBOUNDED)
            .expectNext(1, 2, 3)
            .verifyComplete();
    }

    @Test
    void shouldPropagateErrors() {
        RuntimeException failure = new RuntimeException("boom");

        withVirtualTime(() -> Flux.<Integer>error(failure).transform(TimeoutOnPendingDemand.of(TIMEOUT)), UNBOUNDED)
            .verifyErrorMatches(error -> error == failure);
    }

    @Test
    void shouldNotTimeoutWhenTheConsumerDoesNotRequestMoreItems() {
        withVirtualTime(() -> Flux.range(1, 3).transform(TimeoutOnPendingDemand.of(TIMEOUT)), ONE)
            .expectNext(1)
            .expectNoEvent(LONGER_THAN_TIMEOUT)
            .thenRequest(ONE)
            .expectNext(2)
            .expectNoEvent(LONGER_THAN_TIMEOUT)
            .thenRequest(ONE)
            .expectNext(3)
            .verifyComplete();
    }

    @Test
    void shouldNotTimeoutWhenNoDemandWasEverExpressed() {
        withVirtualTime(() -> Flux.<Integer>never().transform(TimeoutOnPendingDemand.of(TIMEOUT)), NO_INITIAL_REQUEST)
            .expectSubscription()
            .expectNoEvent(LONGER_THAN_TIMEOUT)
            .thenCancel()
            .verify();
    }

    @Test
    void shouldNotTimeoutWhenItemsKeepArrivingWithinTheTimeout() {
        Duration interval = TIMEOUT.dividedBy(2);

        withVirtualTime(() -> Flux.interval(interval).take(3).transform(TimeoutOnPendingDemand.of(TIMEOUT)), UNBOUNDED)
            .thenAwait(interval.multipliedBy(3))
            .expectNext(0L, 1L, 2L)
            .verifyComplete();
    }

    @Test
    void shouldTimeoutWhenTheSourceDoesNotEmitWhileDemandIsPending() {
        withVirtualTime(() -> Flux.<Integer>never().transform(TimeoutOnPendingDemand.of(TIMEOUT)), UNBOUNDED)
            .expectSubscription()
            .thenAwait(TIMEOUT)
            .verifyError(TimeoutException.class);
    }

    @Test
    void shouldCancelTheSourceUponTimeout() {
        AtomicBoolean cancelled = new AtomicBoolean(false);

        withVirtualTime(() -> Flux.<Integer>never().doOnCancel(() -> cancelled.set(true)).transform(TimeoutOnPendingDemand.of(TIMEOUT)), UNBOUNDED)
            .expectSubscription()
            .thenAwait(TIMEOUT)
            .verifyError(TimeoutException.class);

        assertThat(cancelled).isTrue();
    }

    @Test
    void shouldTimeoutWhenTheSourceStallsAfterSomeItemsWhileDemandIsPending() {
        withVirtualTime(() -> Flux.concat(Flux.just(1), Flux.never()).transform(TimeoutOnPendingDemand.of(TIMEOUT)), UNBOUNDED)
            .expectNext(1)
            .expectNoEvent(TIMEOUT.minusMillis(1))
            .thenAwait(Duration.ofMillis(1))
            .verifyError(TimeoutException.class);
    }

    @Test
    void shouldStartCountingWhenDemandIsExpressed() {
        withVirtualTime(() -> Flux.<Integer>never().transform(TimeoutOnPendingDemand.of(TIMEOUT)), NO_INITIAL_REQUEST)
            .expectSubscription()
            .expectNoEvent(LONGER_THAN_TIMEOUT)
            .thenRequest(ONE)
            .expectNoEvent(TIMEOUT.minusMillis(1))
            .thenAwait(Duration.ofMillis(1))
            .verifyError(TimeoutException.class);
    }

    @Test
    void shouldRestartCountingFromTheLastDeliveredItem() {
        Duration halfTimeout = TIMEOUT.dividedBy(2);

        withVirtualTime(() -> Flux.concat(Flux.just(1).delayElements(halfTimeout), Flux.never()).transform(TimeoutOnPendingDemand.of(TIMEOUT)), UNBOUNDED)
            .expectSubscription()
            .thenAwait(halfTimeout)
            .expectNext(1)
            .expectNoEvent(TIMEOUT.minusMillis(1))
            .thenAwait(Duration.ofMillis(1))
            .verifyError(TimeoutException.class);
    }
}
