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

package org.apache.james.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class WaitDelayGeneratorTest {

    @Test
    void generateDelayShouldReturnZeroWhenZeroRetryCount() {
        WaitDelayGenerator generator = WaitDelayGenerator.of(RetryBackoffConfiguration.DEFAULT);

        assertThat(generator.generateDelay(0))
            .isEqualTo(Duration.ofMillis(0));
    }

    @Test
    void generateDelayShouldReturnByRandomInRangeOfExponentialGrowthOfRetryCount() {
        WaitDelayGenerator generator = WaitDelayGenerator.of(RetryBackoffConfiguration.builder()
            .maxRetries(4)
            .firstBackoff(Duration.ofMillis(100))
            .jitterFactor(0.5)
            .build());

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(generator.generateDelay(1).toMillis())
                .isBetween(50L, 150L);
            softly.assertThat(generator.generateDelay(2).toMillis())
                .isBetween(100L, 300L);
            softly.assertThat(generator.generateDelay(3).toMillis())
                .isBetween(200L, 600L);
            softly.assertThat(generator.generateDelay(4).toMillis())
                .isBetween(300L, 1200L);
        });
    }

    @Test
    void generateDelayShouldReturnZeroWhenZeroMaxRetries() {
        WaitDelayGenerator generator = WaitDelayGenerator.of(RetryBackoffConfiguration.builder()
            .maxRetries(0)
            .firstBackoff(Duration.ofMillis(1000))
            .jitterFactor(0.5)
            .build());

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(generator.generateDelay(1)).isEqualTo(Duration.ZERO);
            softly.assertThat(generator.generateDelay(2)).isEqualTo(Duration.ZERO);
            softly.assertThat(generator.generateDelay(3)).isEqualTo(Duration.ZERO);
        });
    }

    @Test
    void generateDelayShouldReturnZeroWhenZeroFirstBackOff() {
        WaitDelayGenerator generator = WaitDelayGenerator.of(RetryBackoffConfiguration.builder()
            .maxRetries(3)
            .firstBackoff(Duration.ZERO)
            .jitterFactor(0.5)
            .build());

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(generator.generateDelay(1)).isEqualTo(Duration.ZERO);
            softly.assertThat(generator.generateDelay(2)).isEqualTo(Duration.ZERO);
            softly.assertThat(generator.generateDelay(3)).isEqualTo(Duration.ZERO);
        });
    }

    @Test
    void generateDelayShouldReturnFloorOfExponentialGrowthStepsWhenZeroJitterFactor() {
        WaitDelayGenerator generator = WaitDelayGenerator.of(RetryBackoffConfiguration.builder()
            .maxRetries(3)
            .firstBackoff(Duration.ofMillis(100))
            .jitterFactor(0.0)
            .build());

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(generator.generateDelay(1)).isEqualTo(Duration.ofMillis(100));
            softly.assertThat(generator.generateDelay(2)).isEqualTo(Duration.ofMillis(200));
            softly.assertThat(generator.generateDelay(3)).isEqualTo(Duration.ofMillis(400));
        });
    }
}