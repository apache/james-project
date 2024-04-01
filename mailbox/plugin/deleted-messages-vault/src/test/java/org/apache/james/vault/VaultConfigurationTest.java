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
import java.time.temporal.ChronoUnit;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.mailbox.DefaultMailboxes;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class VaultConfigurationTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(VaultConfiguration.class)
            .verify();
    }

    @Test
    void constructorShouldThrowWhenRetentionPeriodIsNull() {
        assertThatThrownBy(() -> new VaultConfiguration(true, false, null, DefaultMailboxes.RESTORED_MESSAGES))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorShouldThrowWhenRestoreLocationIsNull() {
        assertThatThrownBy(() -> new VaultConfiguration(true, false, ChronoUnit.YEARS.getDuration(), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromShouldThrowWhenNull() {
        assertThatThrownBy(() -> VaultConfiguration.from(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromShouldReturnConfiguredRestoreLocation() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("restoreLocation", "INBOX");

        assertThat(VaultConfiguration.from(configuration)).isEqualTo(
            new VaultConfiguration(false, false, ChronoUnit.YEARS.getDuration(), DefaultMailboxes.INBOX));
    }

    @Test
    void fromShouldReturnConfiguredRetentionTime() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("retentionPeriod", "15d");

        assertThat(VaultConfiguration.from(configuration)).isEqualTo(
            new VaultConfiguration(false, false, Duration.ofDays(15), DefaultMailboxes.RESTORED_MESSAGES));
    }

    @Test
    void fromShouldHandleHours() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("retentionPeriod", "15h");

        assertThat(VaultConfiguration.from(configuration)).isEqualTo(
            new VaultConfiguration(false, false, Duration.ofHours(15), DefaultMailboxes.RESTORED_MESSAGES));
    }

    @Test
    void fromShouldUseDaysAsADefaultUnit() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("retentionPeriod", "15");

        assertThat(VaultConfiguration.from(configuration)).isEqualTo(
            new VaultConfiguration(false, false, Duration.ofDays(15), DefaultMailboxes.RESTORED_MESSAGES));
    }

    @Test
    void fromShouldReturnDefaultWhenNoConfigurationOptions() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        assertThat(VaultConfiguration.from(configuration)).isEqualTo(VaultConfiguration.DEFAULT);
    }
}