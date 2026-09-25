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

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Operators;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

/**
 * A demand aware alternative to {@link Flux#timeout(Duration)}.
 * <p>
 * {@link Flux#timeout(Duration)} restarts its timer after each emitted item, regardless of whether the downstream
 * subscriber asked for more items. A slow consumer relying on backpressure would thus wrongly trigger the timeout
 * while the source is only waiting for demand.
 * <p>
 * This operator only raises a {@link TimeoutException} when the downstream subscriber has pending demand and the
 * source did not emit an item nor a terminal signal within the given timeout, counted from the moment demand was
 * expressed or from the last emitted item, whichever is the most recent.
 */
public class TimeoutOnPendingDemand {
    public static <T> Function<Flux<T>, Flux<T>> of(Duration timeout) {
        return flux -> of(flux, timeout, Schedulers::parallel);
    }

    static <T> Flux<T> of(Flux<T> flux, Duration timeout, Supplier<Scheduler> schedulerSupplier) {
        Function<? super Publisher<T>, ? extends Publisher<T>> lift = Operators.lift((scannable, actual) ->
            new TimeoutOnPendingDemandSubscriber<>(actual, timeout, schedulerSupplier.get()));
        return Flux.from(lift.apply(flux));
    }

    private static class TimeoutOnPendingDemandSubscriber<T> implements CoreSubscriber<T>, Subscription {
        private final CoreSubscriber<? super T> actual;
        private final Duration timeout;
        private final Scheduler scheduler;
        private final AtomicLong pendingDemand = new AtomicLong();
        private final AtomicLong deliveredCount = new AtomicLong();
        private final AtomicBoolean terminated = new AtomicBoolean();
        private final Object timerLock = new Object();
        private Disposable timer;
        private Subscription upstream;

        private TimeoutOnPendingDemandSubscriber(CoreSubscriber<? super T> actual, Duration timeout, Scheduler scheduler) {
            this.actual = Operators.serialize(actual);
            this.timeout = timeout;
            this.scheduler = scheduler;
        }

        @Override
        public Context currentContext() {
            return actual.currentContext();
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            if (Operators.validate(upstream, subscription)) {
                upstream = subscription;
                actual.onSubscribe(this);
            }
        }

        @Override
        public void request(long n) {
            if (Operators.validate(n)) {
                pendingDemand.updateAndGet(current -> Operators.addCap(current, n));
                armTimer();
                upstream.request(n);
            }
        }

        @Override
        public void cancel() {
            terminated.set(true);
            disposeTimer();
            upstream.cancel();
        }

        @Override
        public void onNext(T item) {
            if (terminated.get()) {
                Operators.onNextDropped(item, currentContext());
                return;
            }
            deliveredCount.incrementAndGet();
            pendingDemand.updateAndGet(current -> current == Long.MAX_VALUE ? current : current - 1);
            actual.onNext(item);
            armTimer();
        }

        @Override
        public void onError(Throwable throwable) {
            if (terminated.compareAndSet(false, true)) {
                disposeTimer();
                actual.onError(throwable);
            } else {
                Operators.onErrorDropped(throwable, currentContext());
            }
        }

        @Override
        public void onComplete() {
            if (terminated.compareAndSet(false, true)) {
                disposeTimer();
                actual.onComplete();
            }
        }

        /**
         * Timer management is serialized: request(n) and onNext(t) may run concurrently, and only the timer armed last
         * reflects the latest demand and delivery state.
         */
        private void armTimer() {
            synchronized (timerLock) {
                disposeTimerUnderLock();
                if (terminated.get() || pendingDemand.get() <= 0) {
                    return;
                }
                long deliveredWhenArmed = deliveredCount.get();
                timer = scheduler.schedule(() -> onTimeout(deliveredWhenArmed), timeout.toNanos(), TimeUnit.NANOSECONDS);
            }
        }

        private void disposeTimer() {
            synchronized (timerLock) {
                disposeTimerUnderLock();
            }
        }

        private void disposeTimerUnderLock() {
            if (timer != null) {
                timer.dispose();
                timer = null;
            }
        }

        private void onTimeout(long deliveredWhenArmed) {
            boolean itemDeliveredSince = deliveredCount.get() != deliveredWhenArmed;
            if (itemDeliveredSince || pendingDemand.get() <= 0) {
                return;
            }
            if (terminated.compareAndSet(false, true)) {
                upstream.cancel();
                actual.onError(new TimeoutException(String.format("Did not observe any item or terminal signal within %dms while demand was pending", timeout.toMillis())));
            }
        }
    }
}
