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

import static org.apache.james.events.RetryBackoffConfiguration.DEFAULT_FIRST_BACKOFF;
import static org.apache.james.events.RetryBackoffConfiguration.DEFAULT_JITTER_FACTOR;
import static org.apache.james.events.RetryBackoffConfiguration.DEFAULT_MAX_RETRIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class RetryBackoffConfigurationTest {

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(RetryBackoffConfiguration.class)
            .verify();
    }

    @Test
    void buildShouldThrowWhenNegativeFirstBackoff() {
        assertThatThrownBy(() -> RetryBackoffConfiguration.builder()
            .maxRetries(DEFAULT_MAX_RETRIES)
            .firstBackoff(Duration.ofMillis(-1000L))
            .jitterFactor(DEFAULT_JITTER_FACTOR)
            .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("firstBackoff is not allowed to be negative");
    }

    @Test
    void buildShouldThrowWhenNegativeMaxRetries() {
        assertThatThrownBy(() -> RetryBackoffConfiguration.builder()
            .maxRetries(-6)
            .firstBackoff(DEFAULT_FIRST_BACKOFF)
            .jitterFactor(DEFAULT_JITTER_FACTOR)
            .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maxRetries is not allowed to be negative");
    }

    @Test
    void buildShouldThrowWhenNegativeJitterFactor() {
        assertThatThrownBy(() -> RetryBackoffConfiguration.builder()
            .maxRetries(DEFAULT_MAX_RETRIES)
            .firstBackoff(DEFAULT_FIRST_BACKOFF)
            .jitterFactor(-2.5)
            .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("jitterFactor is not allowed to be negative or greater than 1");
    }

    @Test
    void buildShouldThrowWhenGreaterThanOneJitterFactor() {
        assertThatThrownBy(() -> RetryBackoffConfiguration.builder()
            .maxRetries(DEFAULT_MAX_RETRIES)
            .firstBackoff(DEFAULT_FIRST_BACKOFF)
            .jitterFactor(1.000001)
            .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("jitterFactor is not allowed to be negative or greater than 1");
    }

    @Test
    void buildShouldSuccessWhenZeroFirstBackoff() {
        RetryBackoffConfiguration retryBackoff = RetryBackoffConfiguration.builder()
            .maxRetries(DEFAULT_MAX_RETRIES)
            .firstBackoff(Duration.ZERO)
            .jitterFactor(DEFAULT_JITTER_FACTOR)
            .build();

        assertThat(retryBackoff.getFirstBackoff().toMillis())
            .isEqualTo(0L);
    }

    @Test
    void buildShouldSuccessWhenZeroMaxRetries() {
        RetryBackoffConfiguration retryBackoff = RetryBackoffConfiguration.builder()
            .maxRetries(0)
            .firstBackoff(DEFAULT_FIRST_BACKOFF)
            .jitterFactor(DEFAULT_JITTER_FACTOR)
            .build();

        assertThat(retryBackoff.getMaxRetries())
            .isEqualTo(0L);
    }

    @Test
    void buildShouldSuccessWhenZeroJitterFactor() {
        RetryBackoffConfiguration retryBackoff = RetryBackoffConfiguration.builder()
            .maxRetries(DEFAULT_MAX_RETRIES)
            .firstBackoff(DEFAULT_FIRST_BACKOFF)
            .jitterFactor(0)
            .build();

        assertThat(retryBackoff.getJitterFactor())
            .isEqualTo(0);
    }

    @Test
    void buildShouldReturnCorrespondingValues() {
        RetryBackoffConfiguration retryBackoff = RetryBackoffConfiguration.builder()
            .maxRetries(5)
            .firstBackoff(Duration.ofMillis(200))
            .jitterFactor(0.6)
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(retryBackoff.getJitterFactor())
                .isEqualTo(0.6);
            softly.assertThat(retryBackoff.getMaxRetries())
                .isEqualTo(5);
            softly.assertThat(retryBackoff.getFirstBackoff())
                .isEqualTo(Duration.ofMillis(200));
        });
    }

}