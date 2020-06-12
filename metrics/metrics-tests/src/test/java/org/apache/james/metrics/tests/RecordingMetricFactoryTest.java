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

package org.apache.james.metrics.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.MetricFactoryContract;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class RecordingMetricFactoryTest implements MetricFactoryContract {

    private static final String TIME_METRIC_NAME = "timerMetric";
    private static final String METRIC_NAME = "metric";
    private static final java.time.Duration ONE_SECOND = java.time.Duration.ofSeconds(1);
    private static final java.time.Duration FIVE_SECONDS = java.time.Duration.ofSeconds(5);

    private RecordingMetricFactory testee;

    @BeforeEach
    void setUp() {
        testee = new RecordingMetricFactory();
    }

    @Override
    public MetricFactory testee() {
        return testee;
    }

    @Test
    void executionTimesForATimeMetricShouldBeStoreMultipleTime() throws InterruptedException {
        TimeMetric timeMetric1 = testee.timer(TIME_METRIC_NAME);
        Thread.sleep(ONE_SECOND.toMillis());
        timeMetric1.stopAndPublish();

        TimeMetric timeMetric2 = testee.timer(TIME_METRIC_NAME);
        Thread.sleep(FIVE_SECONDS.toMillis());
        timeMetric2.stopAndPublish();

        assertThat(testee.executionTimesFor(TIME_METRIC_NAME))
            .hasSize(2);

        assertThat(testee.executionTimesFor(TIME_METRIC_NAME))
            .element(0)
            .satisfies(duration -> assertThat(duration).isGreaterThanOrEqualTo(ONE_SECOND));

        assertThat(testee.executionTimesFor(TIME_METRIC_NAME))
            .element(1)
            .satisfies(duration -> assertThat(duration).isGreaterThanOrEqualTo(FIVE_SECONDS));
    }

    @Test
    void executionTimesForATimeMetricShouldBeStoreMultipleTimeInConcurrent() throws Exception {
        AtomicInteger count = new AtomicInteger();

        ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> testee.decorateSupplierWithTimerMetric(TIME_METRIC_NAME, count::incrementAndGet))
            .threadCount(10)
            .operationCount(200)
            .runSuccessfullyWithin(Duration.ofSeconds(10));

        assertThat(testee.executionTimesFor(TIME_METRIC_NAME))
            .hasSize(2000);
    }

    @Test
    void countForAMetricShouldBeCountForIncrementMultipleTime() {
        Metric metric = testee.generate(METRIC_NAME);
        metric.increment();
        metric.increment();

        assertThat(testee.countFor(METRIC_NAME))
            .isEqualTo(2);
    }

    @Test
    void countForAMetricShouldBeCountForIncrementAndDecrement() {
        Metric metric = testee.generate(METRIC_NAME);
        metric.increment();
        metric.increment();
        metric.decrement();

        assertThat(testee.countFor(METRIC_NAME))
            .isEqualTo(1);
    }

    @Test
    void countForAMetricShouldBeCountForIncrementAddAndRemove() {
        Metric metric = testee.generate(METRIC_NAME);
        metric.increment();
        metric.increment();
        metric.add(3);

        assertThat(testee.countFor(METRIC_NAME))
            .isEqualTo(5);
    }

    @Test
    void decoratePublisherWithTimerMetricShouldRecordANewValueForEachRetry() {
        Duration duration = Duration.ofMillis(100);
        Mono.from(testee.decoratePublisherWithTimerMetric("any", Mono.delay(duration)))
            .repeat(5)
            .blockLast();

        assertThat(testee.executionTimesFor("any"))
            .hasSize(6)
            .allSatisfy(timing -> assertThat(timing).isLessThan(duration.multipliedBy(2)));
    }

    @Test
    void decoratePublisherWithTimerMetricLogP99ShouldRecordANewValueForEachRetry() {
        Duration duration = Duration.ofMillis(100);
        Mono.from(testee.decoratePublisherWithTimerMetricLogP99("any", Mono.delay(duration)))
            .repeat(5)
            .blockLast();

        assertThat(testee.executionTimesFor("any"))
            .hasSize(6)
            .allSatisfy(timing -> assertThat(timing).isLessThan(duration.multipliedBy(2)));
    }
}
