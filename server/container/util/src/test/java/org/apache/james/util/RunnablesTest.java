/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

class RunnablesTest {

    @Test
    void shouldActuallyRunThings() {
        AtomicBoolean sideEffect = new AtomicBoolean(false);
        Runnables.runParallel(() -> sideEffect.set(true));
        assertThat(sideEffect).isTrue();
    }

    @Test
    void shouldActuallyRunInParallel() throws InterruptedException {
        int parallel = 2;
        CountDownLatch countDownLatch = new CountDownLatch(parallel);
        Runnable runnable = countDownLatch::countDown;
        Runnables.runParallel(Flux.range(0, 2).map(i -> runnable));
        assertThat(countDownLatch.await(2, TimeUnit.MINUTES)).isTrue();
    }
}