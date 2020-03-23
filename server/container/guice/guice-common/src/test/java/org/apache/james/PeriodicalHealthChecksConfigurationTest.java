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

package org.apache.james;

import static org.apache.james.PeriodicalHealthChecksConfiguration.DEFAULT_HEALTH_CHECK_INITIAL_DELAY;
import static org.apache.james.PeriodicalHealthChecksConfiguration.DEFAULT_HEALTH_CHECK_PERIOD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConversionException;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class PeriodicalHealthChecksConfigurationTest {

    private static final String EMPTY_STRING = "";
    private static final String RANDOM_STRING = "abcdsfsfs";
    private static final long NEGATIVE_NUMBER = -1;
    private static final long INITIAL_DELAY = 10;
    private static final long PERIOD = 5;

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(PeriodicalHealthChecksConfiguration.class)
            .verify();
    }

    @Test
    void fromShouldThrowWhenInitialDelayIsEmpty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_INITIAL_DELAY, EMPTY_STRING);
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_PERIOD, DEFAULT_HEALTH_CHECK_PERIOD);

        assertThatThrownBy(() -> PeriodicalHealthChecksConfiguration. from(configuration))
            .isInstanceOf(ConversionException.class);
    }

    @Test
    void fromShouldThrowWhenPeriodIsEmpty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_INITIAL_DELAY, DEFAULT_HEALTH_CHECK_INITIAL_DELAY);
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_PERIOD, EMPTY_STRING);

        assertThatThrownBy(() -> PeriodicalHealthChecksConfiguration.from(configuration))
            .isInstanceOf(ConversionException.class);
    }

    @Test
    void fromShouldReturnConfigurationWithDefaultValueWhenInitialDelayIsMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_PERIOD, PERIOD);

        assertThat(PeriodicalHealthChecksConfiguration.from(configuration)).isEqualTo(PeriodicalHealthChecksConfiguration.builder()
            .initialDelay(DEFAULT_HEALTH_CHECK_INITIAL_DELAY)
            .period(PERIOD)
            .build());
    }

    @Test
    void fromShouldReturnConfigurationWithDefaultValueWhenPeriodIsMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_INITIAL_DELAY, INITIAL_DELAY);

        assertThat(PeriodicalHealthChecksConfiguration.from(configuration)).isEqualTo(PeriodicalHealthChecksConfiguration.builder()
            .initialDelay(INITIAL_DELAY)
            .period(DEFAULT_HEALTH_CHECK_PERIOD)
            .build());
    }

    @Test
    void fromShouldReturnConfigurationWithDefaultValueWhenInitialDelayIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_INITIAL_DELAY, null);
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_PERIOD, PERIOD);

        assertThat(PeriodicalHealthChecksConfiguration.from(configuration)).isEqualTo(PeriodicalHealthChecksConfiguration.builder()
            .initialDelay(DEFAULT_HEALTH_CHECK_INITIAL_DELAY)
            .period(PERIOD)
            .build());
    }

    @Test
    void fromShouldReturnConfigurationWithDefaultValueWhenPeriodIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_INITIAL_DELAY, INITIAL_DELAY);
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_PERIOD, null);

        assertThat(PeriodicalHealthChecksConfiguration.from(configuration)).isEqualTo(PeriodicalHealthChecksConfiguration.builder()
            .initialDelay(INITIAL_DELAY)
            .period(DEFAULT_HEALTH_CHECK_PERIOD)
            .build());
    }

    @Test
    void fromShouldThrowWhenInitialDelayIsNotANumber() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_INITIAL_DELAY, RANDOM_STRING);
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_PERIOD, PERIOD);

        assertThatThrownBy(() -> PeriodicalHealthChecksConfiguration.from(configuration))
            .isInstanceOf(ConversionException.class);
    }

    @Test
    void fromShouldThrowWhenInitialDelayIsNegative() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_INITIAL_DELAY, NEGATIVE_NUMBER);
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_PERIOD, PERIOD);

        assertThatThrownBy(() -> PeriodicalHealthChecksConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldThrowWhenPeriodIsNegative() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_INITIAL_DELAY, INITIAL_DELAY);
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_PERIOD, NEGATIVE_NUMBER);

        assertThatThrownBy(() -> PeriodicalHealthChecksConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldThrowWhenPeriodIsNotANumber() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_INITIAL_DELAY, INITIAL_DELAY);
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_PERIOD, RANDOM_STRING);

        assertThatThrownBy(() -> PeriodicalHealthChecksConfiguration.from(configuration))
            .isInstanceOf(ConversionException.class);
    }

    @Test
    void fromShouldReturnProvidedConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_INITIAL_DELAY, INITIAL_DELAY);
        configuration.addProperty(PeriodicalHealthChecksConfiguration.HEALTH_CHECK_PERIOD, PERIOD);

        assertThat(PeriodicalHealthChecksConfiguration.from(configuration)).isEqualTo(PeriodicalHealthChecksConfiguration.builder()
            .initialDelay(INITIAL_DELAY)
            .period(PERIOD)
            .build());
    }
}
