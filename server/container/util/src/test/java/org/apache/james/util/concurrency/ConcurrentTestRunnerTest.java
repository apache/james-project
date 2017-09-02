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

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ConcurrentTestRunnerTest {

    public static final ConcurrentTestRunner.BiConsumer EMPTY_BI_CONSUMER = (threadNumber, step) -> {};
    public static final int DEFAULT_AWAIT_TIME = 100;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void constructorShouldThrowOnNegativeThreadCount() {
        expectedException.expect(IllegalArgumentException.class);

        int operationCount = 1;
        int threadCount = -1;
        new ConcurrentTestRunner(threadCount, operationCount, EMPTY_BI_CONSUMER);
    }

    @Test
    public void constructorShouldThrowOnNegativeOperationCount() {
        expectedException.expect(IllegalArgumentException.class);

        int operationCount = -1;
        int threadCount = 1;
        new ConcurrentTestRunner(threadCount, operationCount, EMPTY_BI_CONSUMER);
    }

    @Test
    public void constructorShouldThrowOnZeroThreadCount() {
        expectedException.expect(IllegalArgumentException.class);

        int operationCount = 1;
        int threadCount = 0;
        new ConcurrentTestRunner(threadCount, operationCount, EMPTY_BI_CONSUMER);
    }

    @Test
    public void constructorShouldThrowOnZeroOperationCount() {
        expectedException.expect(IllegalArgumentException.class);

        int operationCount = 0;
        int threadCount = 1;
        new ConcurrentTestRunner(threadCount, operationCount, EMPTY_BI_CONSUMER);
    }

    @Test
    public void constructorShouldThrowOnNullBiConsumer() {
        expectedException.expect(NullPointerException.class);

        int operationCount = 1;
        int threadCount = 1;
        new ConcurrentTestRunner(threadCount, operationCount, null);
    }

    @Test
    public void awaitTerminationShouldReturnTrueWhenFinished() throws Exception {
        int operationCount = 1;
        int threadCount = 1;

        ConcurrentTestRunner concurrentTestRunner = new ConcurrentTestRunner(threadCount, operationCount, EMPTY_BI_CONSUMER)
            .run();

        assertThat(concurrentTestRunner.awaitTermination(DEFAULT_AWAIT_TIME, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void awaitTerminationShouldReturnFalseWhenNotFinished() throws Exception {
        int operationCount = 1;
        int threadCount = 1;
        final int sleepDelay = 50;

        ConcurrentTestRunner concurrentTestRunner = new ConcurrentTestRunner(threadCount, operationCount,
            (threadNumber, step) -> Thread.sleep(sleepDelay))
            .run();

        assertThat(concurrentTestRunner.awaitTermination(sleepDelay / 2, TimeUnit.MILLISECONDS)).isFalse();
    }

    @Test
    public void runShouldPerformAllOperations() throws Exception {
        int operationCount = 2;
        int threadCount = 2;
        final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

        ConcurrentTestRunner concurrentTestRunner = new ConcurrentTestRunner(threadCount, operationCount,
            (threadNumber, step) -> queue.add(threadNumber + ":" + step))
            .run();

        assertThat(concurrentTestRunner.awaitTermination(DEFAULT_AWAIT_TIME, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(queue).containsOnly("0:0", "0:1", "1:0", "1:1");
    }

    @Test
    public void runShouldNotThrowOnExceptions() throws Exception {
        int operationCount = 2;
        int threadCount = 2;

        ConcurrentTestRunner concurrentTestRunner = new ConcurrentTestRunner(threadCount, operationCount,
            (threadNumber, step) -> {
                throw new RuntimeException();
            })
            .run();

        assertThat(concurrentTestRunner.awaitTermination(DEFAULT_AWAIT_TIME, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void noExceptionsShouldNotThrowWhenNoExceptionGenerated() throws Exception {
        int operationCount = 2;
        int threadCount = 2;

        ConcurrentTestRunner concurrentTestRunner = new ConcurrentTestRunner(threadCount, operationCount, EMPTY_BI_CONSUMER)
            .run();

        concurrentTestRunner.awaitTermination(DEFAULT_AWAIT_TIME, TimeUnit.MILLISECONDS);

        concurrentTestRunner.assertNoException();
    }

    @Test
    public void assertNoExceptionShouldThrowOnExceptions() throws Exception {
        int operationCount = 2;
        int threadCount = 2;

        ConcurrentTestRunner concurrentTestRunner = new ConcurrentTestRunner(threadCount, operationCount,
            (threadNumber, step) -> {
                throw new RuntimeException();
            })
            .run();
        concurrentTestRunner.awaitTermination(DEFAULT_AWAIT_TIME, TimeUnit.MILLISECONDS);

        expectedException.expect(ExecutionException.class);
        concurrentTestRunner.assertNoException();
    }

    @Test
    public void runShouldPerformAllOperationsEvenOnExceptions() throws Exception {
        int operationCount = 2;
        int threadCount = 2;
        final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

        ConcurrentTestRunner concurrentTestRunner = new ConcurrentTestRunner(threadCount, operationCount,
            (threadNumber, step) -> {
                queue.add(threadNumber + ":" + step);
                throw new RuntimeException();
            })
            .run();

        assertThat(concurrentTestRunner.awaitTermination(DEFAULT_AWAIT_TIME, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(queue).containsOnly("0:0", "0:1", "1:0", "1:1");
    }

    @Test
    public void runShouldPerformAllOperationsEvenOnOccasionalExceptions() throws Exception {
        int operationCount = 2;
        int threadCount = 2;
        final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

        ConcurrentTestRunner concurrentTestRunner = new ConcurrentTestRunner(threadCount, operationCount,
            (threadNumber, step) -> {
                queue.add(threadNumber + ":" + step);
                if ((threadNumber + step) % 2 == 0) {
                    throw new RuntimeException();
                }
            })
            .run();

        assertThat(concurrentTestRunner.awaitTermination(DEFAULT_AWAIT_TIME, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(queue).containsOnly("0:0", "0:1", "1:0", "1:1");
    }
}
