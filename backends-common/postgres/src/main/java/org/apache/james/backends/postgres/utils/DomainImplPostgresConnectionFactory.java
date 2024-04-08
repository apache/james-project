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

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DomainImplPostgresConnectionFactory implements JamesPostgresConnectionFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(DomainImplPostgresConnectionFactory.class);
    private static final Domain DEFAULT = Domain.of("default");
    private static final String DEFAULT_DOMAIN_ATTRIBUTE_VALUE = "";

    private final ConnectionFactory connectionFactory;
    private final Map<Domain, Connection> mapDomainToConnection = new ConcurrentHashMap<>();

    @Inject
    public DomainImplPostgresConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Mono<Connection> getConnection(Optional<Domain> maybeDomain) {
        return maybeDomain.map(this::getConnectionForDomain)
            .orElse(getConnectionForDomain(DEFAULT));
    }

    @Override
    public Mono<Void> closeConnection(Connection connection) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> close() {
        return Flux.fromIterable(mapDomainToConnection.values())
            .flatMap(connection -> Mono.from(connection.close()))
            .then();
    }

    private Mono<Connection> getConnectionForDomain(Domain domain) {
        return Mono.just(domain)
            .flatMap(domainValue -> Mono.fromCallable(() -> mapDomainToConnection.get(domainValue))
                .switchIfEmpty(create(domainValue)));
    }

    private Mono<Connection> create(Domain domain) {
        return Mono.from(connectionFactory.create())
            .doOnError(e -> LOGGER.error("Error while creating connection for domain {}", domain, e))
            .flatMap(newConnection -> getAndSetConnection(domain, newConnection));
    }

    private Mono<Connection> getAndSetConnection(Domain domain, Connection newConnection) {
        return Mono.fromCallable(() -> mapDomainToConnection.putIfAbsent(domain, newConnection))
            .map(postgresqlConnection -> {
                //close redundant connection
                Mono.from(newConnection.close())
                    .doOnError(e -> LOGGER.error("Error while closing connection for domain {}", domain, e))
                    .subscribe();
                return postgresqlConnection;
            }).switchIfEmpty(setDomainAttributeForConnection(domain, newConnection));
    }

    private Mono<Connection> setDomainAttributeForConnection(Domain domain, Connection newConnection) {
        return Mono.from(newConnection.createStatement("SET " + DOMAIN_ATTRIBUTE + " TO '" + getDomainAttributeValue(domain) + "'") // It should be set value via Bind, but it doesn't work
                .execute())
            .doOnError(e -> LOGGER.error("Error while setting domain attribute for domain {}", domain, e))
            .then(Mono.just(newConnection));
    }

    private String getDomainAttributeValue(Domain domain) {
        if (DEFAULT.equals(domain)) {
            return DEFAULT_DOMAIN_ATTRIBUTE_VALUE;
        } else {
            return domain.asString();
        }
    }
}
