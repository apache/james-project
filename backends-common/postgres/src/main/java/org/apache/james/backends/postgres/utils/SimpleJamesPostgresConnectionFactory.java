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

package org.apache.james.backends.postgres.utils;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.james.core.Domain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import reactor.core.publisher.Mono;

public class SimpleJamesPostgresConnectionFactory implements JamesPostgresConnectionFactory {
    private static final PostgresqlConnection EMPTY_CONNECTION = null;
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleJamesPostgresConnectionFactory.class);

    private final PostgresqlConnectionFactory connectionFactory;
    private final Map<Domain, PostgresqlConnection> mapDomainToConnection = new ConcurrentHashMap<>();
    private AtomicReference<PostgresqlConnection> defaultConnection = new AtomicReference<>();

    public SimpleJamesPostgresConnectionFactory(PostgresqlConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public Mono<PostgresqlConnection> getConnection(Optional<Domain> maybeDomain) {
        return maybeDomain.map(this::getConnectionForDomain)
            .orElse(getDefaultConnection());
    }

    private Mono<PostgresqlConnection> getConnectionForDomain(Domain domain) {
        return Mono.just(domain)
            .flatMap(domainValue -> Mono.fromCallable(() -> mapDomainToConnection.get(domainValue))
                .switchIfEmpty(create(domainValue)));
    }

    private Mono<PostgresqlConnection> getDefaultConnection() {
        return Mono.justOrEmpty(defaultConnection.get())
            .switchIfEmpty(createDefault());
    }

    private Mono<PostgresqlConnection> createDefault() {
        return connectionFactory.create()
            .doOnError(e -> LOGGER.error("Error while creating default connection", e))
            .map(this::getAndSetDefaultConnection);
    }

    private PostgresqlConnection getAndSetDefaultConnection(PostgresqlConnection postgresqlConnection) {
        if (defaultConnection.compareAndSet(EMPTY_CONNECTION, postgresqlConnection)) {
            return postgresqlConnection;
        } else {
            postgresqlConnection.close()
                .doOnError(e -> LOGGER.error("Error while closing connection", e))
                .subscribe();
            return defaultConnection.get();
        }
    }

    private Mono<PostgresqlConnection> create(Domain domain) {
        return connectionFactory.create()
            .doOnError(e -> LOGGER.error("Error while creating connection for domain {}", domain, e))
            .flatMap(newConnection -> getAndSetConnection(domain, newConnection));
    }

    private Mono<PostgresqlConnection> getAndSetConnection(Domain domain, PostgresqlConnection newConnection) {
        return Mono.justOrEmpty(mapDomainToConnection.putIfAbsent(domain, newConnection))
            .map(postgresqlConnection -> {
                newConnection.close()
                    .doOnError(e -> LOGGER.error("Error while closing connection for domain {}", domain, e))
                    .subscribe();
                return postgresqlConnection;
            }).switchIfEmpty(newConnection.createStatement("SET " + DOMAIN_ATTRIBUTE + " TO '" + domain.asString() + "'") // It should be set value via Bind, but it doesn't work
                .execute()
                .doOnError(e -> LOGGER.error("Error while setting domain attribute for domain {}", domain, e))
                .then(Mono.just(newConnection)));
    }
}
