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

import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.I_Result;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * A request B arrives while the only running request A completes. B is either executed right away, or queued and
 * then dispatched by the completion of A: it must never be left waiting in the queue while nothing runs.
 *
 * With a queue size of 1, {@link ReactiveThrottler#isQueueFull()} tells whether B is still waiting in the queue.
 *
 * Run by {@link ReactiveThrottlerJCStressTest}.
 */
@JCStressTest
@Outcome(id = "0", expect = Expect.ACCEPTABLE, desc = "request B ran directly or was dispatched by the completion of A")
@Outcome(id = "1", expect = Expect.FORBIDDEN, desc = "request B is left waiting in the queue while nothing runs")
@State
public class ReactiveThrottlerLostWakeUpStress {
    final ReactiveThrottler throttler = new ReactiveThrottler(new NoopGaugeRegistry(), 1, 1);
    final Sinks.One<Void> requestA = Sinks.one();

    public ReactiveThrottlerLostWakeUpStress() {
        // A takes the only slot and runs until the first actor completes it
        throttler.throttle(requestA.asMono(), null).subscribe();
    }

    @Actor
    public void requestACompletes() {
        // the completion of A releases its slot on this thread
        requestA.tryEmitEmpty();
    }

    @Actor
    public void requestBArrives() {
        throttler.throttle(Mono.empty(), null).subscribe();
    }

    @Arbiter
    public void arbiter(I_Result r) {
        r.r1 = throttler.isQueueFull() ? 1 : 0;
    }
}
