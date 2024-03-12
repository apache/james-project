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

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.metrics.api.GaugeRegistry;
import org.reactivestreams.Publisher;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public class ReactiveThrottler {
    private static class TaskHolder {
        private final Publisher<Void> task;
        private final AtomicReference<Disposable> disposable = new AtomicReference<>();

        private TaskHolder(Publisher<Void> task) {
            this.task = task;
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

    public ReactiveThrottler(GaugeRegistry gaugeRegistry, int maxConcurrentRequests, int maxQueueSize) {
        gaugeRegistry.register("imap.request.queue.size", () -> Math.max(concurrentRequests.get() - maxConcurrentRequests, 0));

        this.maxConcurrentRequests = maxConcurrentRequests;
        this.maxQueueSize = maxQueueSize;
    }

    public Mono<Void> throttle(Publisher<Void> task, ImapMessage imapMessage) {
        if (maxConcurrentRequests < 0) {
            return Mono.from(task);
        }
        int requestNumber = concurrentRequests.incrementAndGet();

        if (requestNumber <= maxConcurrentRequests) {
            // We have capacity for one more concurrent request
            return Mono.from(task)
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
                .then(Mono.fromRunnable(() -> one.emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST))));
            queue.add(taskHolder);
            // Let the caller await task completion
            return one.asMono()
                .doOnCancel(() -> {
                    cancelled.set(true);
                    Optional.ofNullable(taskHolder.disposable.get()).ifPresent(Disposable::dispose);
                    boolean removed = queue.remove(taskHolder);
                    if (removed) {
                        concurrentRequests.decrementAndGet();
                    }
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

    private void onRequestDone() {
        concurrentRequests.getAndDecrement();
        TaskHolder throttled = queue.poll();
        if (throttled != null) {
            Disposable disposable = Mono.from(throttled.task)
                .doFinally(any -> {
                    onRequestDone();
                })
                .subscribe();
            throttled.disposable.set(disposable);
        }
    }
}
