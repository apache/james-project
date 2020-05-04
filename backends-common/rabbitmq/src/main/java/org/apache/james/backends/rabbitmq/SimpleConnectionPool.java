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

import static org.apache.james.util.ReactorUtils.transformAndPublishIfNotNull;

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
import reactor.util.retry.Retry;

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
        int numRetries = 10;
        Duration initialDelay = Duration.ofMillis(100);
        return Mono.defer(this::getOpenConnection)
            .retryWhen(Retry.backoff(numRetries, initialDelay).scheduler(Schedulers.elastic()));
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

    public Mono<Boolean> tryConnection() {
        return getOpenConnection()
            .timeout(Duration.ofSeconds(1))
            .hasElement()
            .onErrorResume(any -> Mono.just(false));
    }

    public Mono<RabbitMQServerVersion> version() {
        return getOpenConnection()
            .map(Connection::getServerProperties)
            .handle(transformAndPublishIfNotNull(serverProperties -> serverProperties.get("version")))
            .map(Object::toString)
            .map(RabbitMQServerVersion::of)
            .timeout(Duration.ofSeconds(1))
            .onErrorResume(any -> Mono.empty());
    }
}
