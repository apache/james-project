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

package org.apache.james.blob.zstd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.Test;

class CompressionConfigurationTest {
    @Test
    void builderShouldReturnDefaultConfigurationByDefault() {
        CompressionConfiguration compressionConfiguration = CompressionConfiguration.builder()
            .build();

        assertSoftly(softly -> {
            softly.assertThat(compressionConfiguration.enabled()).isFalse();
            softly.assertThat(compressionConfiguration.threshold()).isEqualTo(16 * 1024L);
            softly.assertThat(compressionConfiguration.minRatio()).isEqualTo(1F);
        });
    }

    @Test
    void builderShouldSupportEnabledConfiguration() {
        CompressionConfiguration compressionConfiguration = CompressionConfiguration.builder()
            .enabled(true)
            .threshold(42)
            .minRatio(0.8F)
            .build();

        assertSoftly(softly -> {
            softly.assertThat(compressionConfiguration.enabled()).isTrue();
            softly.assertThat(compressionConfiguration.threshold()).isEqualTo(42L);
            softly.assertThat(compressionConfiguration.minRatio()).isEqualTo(0.8F);
        });
    }

    @Test
    void builderShouldSupportZeroMinRatio() {
        assertThat(CompressionConfiguration.builder()
            .minRatio(0F)
            .build()
            .minRatio())
            .isZero();
    }

    @Test
    void builderShouldThrowWhenThresholdIsZero() {
        assertThatThrownBy(() -> CompressionConfiguration.builder()
            .threshold(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'threshold' needs to be strictly positive");
    }

    @Test
    void builderShouldThrowWhenThresholdIsNegative() {
        assertThatThrownBy(() -> CompressionConfiguration.builder()
            .threshold(-1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'threshold' needs to be strictly positive");
    }

    @Test
    void builderShouldThrowWhenMinRatioIsNegative() {
        assertThatThrownBy(() -> CompressionConfiguration.builder()
            .minRatio(-0.1F))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'minRatio' needs to be between 0 and 1");
    }

    @Test
    void builderShouldThrowWhenMinRatioIsAboveOne() {
        assertThatThrownBy(() -> CompressionConfiguration.builder()
            .minRatio(1.1F))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'minRatio' needs to be between 0 and 1");
    }

}
