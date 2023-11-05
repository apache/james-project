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

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.backends.postgres.utils.JamesPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.SimpleJamesPostgresConnectionFactory;
import org.apache.james.core.Domain;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.spi.Connection;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Testcontainers
public class SimpleJamesPostgresConnectionFactoryTest extends JamesPostgresConnectionFactoryTest {
    static final String DB_NAME = "james-db";
    static final String DB_USER = "james";
    static final String DB_PASSWORD = "1";

    @Container
    static final PostgreSQLContainer<?> container = new PostgreSQLContainer("postgres:16.0")
        .withDatabaseName(DB_NAME)
        .withUsername(DB_USER)
        .withPassword(DB_PASSWORD);

    private PostgresqlConnectionFactory connectionFactory;
    private SimpleJamesPostgresConnectionFactory jamesPostgresConnectionFactory;

    JamesPostgresConnectionFactory jamesPostgresConnectionFactory() {
        return jamesPostgresConnectionFactory;
    }

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
    void factoryShouldCreateCorrectNumberOfConnections() {
        PostgresqlConnection connection = connectionFactory.create().block();
        Integer previousDbActiveNumberOfConnections = getNumberOfConnections(connection);

        // create 50 connections
        Flux.range(1, 50)
            .flatMap(i -> jamesPostgresConnectionFactory.getConnection(Domain.of("james"+i)))
            .last()
            .block();

        Integer dbActiveNumberOfConnections = getNumberOfConnections(connection);

        assertThat(dbActiveNumberOfConnections - previousDbActiveNumberOfConnections).isEqualTo(50);
    }

    @Nullable
    private static Integer getNumberOfConnections(PostgresqlConnection connection) {
        return Mono.from(connection.createStatement("SELECT count(*) from pg_stat_activity where usename = $1;")
            .bind("$1", DB_USER)
            .execute()).flatMap(result -> Mono.from(result.map((row, rowMetadata) -> row.get(0, Integer.class)))).block();
    }

    @Test
    void factoryShouldNotCreateNewConnectionWhenDomainsAreTheSame() {
        Domain domain = Domain.of("james");
        Connection connectionOne = jamesPostgresConnectionFactory.getConnection(domain).block();
        Connection connectionTwo = jamesPostgresConnectionFactory.getConnection(domain).block();

        assertThat(connectionOne == connectionTwo).isTrue();
    }

    @Test
    void factoryShouldNotCreateNewConnectionWhenDomainsAreTheSameAndRequestsAreFromDifferentThreads() throws Exception {
        Set<Connection> connectionSet = ConcurrentHashMap.newKeySet();

        ConcurrentTestRunner.builder()
            .reactorOperation((threadNumber, step) -> jamesPostgresConnectionFactory.getConnection(Domain.of("james"))
                .doOnNext(connectionSet::add)
                .then())
            .threadCount(100)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(connectionSet).hasSize(1);
    }

    @Test
    void factoryShouldCreateOnlyOneDefaultConnection() throws Exception {
        Set<PostgresqlConnection> connectionSet = ConcurrentHashMap.newKeySet();

        ConcurrentTestRunner.builder()
            .reactorOperation((threadNumber, step) -> jamesPostgresConnectionFactory.getConnection(Optional.empty())
                .doOnNext(connectionSet::add)
                .then())
            .threadCount(100)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(connectionSet).hasSize(1);
    }

}
