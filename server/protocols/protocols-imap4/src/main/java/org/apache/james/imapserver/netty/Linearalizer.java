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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public class Linearalizer {
    private final ReentrantLock lock = new ReentrantLock();
    private boolean inFlight = false;
    private final Queue<Publisher<Void>> queue = new ConcurrentLinkedQueue<>();

    public Mono<Void> execute(Publisher<Void> task) {
        lock.lock();
        try {
            if (!inFlight) {
                inFlight = true;
                return Mono.from(task)
                    .doFinally(any -> onRequestDone());
            } else {
                // Queue the request for later
                Sinks.One<Void> one = Sinks.one();
                queue.add(Mono.from(task)
                    .then(Mono.fromRunnable(() -> one.emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST))));
                // Let the caller await task completion
                return one.asMono();
            }
        } finally {
            lock.unlock();
        }
    }

    private void onRequestDone() {
        lock.lock();
        try {
            Publisher<Void> throttled = queue.poll();
            if (throttled != null) {
                Mono.from(throttled)
                    .doFinally(any -> onRequestDone())
                    .subscribe();
            } else {
                inFlight = false;
            }
        } finally {
            lock.unlock();
        }
    }
}
