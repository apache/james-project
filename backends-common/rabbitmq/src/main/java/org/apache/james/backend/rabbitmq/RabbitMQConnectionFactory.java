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
package org.apache.james.backend.rabbitmq;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class RabbitMQConnectionFactory {
    private class ConnectionCallable implements Callable<Connection> {
        private final ConnectionFactory connectionFactory;
        private Optional<Connection> connection;

        ConnectionCallable(ConnectionFactory connectionFactory) {
            this.connectionFactory = connectionFactory;
            connection = Optional.empty();
        }

        @Override
        public Connection call() throws Exception {
            if (connection.map(Connection::isOpen).orElse(false)) {
                return connection.get();
            }
            Connection newConnection = connectionFactory.newConnection();
            connection = Optional.of(newConnection);
            return newConnection;
        }
    }

    private final ConnectionFactory connectionFactory;

    private final RabbitMQConfiguration configuration;

    @Inject
    public RabbitMQConnectionFactory(RabbitMQConfiguration rabbitMQConfiguration) {
        this.connectionFactory = from(rabbitMQConfiguration);
        this.configuration = rabbitMQConfiguration;
    }

    private ConnectionFactory from(RabbitMQConfiguration rabbitMQConfiguration) {
        try {
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setUri(rabbitMQConfiguration.getUri());
            return connectionFactory;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Connection create() {
        return connectionMono().block();
    }

    public Mono<Connection> connectionMono() {
        return Mono.fromCallable(new ConnectionCallable(connectionFactory))
            .retryBackoff(configuration.getMaxRetries(), Duration.ofMillis(configuration.getMinDelayInMs()))
            .publishOn(Schedulers.elastic());
    }
}
