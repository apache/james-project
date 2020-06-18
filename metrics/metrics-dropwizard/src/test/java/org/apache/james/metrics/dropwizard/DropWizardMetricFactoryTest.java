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

package org.apache.james.metrics.dropwizard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.MetricFactoryContract;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.codahale.metrics.MetricRegistry;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DropWizardMetricFactoryTest implements MetricFactoryContract {

    private DropWizardMetricFactory testee;

    @BeforeEach
    void setUp() {
        testee = new DropWizardMetricFactory(new MetricRegistry());
    }

    @Override
    public MetricFactory testee() {
        return testee;
    }

    @Test
    void decoratePublisherWithTimerMetricShouldRecordANewValueForEachRetry() {
        Duration duration = Duration.ofMillis(100);
        Mono.from(testee.decoratePublisherWithTimerMetric("any", Mono.delay(duration)))
            .repeat(5)
            .blockLast();

        assertThat(testee.timer("any").getTimer().getSnapshot().get99thPercentile())
            .isLessThan(duration.get(ChronoUnit.NANOS) * 2);
    }

    @Test
    void decoratePublisherWithTimerMetricLogP99ShouldRecordANewValueForEachRetry() {
        Duration duration = Duration.ofMillis(100);
        Mono.from(testee.decoratePublisherWithTimerMetricLogP99("any", Mono.delay(duration)))
            .repeat(5)
            .blockLast();

        assertThat(testee.timer("any").getTimer().getSnapshot().get99thPercentile())
            .isLessThan(duration.get(ChronoUnit.NANOS) * 2);
    }

    @Test
    void timerShouldAllowRecordingIndividualAndTotalTiming() {
        Duration duration = Duration.ofMillis(100);
        Flux.from(testee.decoratePublisherWithTimerMetric("anyTotal",
            Flux.from(testee.decoratePublisherWithTimerMetric("any", Mono.delay(duration)))
            .repeat(5)))
            .blockLast();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(testee.timer("any").getTimer().getCount())
                .isEqualTo(6);
            softly.assertThat(testee.timer("anyTotal").getTimer().getCount())
                .isEqualTo(1);
        });
    }

     @Test
    void timerShouldAllowRecordingIndividualAndTotalTimingWithP99() {
        Duration duration = Duration.ofMillis(100);
        Flux.from(testee.decoratePublisherWithTimerMetric("anyTotal",
            Flux.from(testee.decoratePublisherWithTimerMetric("any", Mono.delay(duration)))
            .repeat(5)))
            .blockLast();
         SoftAssertions.assertSoftly(softly -> {
             softly.assertThat(testee.timer("any").getTimer().getSnapshot().get99thPercentile())
                 .isLessThan(duration.get(ChronoUnit.NANOS) * 2)
                 .isGreaterThan(duration.get(ChronoUnit.NANOS));
             softly.assertThat(testee.timer("anyTotal").getTimer().getSnapshot().get99thPercentile())
                 .isLessThan(duration.get(ChronoUnit.NANOS) * 7)
                 .isGreaterThan(duration.get(ChronoUnit.NANOS) * 6);
         });
    }
}