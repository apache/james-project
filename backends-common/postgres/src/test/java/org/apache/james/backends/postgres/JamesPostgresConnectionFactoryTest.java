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

import org.apache.james.backends.postgres.utils.SimpleJamesPostgresConnectionFactory;
import org.apache.james.core.Domain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.Connection;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Testcontainers
public class JamesPostgresConnectionFactoryTest {
    static final String DB_NAME = "james-db";
    static final String DB_USER = "james";
    static final String DB_PASSWORD = "1";

    @Container
    static final GenericContainer<?> container = new PostgreSQLContainer("postgres:11.1")
        .withDatabaseName(DB_NAME)
        .withUsername(DB_USER)
        .withPassword(DB_PASSWORD);

    private PostgresqlConnectionFactory connectionFactory;
    private SimpleJamesPostgresConnectionFactory jamesPostgresConnectionFactory;

    @BeforeEach
    void beforeEach() {
        container.stop();
        container.start();
        connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
            .host(container.getHost())
            .port(container.getMappedPort(5432))
            .username(DB_USER)
            .password(DB_PASSWORD)
            .database(DB_NAME)
            .build());
        jamesPostgresConnectionFactory = new SimpleJamesPostgresConnectionFactory(connectionFactory);
    }

    @Test
    void factoryShouldCreateCorrectNumberOfConnection() {
        // create 50 connection
        Flux.range(1, 50)
            .flatMap(i -> jamesPostgresConnectionFactory.getConnection(Domain.of("james"+i)))
            .last()
            .block();

        Integer dbActiveConnectionNumber = Mono.from(connectionFactory.create())
            .flatMap(connection -> Mono.from(connection.createStatement("SELECT count(*) from pg_stat_activity where usename = $1;")
                .bind("$1", DB_USER)
                .execute()))
            .flatMap(result -> Mono.from(result.map((row, rowMetadata) -> row.get(0, Integer.class))))
            .block();

        // It should be 51 connections to the DB plus one connection to check number of connection
        assertThat(dbActiveConnectionNumber).isEqualTo(52);
    }

    @Test
    void factoryShouldNotCreateNewConnectionWhenDomainsAreTheSame() {
        Connection connectionOne = jamesPostgresConnectionFactory.getConnection(Domain.of("james")).block();
        Connection connectionTwo = jamesPostgresConnectionFactory.getConnection(Domain.of("james")).block();

        assertThat(connectionOne == connectionTwo).isTrue();
    }

}
