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

package org.apache.james.jmap.cassandra.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

class CassandraChangesConfigurationTest {
    private static final Duration TOO_BIG_TTL = Duration.ofSeconds(Integer.MAX_VALUE + 1L);

    @Test
    void fromShouldReturnValuesFromSuppliedConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("email.change.ttl", "3 days");
        configuration.addProperty("mailbox.change.ttl", "12 hours");

        assertThat(CassandraChangesConfiguration.from(configuration))
            .isEqualTo(new CassandraChangesConfiguration.Builder()
                .emailChangeTtl(Duration.ofDays(3))
                .mailboxChangeTtl(Duration.ofHours(12))
                .build());
    }

    @Test
    void fromShouldFallbackToDefaultValueWhenEmptySuppliedConfiguration() {
        PropertiesConfiguration emptyConfiguration = new PropertiesConfiguration();

        assertThat(CassandraChangesConfiguration.from(emptyConfiguration))
            .isEqualTo(new CassandraChangesConfiguration.Builder()
                .emailChangeTtl(Duration.ofDays(60))
                .mailboxChangeTtl(Duration.ofDays(60))
                .build());
    }

    @Test
    void shouldThrowWhenConfiguredNegativeTTL() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("email.change.ttl", "-300");

        assertThatThrownBy(() -> CassandraChangesConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenConfiguredZeroTTL() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("email.change.ttl", "0");

        assertThatThrownBy(() -> CassandraChangesConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenConfiguredTooBigTTL() {
        assertThatThrownBy(() -> new CassandraChangesConfiguration.Builder()
            .emailChangeTtl(TOO_BIG_TTL)
            .mailboxChangeTtl(TOO_BIG_TTL)
            .build())
            .isInstanceOf(IllegalArgumentException.class);
    }
}
