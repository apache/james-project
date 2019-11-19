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

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

public class ConcurrentTestRunnerTest {
    public static final ConcurrentTestRunner.ConcurrentOperation NOOP = (threadNumber, step) -> { };
    public static final Duration DEFAULT_AWAIT_TIME = Duration.ofMillis(100);

    @Test
    void constructorShouldThrowOnNegativeThreadCount() {
        assertThatThrownBy(() ->
            ConcurrentTestRunner.builder()
                .operation(NOOP)
                .threadCount(-1)
                .runSuccessfullyWithin(DEFAULT_AWAIT_TIME))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorShouldThrowOnNegativeOperationCount() {
        assertThatThrownBy(() ->
            ConcurrentTestRunner.builder()
                .operation(NOOP)
                .threadCount(1)
                .operationCount(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorShouldThrowOnZeroThreadCount() {
        assertThatThrownBy(() ->
            ConcurrentTestRunner.builder()
                .operation(NOOP)
                .threadCount(0)
                .runSuccessfullyWithin(DEFAULT_AWAIT_TIME))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorShouldThrowOnZeroOperationCount() {
        assertThatThrownBy(() ->
            ConcurrentTestRunner.builder()
                .operation(NOOP)
                .threadCount(1)
                .operationCount(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorShouldThrowOnNullBiConsumer() {
        assertThatThrownBy(() ->
            ConcurrentTestRunner.builder()
                .operation(null)
                .threadCount(1)
                .runSuccessfullyWithin(DEFAULT_AWAIT_TIME))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void awaitTerminationShouldNotThrowWhenFinished() {
        assertThatCode(() ->  ConcurrentTestRunner.builder()
                .operation(NOOP)
                .threadCount(1)
                .runSuccessfullyWithin(DEFAULT_AWAIT_TIME))
            .doesNotThrowAnyException();
    }

    @Test
    void awaitTerminationShouldThrowWhenNotFinished() {
        assertThatThrownBy(() -> ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> Thread.sleep(50))
                .threadCount(1)
                .runSuccessfullyWithin(Duration.ofMillis(25)))
            .isInstanceOf(ConcurrentTestRunner.NotTerminatedException.class);
    }

    @Test
    void runShouldPerformAllOperations() {
        ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

        assertThatCode(() -> ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> queue.add(threadNumber + ":" + step))
            .threadCount(2)
            .operationCount(2)
            .run()
            .awaitTermination(Duration.ofSeconds(1)))
            .doesNotThrowAnyException();

        assertThat(queue).containsOnly("0:0", "0:1", "1:0", "1:1");
    }

    @Test
    void closeShouldPreventPerformAllOperations() throws IOException, InterruptedException {
        ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

        int maxItems = 200000;
        Closeable closeable = ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> queue.add(threadNumber + ":" + step))
            .threadCount(2)
            .operationCount(maxItems)
            .run();
        closeable.close();
        TimeUnit.SECONDS.sleep(1);
        int stabilizedItemCount = queue.size();
        assertThat(stabilizedItemCount).isLessThanOrEqualTo(maxItems * 2);
        TimeUnit.SECONDS.sleep(1);
        assertThat(queue).hasSize(stabilizedItemCount);
    }

    @Test
    void runSuccessfullyWithinShouldPerformAllOperations() {
        ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

        assertThatCode(() -> ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> queue.add(threadNumber + ":" + step))
                .threadCount(2)
                .operationCount(2)
                .runSuccessfullyWithin(DEFAULT_AWAIT_TIME))
            .doesNotThrowAnyException();

        assertThat(queue).containsOnly("0:0", "0:1", "1:0", "1:1");
    }

    @Test
    void operationCountShouldDefaultToOne() {
        ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

        assertThatCode(() -> ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> queue.add(threadNumber + ":" + step))
                .threadCount(2)
                .runSuccessfullyWithin(DEFAULT_AWAIT_TIME))
            .doesNotThrowAnyException();
    }

    @Test
    void runShouldNotThrowOnExceptions() {
        assertThatCode(() -> ConcurrentTestRunner.builder()
                .operation((threadNumber, step) -> {
                    throw new RuntimeException();
                })
                .threadCount(2)
                .operationCount(2)
                .runAcceptingErrorsWithin(DEFAULT_AWAIT_TIME))
            .doesNotThrowAnyException();
    }

    @Test
    void noExceptionsShouldNotThrowWhenNoExceptionGenerated() throws Exception {
        ConcurrentTestRunner.builder()
            .operation(NOOP)
            .threadCount(2)
            .operationCount(2)
            .runSuccessfullyWithin(DEFAULT_AWAIT_TIME)
            .assertNoException();
    }

    @Test
    void assertNoExceptionShouldThrowOnExceptions() {
        assertThatThrownBy(() ->
                ConcurrentTestRunner.builder()
                    .operation((threadNumber, step) -> {
                        throw new RuntimeException();
                    })
                    .threadCount(2)
                    .operationCount(2)
                    .runSuccessfullyWithin(DEFAULT_AWAIT_TIME)
                    .assertNoException())
            .isInstanceOf(ExecutionException.class);
    }

    @Test
    void runShouldPerformAllOperationsEvenOnExceptions() throws Exception {
        ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

        ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> {
                queue.add(threadNumber + ":" + step);
                throw new RuntimeException();
            })
            .threadCount(2)
            .operationCount(2)
            .runAcceptingErrorsWithin(DEFAULT_AWAIT_TIME);

        assertThat(queue).containsOnly("0:0", "0:1", "1:0", "1:1");
    }

    @Test
    void runShouldPerformAllOperationsEvenOnOccasionalExceptions() throws Exception {
        ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

        ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> {
                queue.add(threadNumber + ":" + step);
                if ((threadNumber + step) % 2 == 0) {
                    throw new RuntimeException();
                }
            })
            .threadCount(2)
            .operationCount(2)
            .runAcceptingErrorsWithin(DEFAULT_AWAIT_TIME);

        assertThat(queue).containsOnly("0:0", "0:1", "1:0", "1:1");
    }
}
