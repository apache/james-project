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

import static com.jayway.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.metrics.api.Metric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.awaitility.Duration;
import com.jayway.awaitility.core.ConditionFactory;

class RecordingMetricFactoryTest {

    private static final String TIME_METRIC_NAME = "timerMetric";
    private static final String METRIC_NAME = "metric";
    private static final java.time.Duration ONE_SECOND = java.time.Duration.ofSeconds(1);
    private static final java.time.Duration FIVE_SECONDS = java.time.Duration.ofSeconds(5);
    private static final ConditionFactory WAIT_CONDITION = await().timeout(Duration.ONE_MINUTE);

    private RecordingMetricFactory testee;

    @BeforeEach
    void setUp() {
        testee = new RecordingMetricFactory();
    }

    @Test
    void executionTimesForATimeMetricShouldBeStoreMultipleTime() {
        runTimeMetric(ONE_SECOND);
        runTimeMetric(FIVE_SECONDS);

        WAIT_CONDITION
            .until(() -> {
                assertThat(testee.executionTimesFor(TIME_METRIC_NAME))
                    .hasSize(2);
                assertThat(testee.executionTimesFor(TIME_METRIC_NAME))
                    .contains(ONE_SECOND, FIVE_SECONDS);
            });
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

    private void runTimeMetric(java.time.Duration duration) {
        testee.runPublishingTimerMetric(TIME_METRIC_NAME, () -> {
            try {
                Thread.sleep(duration.toMillis());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }
}
