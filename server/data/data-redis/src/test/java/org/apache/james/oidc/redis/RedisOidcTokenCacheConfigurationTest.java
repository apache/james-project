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

package org.apache.james.oidc.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

class RedisOidcTokenCacheConfigurationTest {
    @Test
    void shouldDefaultCommandTimeout() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        assertThat(RedisOidcTokenCacheConfiguration.from(configuration).commandTimeout())
            .isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void shouldParseDuration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(RedisOidcTokenCacheConfiguration.COMMAND_TIMEOUT_PROPERTY, "500ms");

        assertThat(RedisOidcTokenCacheConfiguration.from(configuration).commandTimeout())
            .isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void shouldRejectInvalidCommandTimeout() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(RedisOidcTokenCacheConfiguration.COMMAND_TIMEOUT_PROPERTY, "invalid");

        assertThatThrownBy(() -> RedisOidcTokenCacheConfiguration.from(configuration))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void shouldRejectZeroCommandTimeout() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(RedisOidcTokenCacheConfiguration.COMMAND_TIMEOUT_PROPERTY, "0seconds");

        assertThatThrownBy(() -> RedisOidcTokenCacheConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
