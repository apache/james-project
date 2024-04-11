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

import java.time.Duration;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Mono;

public class PoolBackedPostgresConnectionFactory implements JamesPostgresConnectionFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(PoolBackedPostgresConnectionFactory.class);
    private static final Domain DEFAULT = Domain.of("default");
    private static final String DEFAULT_DOMAIN_ATTRIBUTE_VALUE = "";
    private static final int INITIAL_SIZE = 10;
    private static final int MAX_SIZE = 20;
    private static final Duration MAX_IDLE_TIME = Duration.ofMillis(5000);

    private final boolean rowLevelSecurityEnabled;
    private final ConnectionPool pool;

    public PoolBackedPostgresConnectionFactory(boolean rowLevelSecurityEnabled, Optional<Integer> maxSize, ConnectionFactory connectionFactory) {
        this.rowLevelSecurityEnabled = rowLevelSecurityEnabled;
        final ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration.builder(connectionFactory)
            .maxIdleTime(MAX_IDLE_TIME)
            .initialSize(INITIAL_SIZE)
            .maxSize(maxSize.orElse(MAX_SIZE))
            .build();
        pool = new ConnectionPool(configuration);
    }

    public PoolBackedPostgresConnectionFactory(boolean rowLevelSecurityEnabled, ConnectionFactory connectionFactory) {
        this(rowLevelSecurityEnabled, Optional.empty(), connectionFactory);
    }

    @Override
    public Mono<Connection> getConnection(Optional<Domain> domain) {
        if (rowLevelSecurityEnabled) {
            return pool.create().flatMap(connection -> setDomainAttributeForConnection(domain.orElse(DEFAULT), connection));
        } else {
            return pool.create();
        }
    }

    @Override
    public Mono<Void> closeConnection(Connection connection) {
        return Mono.from(connection.close());
    }

    @Override
    public Mono<Void> close() {
        return pool.close();
    }

    private Mono<Connection> setDomainAttributeForConnection(Domain domain, Connection connection) {
        return Mono.from(connection.createStatement("SET " + DOMAIN_ATTRIBUTE + " TO '" + getDomainAttributeValue(domain) + "'") // It should be set value via Bind, but it doesn't work
                .execute())
            .doOnError(e -> LOGGER.error("Error while setting domain attribute for domain {}", domain, e))
            .then(Mono.just(connection));
    }

    private String getDomainAttributeValue(Domain domain) {
        if (DEFAULT.equals(domain)) {
            return DEFAULT_DOMAIN_ATTRIBUTE_VALUE;
        } else {
            return domain.asString();
        }
    }
}
