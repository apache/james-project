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

import java.time.Duration;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.util.DurationParser;
import org.reactivestreams.Publisher;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

public class ReactiveThrottler {
    private static final Duration MAX_EXECUTION_TIME = Optional.ofNullable(System.getProperty("james.imap.reactive.throttler.max.task.execution.time"))
        .map(DurationParser::parse)
        .orElse(Duration.ofHours(1));

    private static class TaskHolder {
        private final Publisher<Void> task;
        private final Consumer<Runnable> ctx;
        private final AtomicReference<Disposable> disposable = new AtomicReference<>();

        private TaskHolder(Publisher<Void> task, Consumer<Runnable> ctx) {
            this.task = task;
            this.ctx = ctx;
        }
    }

    public static class RejectedException extends RuntimeException {
        private final ImapMessage imapMessage;

        public RejectedException(String message, ImapMessage imapMessage) {
            super(message);
            this.imapMessage = imapMessage;
        }

        public ImapMessage getImapMessage() {
            return imapMessage;
        }
    }

    private final int maxConcurrentRequests;
    private final int maxQueueSize;
    // In flight + executing
    private final AtomicInteger concurrentRequests = new AtomicInteger(0);
    private final Queue<TaskHolder> queue = new ConcurrentLinkedQueue<>();
    private final Sinks.Many<TaskHolder> sink;

    public ReactiveThrottler(GaugeRegistry gaugeRegistry, int maxConcurrentRequests, int maxQueueSize) {
        gaugeRegistry.register("imap.request.queue.size", () -> Math.max(concurrentRequests.get() - maxConcurrentRequests, 0));

        this.maxConcurrentRequests = maxConcurrentRequests;
        this.maxQueueSize = maxQueueSize;
        this.sink = Sinks.many().multicast()
            .onBackpressureBuffer();

        sink.asFlux()
            .subscribeOn(Schedulers.parallel())
            .subscribe(taskHolder -> taskHolder.ctx.accept(() -> {
                    Disposable disposable = Mono.from(taskHolder.task)
                        .timeout(MAX_EXECUTION_TIME)
                        .doFinally(any -> onRequestDone())
                        .subscribe();
                    taskHolder.disposable.set(disposable);
                }));
    }

    @VisibleForTesting
    public Mono<Void> throttle(Publisher<Void> task, ImapMessage imapMessage) {
        return throttle(task, imapMessage, Runnable::run);
    }

    public Mono<Void> throttle(Publisher<Void> task, ImapMessage imapMessage, Consumer<Runnable> ctx) {
        if (maxConcurrentRequests < 0) {
            return Mono.from(task);
        }
        int requestNumber = concurrentRequests.incrementAndGet();

        if (requestNumber <= maxConcurrentRequests) {
            // We have capacity for one more concurrent request
            return Mono.from(task)
                .timeout(MAX_EXECUTION_TIME)
                .doFinally(any -> onRequestDone());
        } else if (requestNumber <= maxQueueSize + maxConcurrentRequests) {
            // Queue the request for later
            AtomicBoolean cancelled = new AtomicBoolean(false);
            Sinks.One<Void> one = Sinks.one();
            TaskHolder taskHolder = new TaskHolder(Mono.fromCallable(cancelled::get)
                .flatMap(cancel -> {
                    if (cancel) {
                        return Mono.empty();
                    }
                    return Mono.from(task);
                })
                .then(Mono.fromRunnable(() -> one.emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST))), ctx);
            queue.add(taskHolder);
            // Let the caller await task completion
            return one.asMono()
                .doOnCancel(() -> {
                    cancelled.set(true);
                    Optional.ofNullable(taskHolder.disposable.get())
                        .ifPresentOrElse(Disposable::dispose,
                            // If no Disposable set → task never started,
                            // but still counted → must release the slot
                            concurrentRequests::decrementAndGet);
                });
        } else {
            concurrentRequests.decrementAndGet();

            return Mono.error(new RejectedException(
                String.format(
                    "The IMAP server has reached its maximum capacity "
                        + "(concurrent requests: %d, queue size: %d)",
                    maxConcurrentRequests, maxQueueSize), imapMessage));
        }
    }

    public boolean isQueueFull() {
        return maxQueueSize <= queue.size();
    }

    private void onRequestDone() {
        concurrentRequests.getAndDecrement();
        TaskHolder throttled = queue.poll();
        if (throttled != null) {
            synchronized (sink) {
                sink.emitNext(throttled, Sinks.EmitFailureHandler.FAIL_FAST);
            }
        }
    }
}
