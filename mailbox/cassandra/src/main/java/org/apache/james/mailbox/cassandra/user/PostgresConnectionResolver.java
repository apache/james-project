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

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.apache.james.core.Domain;
import org.apache.james.mailbox.MailboxSession;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.spi.Connection;
import reactor.core.publisher.Mono;

public interface PostgresConnectionResolver {

    Publisher<? extends Connection> resolver(MailboxSession mailboxSession);

    default Publisher<Void> release(MailboxSession mailboxSession) {
        return Mono.empty();
    }


    /**
     * @deprecated use {@link PostgresConnectionPoolResolver}
     */
    @Deprecated
    class PostgresRLSConnectionResolver implements PostgresConnectionResolver {
        private static final Logger LOGGER = LoggerFactory.getLogger(PostgresRLSConnectionResolver.class);

        private final Map<Domain, PostgresqlConnection> dataSource = new ConcurrentHashMap<>();
        private final PostgresqlConnectionFactory connectionFactory;
        private final Mono<PostgresqlConnection> defaultConnection;

        public PostgresRLSConnectionResolver(PostgresqlConnectionFactory connectionFactory) {
            this.connectionFactory = connectionFactory;
            this.defaultConnection = connectionFactory.create();
        }

        @Override
        public Mono<? extends Connection> resolver(MailboxSession mailboxSession) {
            return Mono.justOrEmpty(mailboxSession.getUser().getDomainPart())
                .flatMap(domainValue -> Mono.fromCallable(() -> dataSource.get(domainValue))
                    .switchIfEmpty(create(domainValue)))
                .switchIfEmpty(defaultConnection);
        }

        private Mono<PostgresqlConnection> create(Domain domain) {
            return connectionFactory.create()
                .flatMap(connection -> connection.createStatement("SET app.current_domain TO '" + domain.asString() + "'") // It should be set value via Bind, but it doesn't work
                    .execute()
                    .then()
                    .doOnError(e -> LOGGER.error("Error while creating connection for domain {}", domain, e))
                    .then(Mono.just(connection))
                    .doOnSuccess(sessionConnection -> {
                        dataSource.put(domain, sessionConnection);
                        LOGGER.info("Connection created for domain {}", domain);
                    }));
        }
    }

    class PostgresConnectionResolverDefault implements PostgresConnectionResolver {
        private final Mono<PostgresqlConnection> singletonConnection;

        public PostgresConnectionResolverDefault(PostgresqlConnectionFactory connectionFactory) {
            this.singletonConnection = connectionFactory.create();
        }

        @Override
        public Mono<PostgresqlConnection> resolver(MailboxSession mailboxSession) {
            return singletonConnection;
        }
    }

    class PostgresConnectionPoolResolver implements PostgresConnectionResolver, Closeable {
        private static final Logger LOGGER = LoggerFactory.getLogger(PostgresConnectionPoolResolver.class);

        private final ConnectionPool connectionPool;

        private final Map<MailboxSession.SessionId, Connection> mapConnectionLookup;

        public PostgresConnectionPoolResolver(ConnectionPoolConfiguration poolConfiguration) {
            this.connectionPool = new ConnectionPool(poolConfiguration);

            this.mapConnectionLookup = new HashMap<>();
        }

        @Override
        public Mono<? extends Connection> resolver(MailboxSession mailboxSession) {
            return Mono.fromCallable(() -> mapConnectionLookup.get(mailboxSession.getSessionId()))
                .switchIfEmpty(createPooledConnection(mailboxSession.getUser().getDomainPart().get())
                    .doOnSuccess(connection -> mapConnectionLookup.put(mailboxSession.getSessionId(), connection)));
        }

        @Override
        public Mono<Void> release(MailboxSession mailboxSession) {
            return Mono.fromCallable(() -> mapConnectionLookup.get(mailboxSession.getSessionId()))
                .flatMap(release());
        }

        private Mono<Connection> createPooledConnection(Domain domain) {
            return connectionPool.create()
                .doOnNext(e -> System.out.println("connection has been created, " + e.hashCode()))
                .flatMap(pooledConnection ->
                    Mono.from(pooledConnection.createStatement("SET app.current_domain TO '" + domain.asString() + "'")
                            .execute())
                        .thenReturn(pooledConnection));
        }

        private static Function<Connection, Mono<Void>> release() {
            return connection -> Mono.from(connection.createStatement("RESET app.current_domain").execute())
                .doOnSuccess(e -> {
                    LOGGER.debug("connection has been released, {}", e.hashCode());
                })
                .then(Mono.from(connection.close()));
        }

        @Override
        public void close() throws IOException {
            connectionPool.dispose();
        }
    }

}
