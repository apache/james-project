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

import org.apache.james.backends.postgres.RowLevelSecurity;
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
    private static final int DEFAULT_INITIAL_SIZE = 10;
    private static final int DEFAULT_MAX_SIZE = 20;

    private final RowLevelSecurity rowLevelSecurity;
    private final ConnectionPool pool;

    public PoolBackedPostgresConnectionFactory(RowLevelSecurity rowLevelSecurity, int initialSize, int maxSize, ConnectionFactory connectionFactory) {
        this.rowLevelSecurity = rowLevelSecurity;
        ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration.builder(connectionFactory)
            .initialSize(initialSize)
            .maxSize(maxSize)
            .build();
        LOGGER.info("Creating new postgres ConnectionPool with initialSize {} and maxSize {}", initialSize, maxSize);
        pool = new ConnectionPool(configuration);
    }

    public PoolBackedPostgresConnectionFactory(RowLevelSecurity rowLevelSecurity, ConnectionFactory connectionFactory) {
        this(rowLevelSecurity, DEFAULT_INITIAL_SIZE, DEFAULT_MAX_SIZE, connectionFactory);
    }

    @Override
    public Mono<Connection> getConnection(Domain domain) {
        if (rowLevelSecurity.isRowLevelSecurityEnabled()) {
            return pool.create().flatMap(connection -> setDomainAttributeForConnection(domain.asString(), connection));
        } else {
            return pool.create();
        }
    }

    @Override
    public Mono<Connection> getConnection() {
        return pool.create();
    }

    @Override
    public Mono<Void> closeConnection(Connection connection) {
        return Mono.from(connection.close());
    }

    @Override
    public Mono<Void> close() {
        return pool.close();
    }

    private Mono<Connection> setDomainAttributeForConnection(String domainAttribute, Connection connection) {
        return Mono.from(connection.createStatement("SET " + DOMAIN_ATTRIBUTE + " TO '" + domainAttribute + "'") // It should be set value via Bind, but it doesn't work
                .execute())
            .doOnError(e -> LOGGER.error("Error while setting domain attribute for domain {}", domainAttribute, e))
            .then(Mono.just(connection));
    }
}
