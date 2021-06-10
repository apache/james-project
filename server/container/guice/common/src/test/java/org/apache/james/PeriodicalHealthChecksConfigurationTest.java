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

import static org.apache.james.PeriodicalHealthChecksConfiguration.DEFAULT_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.util.DurationParser;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class PeriodicalHealthChecksConfigurationTest {

    private static final String HEALTH_CHECK_PERIOD = "healthcheck.period";
    private static final String PERIOD = "10s";
    private static final String EMPTY_STRING = "";
    private static final String RANDOM_STRING = "abcdsfsfs";

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(PeriodicalHealthChecksConfiguration.class)
            .verify();
    }

    @Test
    void builderShouldThrowWhenPeriodIsNull() {
        assertThatThrownBy(() -> PeriodicalHealthChecksConfiguration.builder()
            .period(null)
            .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builderShouldThrowWhenPeriodHasIncorrectFormat() {
        assertThatThrownBy(() -> PeriodicalHealthChecksConfiguration.builder()
            .period(DurationParser.parse(RANDOM_STRING))
            .build())
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void builderShouldThrowWhenPeriodIsLessThanMinimalValue() {
        assertThatThrownBy(() -> PeriodicalHealthChecksConfiguration.builder()
            .period(Duration.ofSeconds(1))
            .build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builderShouldReturnCorrectConfiguration() {
        PeriodicalHealthChecksConfiguration configuration = PeriodicalHealthChecksConfiguration.builder()
            .period(DurationParser.parse(PERIOD))
            .build();

        assertThat(configuration.getPeriod()).isEqualTo(DurationParser.parse(PERIOD));
    }

    @Test
    void fromShouldReturnDefaultConfigurationWhenPeriodIsMissing() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        assertThat(PeriodicalHealthChecksConfiguration.from(configuration)).isEqualTo(PeriodicalHealthChecksConfiguration.builder()
            .period(DEFAULT_CONFIGURATION.getPeriod())
            .build());
    }

    @Test
    void fromShouldReturnDefaultConfigurationWhenPeriodIsNull() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(HEALTH_CHECK_PERIOD, null);

        assertThat(PeriodicalHealthChecksConfiguration.from(configuration)).isEqualTo(PeriodicalHealthChecksConfiguration.builder()
            .period(DEFAULT_CONFIGURATION.getPeriod())
            .build());
    }

    @Test
    void fromShouldReturnDefaultConfigurationWhenPeriodIsEmpty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(HEALTH_CHECK_PERIOD, EMPTY_STRING);

        assertThat(PeriodicalHealthChecksConfiguration.from(configuration)).isEqualTo(PeriodicalHealthChecksConfiguration.builder()
            .period(DEFAULT_CONFIGURATION.getPeriod())
            .build());
    }

    @Test
    void fromShouldThrowWhenPeriodHasIncorrectFormat() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(HEALTH_CHECK_PERIOD, RANDOM_STRING);

        assertThatThrownBy(() -> PeriodicalHealthChecksConfiguration.from(configuration))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void fromShouldReturnProvidedConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(HEALTH_CHECK_PERIOD, PERIOD);

        assertThat(PeriodicalHealthChecksConfiguration.from(configuration)).isEqualTo(PeriodicalHealthChecksConfiguration.builder()
            .period(DurationParser.parse(PERIOD))
            .build());
    }
}