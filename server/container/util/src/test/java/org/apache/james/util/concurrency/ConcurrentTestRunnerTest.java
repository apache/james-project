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

package org.apache.james.util.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class ConcurrentTestRunnerTest {
    public static final ConcurrentTestRunner.ConcurrentOperation NOOP = (threadNumber, step) -> { };
    public static final int DEFAULT_AWAIT_TIME = 100;

    @Test
    public void constructorShouldThrowOnNegativeThreadCount() {
        assertThatThrownBy(() ->
            ConcurrentTestRunner.builder()
                .operation(NOOP)
                .threadCount(-1)
                .runSuccessfullyWithin(1, TimeUnit.SECONDS))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void constructorShouldThrowOnNegativeOperationCount() {
        assertThatThrownBy(() ->
            ConcurrentTestRunner.builder()
                .operation(NOOP)
                .threadCount(1)
                .operationCount(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void constructorShouldThrowOnZeroThreadCount() {
        assertThatThrownBy(() ->
            ConcurrentTestRunner.builder()
                .operation(NOOP)
                .threadCount(0)
                .runSuccessfullyWithin(DEFAULT_AWAIT_TIME, TimeUnit.MILLISECONDS))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void constructorShouldThrowOnZeroOperationCount() {
        assertThatThrownBy(() ->
            ConcurrentTestRunner.builder()
                .operation(NOOP)
                .threadCount(1)
                .operationCount(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void constructorShouldThrowOnNullBiConsumer() {
        assertThatThrownBy(() ->
            ConcurrentTestRunner.builder()
                .operation(null)
                .threadCount(1)
                .runSuccessfullyWithin(DEFAULT_AWAIT_TIME, TimeUnit.MILLISECONDS))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void awaitTerminationShouldNotThrowWhenFinished() {
        assertThatCode(() ->  ConcurrentTestRunner.builder()
                .operation(NOOP)
                .threadCount(1)
                .runSuccessfullyWithin(DEFAULT_AWAIT_TIME, TimeUnit.MILLISECONDS))
            .doesNotThrowAnyException();
    }

    @Test
    public void awaitTerminationShouldThrowWhenNotFinished() {
        assertThatThrownBy(() -> ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> Thread.sleep(50))
                .threadCount(1)
                .runSuccessfullyWithin(25, TimeUnit.MILLISECONDS))
            .isInstanceOf(ConcurrentTestRunner.NotTerminatedException.class);
    }

    @Test
    public void runShouldPerformAllOperations() {
        ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

        assertThatCode(() -> ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> queue.add(threadNumber + ":" + step))
                .threadCount(2)
                .operationCount(2)
                .runSuccessfullyWithin(DEFAULT_AWAIT_TIME, TimeUnit.MILLISECONDS))
            .doesNotThrowAnyException();

        assertThat(queue).containsOnly("0:0", "0:1", "1:0", "1:1");
    }

    @Test
    public void operationCountShouldDefaultToOne() {
        ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

        assertThatCode(() -> ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> queue.add(threadNumber + ":" + step))
                .threadCount(2)
                .runSuccessfullyWithin(DEFAULT_AWAIT_TIME, TimeUnit.MILLISECONDS))
            .doesNotThrowAnyException();
    }

    @Test
    public void runShouldNotThrowOnExceptions() {
        assertThatCode(() -> ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> {
                    throw new RuntimeException();
                })
                .threadCount(2)
                .operationCount(2)
                .runSuccessfullyWithin(DEFAULT_AWAIT_TIME, TimeUnit.MILLISECONDS))
            .doesNotThrowAnyException();
    }

    @Test
    public void noExceptionsShouldNotThrowWhenNoExceptionGenerated() throws Exception {
        ConcurrentTestRunner.builder()
            .operation(NOOP)
            .threadCount(2)
            .operationCount(2)
            .runSuccessfullyWithin(DEFAULT_AWAIT_TIME, TimeUnit.MILLISECONDS)
            .assertNoException();
    }

    @Test
    public void assertNoExceptionShouldThrowOnExceptions() throws Exception {
        assertThatThrownBy(() ->
                ConcurrentTestRunner.builder()
                    .operation((threadNumber, step) -> {
                        throw new RuntimeException();
                    })
                    .threadCount(2)
                    .operationCount(2)
                    .runSuccessfullyWithin(DEFAULT_AWAIT_TIME, TimeUnit.MILLISECONDS)
                    .assertNoException())
            .isInstanceOf(ExecutionException.class);
    }

    @Test
    public void runShouldPerformAllOperationsEvenOnExceptions() {
        ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

        assertThatCode(() -> ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> {
                    queue.add(threadNumber + ":" + step);
                    throw new RuntimeException();
                })
                .threadCount(2)
                .operationCount(2)
                .runSuccessfullyWithin(DEFAULT_AWAIT_TIME, TimeUnit.MILLISECONDS))
            .doesNotThrowAnyException();

        assertThat(queue).containsOnly("0:0", "0:1", "1:0", "1:1");
    }

    @Test
    public void runShouldPerformAllOperationsEvenOnOccasionalExceptions() {
        ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

        assertThatCode(() -> ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> {
                    queue.add(threadNumber + ":" + step);
                    if ((threadNumber + step) % 2 == 0) {
                        throw new RuntimeException();
                    }
                })
                .threadCount(2)
                .operationCount(2)
                .runSuccessfullyWithin(DEFAULT_AWAIT_TIME, TimeUnit.MILLISECONDS))
            .doesNotThrowAnyException();

        assertThat(queue).containsOnly("0:0", "0:1", "1:0", "1:1");
    }
}
