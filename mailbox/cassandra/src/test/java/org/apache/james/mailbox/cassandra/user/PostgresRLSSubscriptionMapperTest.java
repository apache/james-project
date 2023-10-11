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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.user.PostgresConnectionResolver.PostgresRLSConnectionResolver;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.SubscriptionMapperTest;
import org.apache.james.mailbox.store.user.model.Subscription;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Testcontainers
public class PostgresRLSSubscriptionMapperTest extends SubscriptionMapperTest {
    static final String DB_NAME = "rlsdb";
    static final String SUPER_DB_USER = "james";
    static final String SUPER_USER_PASSWORD = "secret";
    @Container
    private static final GenericContainer<?> container = new PostgreSQLContainer("postgres:11.1")
        .withUsername(SUPER_DB_USER)
        .withPassword(SUPER_USER_PASSWORD)
        .withInitScript("postgres-rls-subscription-init.sql");

    static PostgresqlConnectionFactory postgresqlConnectionFactory;

    @BeforeAll
    static void beforeAll() {
        System.out.println("Postgres port: " + container.getMappedPort(5432));

        /**
         * The user "james" is superuser, by default it can bypass RLS.
         * => we need another user to test RLS, here is "rlsuser"
         */
        postgresqlConnectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
            .host(container.getHost())
            .port(container.getMappedPort(5432))
            .username("rlsuser")
            .password("secret")
            .database(DB_NAME)
            .build());

        Flux.usingWhen(
                postgresqlConnectionFactory.create(),
                connection -> Mono
                    .from(connection.createStatement("CREATE TABLE IF NOT EXISTS subscription " +
                        "( " +
                        "    username varchar(255) not null, " +
                        "    mailbox  varchar(500) not null, " +
                        "    domain varchar(255) not null DEFAULT current_setting('app.current_domain')::text, " +
                        "    constraint usenrame_mailbox_pk unique (username, mailbox) " +
                        ");" +
                        "ALTER TABLE subscription ENABLE ROW LEVEL SECURITY;" +
                        "ALTER TABLE subscription FORCE ROW LEVEL SECURITY;").execute())
                    .thenMany(connection.createStatement("CREATE POLICY domain_subscription_policy ON subscription " +
                        "    USING (domain = current_setting('app.current_domain')::text);").execute())
                    .map(Result::getRowsUpdated),
                Connection::close)
            .blockFirst();
    }

    @BeforeEach
    void beforeEach() {
        // clean data with superuser
        Flux.usingWhen(
                new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                    .host(container.getHost())
                    .port(container.getMappedPort(5432))
                    .username(SUPER_DB_USER)
                    .password(SUPER_USER_PASSWORD)
                    .database(DB_NAME)
                    .build()).create(),
                connection -> Mono
                    .from(connection.createStatement("DELETE FROM subscription ").execute())
                    .map(Result::getRowsUpdated),
                Connection::close)
            .blockFirst();
    }

    protected SubscriptionMapper createSubscriptionMapper() {
        return new PostgresSubscriptionMapper(new PostgresRLSConnectionResolver(postgresqlConnectionFactory));
    }

    @Test
    void rlsShouldWork() throws SubscriptionException {
        // Given: insert to subscriptions
        /* | username        | mailbox  | domain     |
           | user1@domain1   | mailbox1 | domain1    |
           | user1@domain1   | mailbox2 | domain2    | */

        Flux.usingWhen(
                postgresqlConnectionFactory.create(),
                connection -> Mono.from(connection.createStatement("SET SESSION app.current_domain TO 'domain1.tld'; " +
                        "INSERT INTO subscription VALUES ('user1@domain1.tld', 'mailbox1', 'domain1.tld')").execute())
                    .thenMany(connection.createStatement("SET SESSION app.current_domain TO 'domain2.tld';" +
                        "INSERT INTO subscription VALUES ('user1@domain1.tld', 'mailbox2', 'domain2.tld');").execute())
                    .map(Result::getRowsUpdated),
                Connection::close)
            .blockFirst();

        // When find subscriptions for user
        Username userWithDomain1 = Username.of("user1@domain1.tld");
        List<Subscription> results = testee.findSubscriptionsForUser(userWithDomain1);

        // Then: only return subscriptions with domain1
        assertThat(results).containsOnly(new Subscription(userWithDomain1, "mailbox1"))
            .doesNotContain(new Subscription(userWithDomain1, "mailbox2"));
    }
}
