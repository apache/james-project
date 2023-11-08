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

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.backends.postgres.utils.JamesPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.SimpleJamesPostgresConnectionFactory;
import org.apache.james.core.Domain;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.spi.Connection;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SimpleJamesPostgresConnectionFactoryTest extends JamesPostgresConnectionFactoryTest {
    @RegisterExtension
    static PostgresExtension postgresExtension = new PostgresExtension();

    private PostgresqlConnection postgresqlConnection;
    private SimpleJamesPostgresConnectionFactory jamesPostgresConnectionFactory;

    JamesPostgresConnectionFactory jamesPostgresConnectionFactory() {
        return jamesPostgresConnectionFactory;
    }

    @BeforeEach
    void beforeEach() {
        jamesPostgresConnectionFactory = new SimpleJamesPostgresConnectionFactory(postgresExtension.getConnectionFactory());
        postgresqlConnection = (PostgresqlConnection) postgresExtension.getConnection().block();
    }

    @AfterEach
    void afterEach() throws URISyntaxException {
        postgresExtension.restartContainer();
    }

    @Test
    void factoryShouldCreateCorrectNumberOfConnections() {
        Integer previousDbActiveNumberOfConnections = getNumberOfConnections();

        // create 50 connections
        Flux.range(1, 50)
            .flatMap(i -> jamesPostgresConnectionFactory.getConnection(Domain.of("james" + i)))
            .last()
            .block();

        Integer dbActiveNumberOfConnections = getNumberOfConnections();

        assertThat(dbActiveNumberOfConnections - previousDbActiveNumberOfConnections).isEqualTo(50);
    }

    @Nullable
    private Integer getNumberOfConnections() {
        return Mono.from(postgresqlConnection.createStatement("SELECT count(*) from pg_stat_activity where usename = $1;")
            .bind("$1", PostgresFixture.Database.DB_USER)
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
    void factoryShouldCreateNewConnectionWhenDomainsAreDifferent() {
        Connection connectionOne = jamesPostgresConnectionFactory.getConnection(Domain.of("james")).block();
        Connection connectionTwo = jamesPostgresConnectionFactory.getConnection(Domain.of("lin")).block();

        String domainOne = getDomainAttributeValue(connectionOne);

        String domainTwo = Flux.from(connectionTwo.createStatement("show " + JamesPostgresConnectionFactory.DOMAIN_ATTRIBUTE)
                .execute())
            .flatMap(result -> result.map((row, rowMetadata) -> row.get(0, String.class)))
            .collect(ImmutableList.toImmutableList())
            .block().get(0);

        assertThat(connectionOne).isNotEqualTo(connectionTwo);
        assertThat(domainOne).isNotEqualTo(domainTwo);
    }

    @Test
    void factoryShouldNotCreateNewConnectionWhenDomainsAreTheSameAndRequestsAreFromDifferentThreads() throws Exception {
        Set<Connection> connectionSet = ConcurrentHashMap.newKeySet();

        ConcurrentTestRunner.builder()
            .reactorOperation((threadNumber, step) -> jamesPostgresConnectionFactory.getConnection(Domain.of("james"))
                .doOnNext(connectionSet::add)
                .then())
            .threadCount(50)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(connectionSet).hasSize(1);
    }

    @Test
    void factoryShouldCreateOnlyOneDefaultConnection() throws Exception {
        Set<Connection> connectionSet = ConcurrentHashMap.newKeySet();

        ConcurrentTestRunner.builder()
            .reactorOperation((threadNumber, step) -> jamesPostgresConnectionFactory.getConnection(Optional.empty())
                .doOnNext(connectionSet::add)
                .then())
            .threadCount(50)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(connectionSet).hasSize(1);
    }

}
