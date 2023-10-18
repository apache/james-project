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

package org.apache.james.mailbox.cassandra.user;

import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;
import static org.apache.james.mailbox.cassandra.user.PostgresConnectionResolver.PostgresConnectionPoolResolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.store.user.model.Subscription;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

//@Testcontainers
public class PostgresConnectionPoolTest {

    static final String DB_NAME = "james-db";
    static final String DB_USER = "james";
    static final String DB_PASSWORD = "secret";
    @Container
    private static final GenericContainer<?> container = new PostgreSQLContainer("postgres:11.1")
        .withDatabaseName(DB_NAME)
        .withUsername(DB_USER)
        .withPassword(DB_PASSWORD)
        .withInitScript("postgres-subscription-init.sql");

    @BeforeAll
    static void beforeAll() {
        // System.out.println("Postgres port: " + container.getMappedPort(5432));
    }

    private static ConnectionFactory getConnectionFactory() {
        return ConnectionFactories.get(ConnectionFactoryOptions.builder()
            .option(DRIVER, "postgresql")
            .option(HOST, "localhost")
            //.option(PORT, container.getMappedPort(5432))  // from testContainer
            .option(PORT, 5432)  // from local database
            .option(USER, DB_USER)
            .option(PASSWORD, DB_PASSWORD)
            .option(DATABASE, DB_NAME)
            .build());
    }

    private MailboxSession getMockSession(Username username) {
        MailboxSession mailboxSession = mock(MailboxSession.class);
        when(mailboxSession.getUser()).thenReturn(username);
        return mailboxSession;
    }

    @Test
    void poolShouldCreateCorrectNumberConnection() throws Exception {
        int poolMaxSize = 15;

        // given pool with max size 15
        ConnectionPoolConfiguration poolConfiguration = ConnectionPoolConfiguration.builder(getConnectionFactory())
            .name("Pool Test1")
            .maxSize(poolMaxSize)
            .build();

        ConnectionPool connectionPool = new ConnectionPool(poolConfiguration);

        // when try to create 100 connection
        Flux.range(0, 100)
            .flatMap(i ->
                connectionPool.create()
                    .flatMapMany(connection -> Flux.from(connection.createStatement("SET SESSION app.attribute TO '" + UUID.randomUUID() + "'").execute())))
            .last()
            .subscribe();

        Thread.sleep(2000);

        // Then database should have 15 connection in the pool
        Integer dbActiveConnectionNumber = Mono.from(getConnectionFactory().create())
            .flatMap(connection -> Mono.from(connection.createStatement("SELECT count(*) from pg_stat_activity where usename ='james';").execute()))
            .flatMap(result -> Mono.from(result.map((row, rowMetadata) -> row.get(0, Integer.class))))
            .block();

        assertThat(dbActiveConnectionNumber).isEqualTo(poolMaxSize + 1);
    }

    @Test
    void saveSubscriptionShouldSuccessWhenNumberDomainGreaterThanConnectionPoolSize() throws Exception {
        // Given pool with max size 15
        int poolMaxSize = 15;
        ConnectionPoolConfiguration poolConfiguration = ConnectionPoolConfiguration.builder(getConnectionFactory())
            .name("Pool Test1")
            .maxSize(poolMaxSize)
            .build();

        // When try to save 65 subscription with 65 different domain
        PostgresConnectionPoolResolver postgresConnectionPoolResolver = new PostgresConnectionPoolResolver(poolConfiguration);
        PostgresSubscriptionMapperFactory postgresSubscriptionMapperFactory = new PostgresSubscriptionMapperFactory(postgresConnectionPoolResolver);

        Function<Username, Mono<String>> storageSubscriptionFunction = username -> {
            MailboxSession mailboxSession = getMockSession(username);
            return Mono.usingWhen(postgresSubscriptionMapperFactory.getSubscriptionMapperReactive(mailboxSession),
                    subscriptionMapper -> subscriptionMapper.saveReactive(new Subscription(username, "mailbox_" + username.asString())),
                    subscriptionMapper -> postgresSubscriptionMapperFactory.endProcessingRequest(mailboxSession))
                .thenReturn(username.asString());
        };

        int numberDomain = poolMaxSize + 50;

        Flux.range(0, numberDomain)
            .flatMap(i -> storageSubscriptionFunction.apply(Username.from("user_" + i, Optional.of("domain.tld" + i)))).last()
            .block();

        // Then the persist entry to database should success
        Integer distinctDomainCounter = Mono.from(getConnectionFactory().create())
            .flatMap(connection -> Mono.from(connection.createStatement("SELECT COUNT(DISTINCT domain) FROM subscription").execute()))
            .flatMap(result -> Mono.from(result.map((row, rowMetadata) -> row.get(0, Integer.class))))
            .block();
        assertThat(distinctDomainCounter).isEqualTo(numberDomain);
    }

    @Test
    void sameDomainInMultiRequestShouldSuccess() throws Exception {
        int poolMaxSize = 10;
        ConnectionPoolConfiguration poolConfiguration = ConnectionPoolConfiguration.builder(getConnectionFactory())
            .name("Pool Test1")
            .maxSize(poolMaxSize)
            .build();

        PostgresConnectionPoolResolver postgresConnectionPoolResolver = new PostgresConnectionPoolResolver(poolConfiguration);
        PostgresSubscriptionMapperFactory postgresSubscriptionMapperFactory = new PostgresSubscriptionMapperFactory(postgresConnectionPoolResolver);

        Function<Username, Mono<String>> storageSubscriptionFunction = username -> {
            MailboxSession mailboxSession = getMockSession(username);
            return Mono.usingWhen(postgresSubscriptionMapperFactory.getSubscriptionMapperReactive(mailboxSession),
                    subscriptionMapper -> subscriptionMapper.saveReactive(new Subscription(username, "mailbox_" + UUID.randomUUID())),
                    subscriptionMapper -> postgresSubscriptionMapperFactory.endProcessingRequest(mailboxSession))
                .thenReturn(username.asString());
        };

        List<Username> userPool = List.of(Username.of("user1@domain.tld1"),
            Username.of("user2@domain.tld1"),
            Username.of("user3@domain.tld1"),
            Username.of("user@domain.tld2"),
            Username.of("user@domain.tld3"),
            Username.of("user@domain.tld4"));

        Random random = new Random();

        int requestNumber = 100;
        Flux.range(0, requestNumber)
            .flatMap(i -> storageSubscriptionFunction.apply(userPool.get(random.nextInt(userPool.size())))).last()
            .block();

        // Then the persist entry to database should success
        Integer subscriptionNumber = Mono.from(getConnectionFactory().create())
            .flatMap(connection -> Mono.from(connection.createStatement("SELECT COUNT(*) FROM subscription").execute()))
            .flatMap(result -> Mono.from(result.map((row, rowMetadata) -> row.get(0, Integer.class))))
            .block();

        assertThat(subscriptionNumber).isEqualTo(requestNumber);
    }

}
