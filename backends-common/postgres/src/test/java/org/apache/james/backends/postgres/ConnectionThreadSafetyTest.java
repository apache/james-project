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

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;

import org.apache.james.backends.postgres.utils.SimpleJamesPostgresConnectionFactory;
import org.apache.james.core.Domain;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;

@Testcontainers
public class ConnectionThreadSafetyTest {
    static final String DB_NAME = "james-db";
    static final String DB_USER = "james";
    static final String DB_PASSWORD = "1";
    static final String CREATE_TABLE_STATEMENT = "CREATE TABLE IF NOT EXISTS person (\n" +
        "\tid serial PRIMARY KEY,\n" +
        "\tname VARCHAR ( 50 ) UNIQUE NOT NULL\n" +
        ");";

    @Container
    private static final GenericContainer<?> container = new PostgreSQLContainer("postgres:11.1")
        .withDatabaseName(DB_NAME)
        .withUsername(DB_USER)
        .withPassword(DB_PASSWORD);

    private static PostgresqlConnectionFactory connectionFactory;
    private static SimpleJamesPostgresConnectionFactory jamesPostgresConnectionFactory;

    @BeforeAll
    static void beforeAll() {
        connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
            .host(container.getHost())
            .port(container.getMappedPort(5432))
            .username(DB_USER)
            .password(DB_PASSWORD)
            .database(DB_NAME)
            .build());
        jamesPostgresConnectionFactory = new SimpleJamesPostgresConnectionFactory(connectionFactory);

        connectionFactory.create().flatMapMany(connection -> connection
            .createStatement(CREATE_TABLE_STATEMENT)
            .execute()
            .flatMap(PostgresqlResult::getRowsUpdated)
            .doFinally(signalType -> connection.close())
            .collect(Collectors.toUnmodifiableList())).blockLast();
    }

    @Test
    void test() throws Exception {
        createData();

        PostgresqlConnection connection = jamesPostgresConnectionFactory.getConnection(Domain.of("james")).block();

        List<String> actual = new Vector<>();
        for (int i=1; i<=200; i++) {
            final int count = i;
            Thread thread = new Thread(() -> {
                connection.createStatement("SELECT id, name FROM PERSON WHERE id = $1")
                    .bind("$1", count)
                    .execute()
                    .flatMap(result -> result.map((row, rowMetadata) -> row.get("id", Long.class) + "|" + row.get("name", String.class)))
                    .doOnNext(s -> actual.add(s))
                    .collect(Collectors.toUnmodifiableList())
                    .block();
            });
            thread.start();
            thread.join();
        }

        List<String> expected = new ArrayList<>();
        for (int i=1; i<=200; i++) {
            expected.add(i+"|Peter"+i);
        }

        assertThat(expected).containsExactlyInAnyOrderElementsOf(actual);
    }

    private static void createData() {
        PostgresqlConnection connection = connectionFactory.create().block();
        for (int i=1; i<=200; i++) {
            final int count = i;
            connection.createStatement("INSERT INTO person (name) VALUES ($1)")
                .bind("$1", "Peter"+count)
                .execute().flatMap(PostgresqlResult::getRowsUpdated)
                .collect(Collectors.toUnmodifiableList()).block();
        }
    }
}
