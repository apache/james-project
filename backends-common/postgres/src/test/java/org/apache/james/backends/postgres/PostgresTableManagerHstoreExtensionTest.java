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

import static org.apache.james.backends.postgres.PostgresFixture.Database.DEFAULT_DATABASE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.james.backends.postgres.utils.PoolBackedPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.postgres.extensions.types.Hstore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.Connection;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PostgresTableManagerHstoreExtensionTest {
    private static final int POOL_SIZE = 3;
    private static final Table<?> TABLE = DSL.table("hstore_table");
    private static final Field<Hstore> HSTORE_COLUMN = DSL.field("hstore_column", PostgresCommons.DataTypes.HSTORE);
    private static final PostgresDataDefinition MODULE = PostgresDataDefinition.table(PostgresTable.name(TABLE.getName())
        .createTableStep((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
            .column(HSTORE_COLUMN))
        .disableRowLevelSecurity()
        .build());

    private final PostgreSQLContainer<?> container = DockerPostgresSingleton.SINGLETON;
    private String databaseName;
    private PoolBackedPostgresConnectionFactory pool;
    private PostgresExecutor postgresExecutor;
    private PostgresqlConnectionFactory connectionFactory;
    private PostgresConfiguration postgresConfiguration;

    @BeforeEach
    void setUp() throws Exception {
        // A database of its own: the one of PostgresExtension already has the hstore extension
        databaseName = "hstore_" + UUID.randomUUID().toString().replace("-", "");
        container.execInContainer("psql", "-U", DEFAULT_DATABASE.dbUser(), "-c", "CREATE DATABASE " + databaseName + ";");

        postgresConfiguration = PostgresConfiguration.builder()
            .databaseName(databaseName)
            .databaseSchema(DEFAULT_DATABASE.schema())
            .host(container.getHost())
            .port(container.getMappedPort(PostgresFixture.PORT))
            .username(DEFAULT_DATABASE.dbUser())
            .password(DEFAULT_DATABASE.dbPassword())
            .rowLevelSecurityEnabled(false)
            .jooqReactiveTimeout(Optional.of(Duration.ofSeconds(20L)))
            .build();
        connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
            .host(postgresConfiguration.getHost())
            .port(postgresConfiguration.getPort())
            .database(databaseName)
            .schema(DEFAULT_DATABASE.schema())
            .username(DEFAULT_DATABASE.dbUser())
            .password(DEFAULT_DATABASE.dbPassword())
            .build());
        pool = new PoolBackedPostgresConnectionFactory(RowLevelSecurity.DISABLED, POOL_SIZE, POOL_SIZE, connectionFactory);
        postgresExecutor = new PostgresExecutor.Factory(pool, postgresConfiguration, new RecordingMetricFactory()).create();
    }

    @AfterEach
    void tearDown() throws Exception {
        pool.close().block();
        container.execInContainer("psql", "-U", DEFAULT_DATABASE.dbUser(), "-c", "DROP DATABASE IF EXISTS " + databaseName + " WITH (FORCE);");
    }

    @Test
    void everyPooledConnectionShouldDecodeHstoreWhenInitializingAFreshDatabase() {
        new PostgresTableManager(postgresExecutor, connectionFactory, MODULE, postgresConfiguration)
            .initPostgres();

        Map<String, String> entries = Map.of("alice@domain.tld", "lr", "bob@domain.tld", "aeiklprstwx");
        postgresExecutor.executeVoid(dsl -> Mono.from(dsl.insertInto(TABLE, HSTORE_COLUMN)
                .values(Hstore.hstore(entries))))
            .block();

        assertThat(readHstoreOnEveryPooledConnection())
            .hasSize(POOL_SIZE)
            .allSatisfy(read -> assertThat(read).isEqualTo(entries));
    }

    private List<Map<?, ?>> readHstoreOnEveryPooledConnection() {
        return Flux.range(0, POOL_SIZE)
            .flatMap(any -> pool.getConnection())
            .collectList()
            .flatMapMany(connections -> Flux.fromIterable(connections)
                .concatMap(this::readHstore)
                .concatWith(Flux.fromIterable(connections)
                    .concatMap(connection -> pool.closeConnection(connection))
                    .then(Mono.empty())))
            .collectList()
            .block();
    }

    private Mono<Map<?, ?>> readHstore(Connection connection) {
        // An untyped select, as the DAOs do (selectFrom(TABLE_NAME) then record.get(field, LinkedHashMap.class))
        return Mono.from(DSL.using(connection, SQLDialect.POSTGRES).selectFrom(TABLE))
            .map(record -> record.get(HSTORE_COLUMN, LinkedHashMap.class));
    }
}
