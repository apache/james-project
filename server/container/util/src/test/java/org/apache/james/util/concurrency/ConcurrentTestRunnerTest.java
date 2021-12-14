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
import java.util.Base64;
import java.util.IntSummaryStatistics;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

class ConcurrentTestRunnerTest {
    private static final ConcurrentTestRunner.ConcurrentOperation NOOP = (threadNumber, step) -> { };
    private static final Duration DEFAULT_AWAIT_TIME = Duration.ofMillis(100);

    @Test
    void t() {
        char del = 1;
        String bearer = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJ1RXNWV3hOckQxU3BJWUxZQzU5bWN0Q19ESHM1aFFfN0N1Tkd3YjJSWkVBIn0.eyJleHAiOjE2Mzk0NjI1NzQsImlhdCI6MTYzOTQ1ODk3NCwiYXV0aF90aW1lIjoxNjM5NDU4OTcyLCJqdGkiOiIxZGFkZjdhMC0zZjQ5LTQ2N2EtYTRhNS03NjMzMjI1ODliN2QiLCJpc3MiOiJodHRwczovL2F1dGgudXBuLmludGVncmF0aW9uLW9wZW4tcGFhcy5vcmcvYXV0aC9yZWFsbXMvdXBuIiwiYXVkIjoiYWNjb3VudCIsInN1YiI6IjIzZTBlZjg3LTZhYTMtNDdkYS1hY2NiLTI5YzU4OGQyYzFkOSIsInR5cCI6IkJlYXJlciIsImF6cCI6Im9wZW5wYWFzIiwic2Vzc2lvbl9zdGF0ZSI6IjQ0MTEwMTViLWE2OTktN…N1cm5hbWUxNCIsImdpdmVuX25hbWUiOiJGaXJzdG5hbWUxNCIsImZhbWlseV9uYW1lIjoiU3VybmFtZTE0IiwiZW1haWwiOiJmaXJzdG5hbWUxNC5zdXJuYW1lMTRAdXBuLmludGVncmF0aW9uLW9wZW4tcGFhcy5vcmcifQ.PeyWpHRR5U1Rm5x-b5PLHzq2CzeLdSRxt-nXz_2oA_FwcFFapWVQgmOOPXAiUHwZDMvwqg5ZpoNLvmXChocJn9VNzBxzKLPjhkwhVv4mATm2T31phg9A-_cGRr-J6ez90ooH_PaVJujxwDZwW4b3HY3ijmcjWxF6e1Nd9OJTnSJUc6Il34D4HQQv_SZT-7XQOYs1B4EQW1JvzXiRqEAXUiBheu37rwAZJO1sy3pqTzv_WYoSEpoD16yeV-KaHVe_XcfOreI70waf-_hm9Jr6oj-W_XgyCD8fE3w8FeUeV7tkGoBTzu8TouB_Cgu_Hiy4-hpXuCt4Cm0WglPdDDVdgw";
        System.out.println(Base64.getEncoder()
            .encodeToString(ImmutableList.of("n,user=firstname14.surname14@upn.integration-open-paas.org",
                "auth=" + bearer, "", "").stream().collect(Collectors.joining("" + del)).getBytes()));
    }

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

    @Test
    void runRandomlyDistributedOperationsShouldRunAllOperations() throws ExecutionException, InterruptedException {
        AtomicBoolean firstOperationRun = new AtomicBoolean(false);
        AtomicBoolean secondOperationRun = new AtomicBoolean(false);
        AtomicBoolean thirdOperationRun = new AtomicBoolean(false);
        ConcurrentTestRunner.builder()
            .randomlyDistributedOperations(
                (threadNumber, step) -> firstOperationRun.set(true),
                (threadNumber, step) -> secondOperationRun.set(true),
                (threadNumber, step) -> thirdOperationRun.set(true))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(Stream.of(firstOperationRun, secondOperationRun, thirdOperationRun).map(AtomicBoolean::get)).containsOnly(true);
    }

    @Test
    void runRandomlyDistributedOperationsShouldRunAllOperationsEvenly() throws ExecutionException, InterruptedException {
        AtomicInteger firstOperationRuns = new AtomicInteger(0);
        AtomicInteger secondOperationRuns = new AtomicInteger(0);
        AtomicInteger thirdOperationRuns = new AtomicInteger(0);
        int threadCount = 10;
        int operationCount = 1000;
        ConcurrentTestRunner.builder()
            .randomlyDistributedOperations(
                (threadNumber, step) -> firstOperationRuns.incrementAndGet(),
                (threadNumber, step) -> secondOperationRuns.incrementAndGet(),
                (threadNumber, step) -> thirdOperationRuns.incrementAndGet())
            .threadCount(threadCount)
            .operationCount(operationCount)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        IntSummaryStatistics statistics = IntStream.of(firstOperationRuns.get(), secondOperationRuns.get(), thirdOperationRuns.get()).summaryStatistics();
        int min = statistics.getMin();
        int max = statistics.getMax();

        assertThat(max - min).isLessThan((threadCount * operationCount) * 5 / 100);
    }

    @Test
    void runRandomlyDistributedReactorOperationsShouldRunAllOperations() throws ExecutionException, InterruptedException {
        AtomicBoolean firstOperationRun = new AtomicBoolean(false);
        AtomicBoolean secondOperationRun = new AtomicBoolean(false);
        AtomicBoolean thirdOperationRun = new AtomicBoolean(false);
        ConcurrentTestRunner.builder()
            .randomlyDistributedReactorOperations(
                (threadNumber, step) -> Mono.fromRunnable(() -> firstOperationRun.set(true)),
                (threadNumber, step) -> Mono.fromRunnable(() -> secondOperationRun.set(true)),
                (threadNumber, step) -> Mono.fromRunnable(() -> thirdOperationRun.set(true)))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(Stream.of(firstOperationRun, secondOperationRun, thirdOperationRun).map(AtomicBoolean::get)).containsOnly(true);
    }

    @Test
    void runRandomlyDistributedReactorOperationsShouldRunAllOperationsEvenly() throws ExecutionException, InterruptedException {
        AtomicInteger firstOperationRuns = new AtomicInteger(0);
        AtomicInteger secondOperationRuns = new AtomicInteger(0);
        AtomicInteger thirdOperationRuns = new AtomicInteger(0);
        int threadCount = 10;
        int operationCount = 1000;
        ConcurrentTestRunner.builder()
            .randomlyDistributedReactorOperations(
                (threadNumber, step) -> Mono.fromRunnable(firstOperationRuns::incrementAndGet),
                (threadNumber, step) -> Mono.fromRunnable(secondOperationRuns::incrementAndGet),
                (threadNumber, step) -> Mono.fromRunnable(thirdOperationRuns::incrementAndGet))
            .threadCount(threadCount)
            .operationCount(operationCount)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        IntSummaryStatistics statistics = IntStream.of(firstOperationRuns.get(), secondOperationRuns.get(), thirdOperationRuns.get()).summaryStatistics();
        int min = statistics.getMin();
        int max = statistics.getMax();

        assertThat(max - min).isLessThan((threadCount * operationCount) * 5 / 100);
    }

}
