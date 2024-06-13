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

package org.apache.james.backends.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.r2dbc.postgresql.client.SSLMode;

class PostgresConfigurationTest {

    @Test
    void shouldReturnCorrespondingProperties() {
        PostgresConfiguration configuration = PostgresConfiguration.builder()
            .host("1.1.1.1")
            .port(1111)
            .databaseName("db")
            .databaseSchema("sc")
            .username("james")
            .password("1")
            .byPassRLSUser("bypassrlsjames")
            .byPassRLSPassword("2")
            .rowLevelSecurityEnabled()
            .sslMode("require")
            .build();

        assertThat(configuration.getHost()).isEqualTo("1.1.1.1");
        assertThat(configuration.getPort()).isEqualTo(1111);
        assertThat(configuration.getDatabaseName()).isEqualTo("db");
        assertThat(configuration.getDatabaseSchema()).isEqualTo("sc");
        assertThat(configuration.getDefaultCredential().getUsername()).isEqualTo("james");
        assertThat(configuration.getDefaultCredential().getPassword()).isEqualTo("1");
        assertThat(configuration.getByPassRLSCredential().getUsername()).isEqualTo("bypassrlsjames");
        assertThat(configuration.getByPassRLSCredential().getPassword()).isEqualTo("2");
        assertThat(configuration.rowLevelSecurityEnabled()).isEqualTo(true);
        assertThat(configuration.getSslMode()).isEqualTo(SSLMode.REQUIRE);
    }

    @Test
    void shouldUseDefaultValues() {
        PostgresConfiguration configuration = PostgresConfiguration.builder()
            .username("james")
            .password("1")
            .build();

        assertThat(configuration.getHost()).isEqualTo(PostgresConfiguration.HOST_DEFAULT_VALUE);
        assertThat(configuration.getPort()).isEqualTo(PostgresConfiguration.PORT_DEFAULT_VALUE);
        assertThat(configuration.getDatabaseName()).isEqualTo(PostgresConfiguration.DATABASE_NAME_DEFAULT_VALUE);
        assertThat(configuration.getDatabaseSchema()).isEqualTo(PostgresConfiguration.DATABASE_SCHEMA_DEFAULT_VALUE);
        assertThat(configuration.getByPassRLSCredential().getUsername()).isEqualTo("james");
        assertThat(configuration.getByPassRLSCredential().getPassword()).isEqualTo("1");
        assertThat(configuration.rowLevelSecurityEnabled()).isEqualTo(false);
        assertThat(configuration.getSslMode()).isEqualTo(SSLMode.ALLOW);
    }

    @Test
    void shouldThrowWhenMissingUsername() {
        assertThatThrownBy(() -> PostgresConfiguration.builder()
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("You need to specify username");
    }

    @Test
    void shouldThrowWhenMissingPassword() {
        assertThatThrownBy(() -> PostgresConfiguration.builder()
            .username("james")
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("You need to specify password");
    }

    @Test
    void shouldThrowWhenMissingByPassRLSUserAndRLSIsEnabled() {
        assertThatThrownBy(() -> PostgresConfiguration.builder()
            .username("james")
            .password("1")
            .rowLevelSecurityEnabled()
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("You need to specify byPassRLSUser");
    }

    @Test
    void shouldThrowWhenMissingByPassRLSPasswordAndRLSIsEnabled() {
        assertThatThrownBy(() -> PostgresConfiguration.builder()
            .username("james")
            .password("1")
            .byPassRLSUser("bypassrlsjames")
            .rowLevelSecurityEnabled()
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("You need to specify byPassRLSPassword");
    }

    @Test
    void shouldThrowWhenInvalidSslMode() {
        assertThatThrownBy(() -> PostgresConfiguration.builder()
            .username("james")
            .password("1")
            .sslMode("invalid")
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid ssl mode value: invalid");
    }
}
