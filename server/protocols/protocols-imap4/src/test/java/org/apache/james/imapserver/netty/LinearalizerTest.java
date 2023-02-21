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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import reactor.core.publisher.Mono;

class LinearalizerTest {
    @Test
    void shouldExecuteSubmittedTasks() {
        Linearalizer testee = new Linearalizer();

        // When I submit a task
        AtomicBoolean executed = new AtomicBoolean(false);
        Mono.from(testee.execute(Mono.delay(Duration.ofMillis(50)).then(Mono.fromRunnable(() -> executed.getAndSet(true))))).block();

        // Then that task is executed
        assertThat(executed.get()).isTrue();
    }

    @Test
    void shouldNotExecuteQueuedTasksLogicRightAway() {
        Linearalizer testee = new Linearalizer();

        // When I submit 2 tasks task
        AtomicBoolean executed = new AtomicBoolean(false);
        Mono.from(testee.execute(Mono.delay(Duration.ofMillis(200)).then())).subscribe();
        Mono.from(testee.execute(Mono.fromRunnable(() -> executed.getAndSet(true)))).subscribe();

        // Then the second task is not executed staight away
        assertThat(executed.get()).isFalse();
    }

    @Test
    void shouldEventuallyExecuteQueuedTasks() {
        Linearalizer testee = new Linearalizer();

        // When I submit 2 tasks task
        AtomicBoolean executed = new AtomicBoolean(false);
        Mono.from(testee.execute(Mono.delay(Duration.ofMillis(200)).then())).subscribe();
        Mono.from(testee.execute(Mono.fromRunnable(() -> executed.getAndSet(true)))).subscribe();

        // Then that task is eventually executed
        Awaitility.await().atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> assertThat(executed.get()).isTrue());
    }
}