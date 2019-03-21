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

package org.apache.james.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class RetentionConfigurationTest {
    @Test
    void ShouldMatchBeanContract() {
        EqualsVerifier.forClass(RetentionConfiguration.class)
            .verify();
    }

    @Test
    void constructorShouldThrowWhenNull() {
        assertThatThrownBy(() -> new RetentionConfiguration(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromShouldThrowWhenNull() {
        assertThatThrownBy(() -> RetentionConfiguration.from(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromShouldReturnConfiguredRetentionTime() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("retentionPeriod", "15d");

        assertThat(RetentionConfiguration.from(configuration)).isEqualTo(new RetentionConfiguration(Duration.ofDays(15)));
    }

    @Test
    void fromShouldHandleHours() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("retentionPeriod", "15h");

        assertThat(RetentionConfiguration.from(configuration)).isEqualTo(new RetentionConfiguration(Duration.ofHours(15)));
    }

    @Test
    void fromShouldUseDaysAsADefaultUnit() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("retentionPeriod", "15");

        assertThat(RetentionConfiguration.from(configuration)).isEqualTo(new RetentionConfiguration(Duration.ofDays(15)));
    }

    @Test
    void fromShouldReturnDefaultWhenNoRetentionTime() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        assertThat(RetentionConfiguration.from(configuration)).isEqualTo(RetentionConfiguration.DEFAULT);
    }
}