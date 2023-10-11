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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.core.Domain;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import reactor.core.publisher.Mono;

public interface PostgresConnectionResolver {

    Publisher<PostgresqlConnection> resolver(Domain domain);


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
        public Mono<PostgresqlConnection> resolver(Domain domain) {
            return Mono.justOrEmpty(domain)
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
        public Mono<PostgresqlConnection> resolver(Domain domain) {
            return singletonConnection;
        }
    }

}
