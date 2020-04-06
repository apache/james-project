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

package org.apache.james.backends.rabbitmq;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class SimpleConnectionPool implements AutoCloseable {
    private final AtomicReference<Connection> connectionReference;
    private final RabbitMQConnectionFactory connectionFactory;

    @Inject
    @VisibleForTesting
    public SimpleConnectionPool(RabbitMQConnectionFactory factory) {
        this.connectionFactory = factory;
        this.connectionReference = new AtomicReference<>();
    }

    @PreDestroy
    @Override
    public void close() {
        Optional.ofNullable(connectionReference.get())
            .filter(Connection::isOpen)
            .ifPresent(Throwing.<Connection>consumer(Connection::close).orDoNothing());
    }

    public Mono<Connection> getResilientConnection() {
        int numRetries = 100;
        Duration initialDelay = Duration.ofMillis(100);
        Duration forever = Duration.ofMillis(Long.MAX_VALUE);
        return Mono.defer(this::getOpenConnection)
            .retryBackoff(numRetries, initialDelay, forever, Schedulers.elastic());
    }

    private Mono<Connection> getOpenConnection() {
        Connection previous = connectionReference.get();
        Connection current = Optional.ofNullable(previous)
            .filter(Connection::isOpen)
            .orElseGet(connectionFactory::create);
        boolean updated = connectionReference.compareAndSet(previous, current);
        if (updated) {
            return Mono.just(current);
        } else {
            try {
                current.close();
            } catch (IOException e) {
                //error below
            }
            return Mono.error(new RuntimeException("unable to create and register a new Connection"));
        }
    }

    public boolean tryConnection() {
        try {
            return getOpenConnection()
                .blockOptional(Duration.ofSeconds(1))
                .isPresent();
        } catch (Throwable t) {
            return false;
        }
    }

    public Optional<RabbitMQServerVersion> version() {
        try {
            return getOpenConnection()
                .map(Connection::getServerProperties)
                .flatMap(serverProperties -> Mono.justOrEmpty(serverProperties.get("version")))
                .map(Object::toString)
                .map(RabbitMQServerVersion::of)
                .blockOptional(Duration.ofSeconds(1));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }
}
