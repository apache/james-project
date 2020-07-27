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

package org.apache.james.jmap.api.projections;

import static org.apache.james.jmap.api.projections.MessageFastViewProjection.METRIC_RETRIEVE_HIT_COUNT;
import static org.apache.james.jmap.api.projections.MessageFastViewProjection.METRIC_RETRIEVE_MISS_COUNT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MessageFastViewProjectionHealthCheckTest {

    private static final ComponentName COMPONENT_NAME = new ComponentName("MessageFastViewProjection");

    private MessageFastViewProjectionHealthCheck testee;
    private Metric hitMetric;
    private Metric missMetric;

    @BeforeEach
    void setUp() {
        RecordingMetricFactory metricFactory = new RecordingMetricFactory();
        testee = new MessageFastViewProjectionHealthCheck(metricFactory);

        hitMetric = metricFactory.generate(METRIC_RETRIEVE_HIT_COUNT);
        missMetric = metricFactory.generate(METRIC_RETRIEVE_MISS_COUNT);
    }

    @Test
    void componentNameShouldReturnTheRightValue() {
        assertThat(testee.componentName())
            .isEqualTo(COMPONENT_NAME);
    }

    @Nested
    class WithTenPercentMaximumOfMissCount {

        @Test
        void checkShouldReturnHealthyWhenNoRetrieveCalled() {
            assertThat(testee.check().block())
                .isEqualTo(Result.healthy(COMPONENT_NAME));
        }

        @Test
        void checkShouldReturnDegradedWhenNoHitButSomeMiss() {
            missMetric.increment();
            missMetric.increment();

            assertThat(testee.check().block())
                .isEqualTo(Result.degraded(COMPONENT_NAME, "Miss percentage 100.00% (2/2) is higher than the threshold 10%"));
        }

        @Test
        void checkShouldReturnHealthyWhenMissCountPercentageIsLessThanThreshold() {
            missMetric.increment();
            hitMetric.add(43);

            assertThat(testee.check().block())
                .isEqualTo(Result.healthy(COMPONENT_NAME));
        }

        @Test
        void checkShouldReturnHealthyWhenMissCountPercentageIsEqualToThreshold() {
            missMetric.increment();
            hitMetric.add(9);

            assertThat(testee.check().block())
                .isEqualTo(Result.healthy(COMPONENT_NAME));
        }

        @Test
        void checkShouldReturnDegradedWhenMissCountPercentageIsGreaterThanThreshold() {
            missMetric.increment();
            hitMetric.add(3);

            assertThat(testee.check().block())
                .isEqualTo(Result.degraded(COMPONENT_NAME,
                    "Miss percentage 25.00% (1/4) is higher than the threshold 10%"));
        }

        @Test
        void checkShouldReturnHealthyAfterMoreHits() {
            missMetric.increment();
            hitMetric.increment();
            Result resultWithLessHit = testee.check().block();

            // more hits
            hitMetric.add(10);
            Result resultWithMoreHit = testee.check().block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(resultWithLessHit)
                    .isEqualTo(Result.degraded(COMPONENT_NAME,
                    "Miss percentage 50.00% (1/2) is higher than the threshold 10%"));
                softly.assertThat(resultWithMoreHit)
                    .isEqualTo(Result.healthy(COMPONENT_NAME));
            });
        }

        @Test
        void checkShouldKeepBeingDegradedAfterNotEnoughOfHits() {
            missMetric.increment();
            hitMetric.increment();
            Result resultWithLessHit = testee.check().block();

            // more hits, but not enough
            hitMetric.add(3);
            Result resultWithMoreHit = testee.check().block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(resultWithLessHit)
                    .isEqualTo(Result.degraded(COMPONENT_NAME,
                    "Miss percentage 50.00% (1/2) is higher than the threshold 10%"));
                softly.assertThat(resultWithMoreHit)
                    .isEqualTo(Result.degraded(COMPONENT_NAME,
                        "Miss percentage 20.00% (1/5) is higher than the threshold 10%"));
            });
        }

        @Test
        void checkShouldReturnDegradedAfterMoreMiss() {
            missMetric.increment();
            // enough of hits
            hitMetric.add(10);

            Result resultWithEnoughOfHits = testee.check().block();

            // more miss
            missMetric.increment();
            Result resultWithMoreMiss = testee.check().block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(resultWithEnoughOfHits)
                    .isEqualTo(Result.healthy(COMPONENT_NAME));
                softly.assertThat(resultWithMoreMiss)
                    .isEqualTo(Result.degraded(COMPONENT_NAME, "Miss percentage 16.67% (2/12) is higher than the threshold 10%"));
            });
        }

        @Test
        void checkShouldKeepBeingHealthyAfterNotEnoughOfMiss() {
            missMetric.increment();
            // enough of hits
            hitMetric.add(10000);
            Result resultWithEnoughOfHits = testee.check().block();

            // more miss, but not enough
            IntStream.rangeClosed(1, 3)
                .forEach(counter -> missMetric.increment());
            Result resultWithMoreMiss = testee.check().block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(resultWithEnoughOfHits)
                    .isEqualTo(Result.healthy(COMPONENT_NAME));
                softly.assertThat(resultWithMoreMiss)
                    .isEqualTo(Result.healthy(COMPONENT_NAME));
            });
        }
    }
}