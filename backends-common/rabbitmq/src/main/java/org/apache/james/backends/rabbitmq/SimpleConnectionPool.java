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

import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.rabbitmq.client.Connection;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

public class SimpleConnectionPool implements AutoCloseable {
    public static class Configuration {
        @FunctionalInterface
        public interface RequiresRetries {
            RequiresInitialDelay retries(int retries);
        }

        @FunctionalInterface
        public interface RequiresInitialDelay {
            Configuration initialDelay(Duration minBorrowDelay);
        }

        public static final Configuration DEFAULT = builder()
                .retries(10)
                .initialDelay(Duration.ofMillis(100));

        public static RequiresRetries builder() {
            return retries -> initialDelay -> new Configuration(retries, initialDelay);
        }

        public static Configuration from(org.apache.commons.configuration2.Configuration configuration) {
            return builder()
                .retries(configuration.getInt("connection.pool.retries", 10))
                .initialDelay(Duration.ofMillis(configuration.getLong("connection.pool.min.delay.ms", 100)));
        }

        private final int numRetries;
        private final Duration initialDelay;

        public Configuration(int numRetries, Duration initialDelay) {
            this.numRetries = numRetries;
            this.initialDelay = initialDelay;
        }

        public int getNumRetries() {
            return numRetries;
        }

        public Duration getInitialDelay() {
            return initialDelay;
        }
    }

    public interface ReconnectionHandler {
        Publisher<Void> handleReconnection(Connection connection);
    }

    private final AtomicReference<Connection> connectionReference;
    private final RabbitMQConnectionFactory connectionFactory;
    private final Set<ReconnectionHandler> reconnectionHandlers;
    private final Configuration configuration;

    @Inject
    @VisibleForTesting
    public SimpleConnectionPool(RabbitMQConnectionFactory factory, Set<ReconnectionHandler> reconnectionHandlers, Configuration configuration) {
        this.connectionFactory = factory;
        this.reconnectionHandlers = reconnectionHandlers;
        this.configuration = configuration;
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
        return Mono.defer(this::getOpenConnection)
            .retryWhen(Retry.backoff(configuration.getNumRetries(), configuration.getInitialDelay()).scheduler(Schedulers.elastic()));
    }

    private Mono<Connection> getOpenConnection() {
        Connection previous = connectionReference.get();
        Connection current = Optional.ofNullable(previous)
            .filter(Connection::isOpen)
            .orElseGet(connectionFactory::create);
        boolean updated = connectionReference.compareAndSet(previous, current);
        if (updated) {
            if (previous != null && previous != current) {
                return Flux.fromIterable(reconnectionHandlers)
                    .concatMap(handler -> handler.handleReconnection(current))
                    .then()
                    .thenReturn(current);
            }
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
            .map(serverProperties -> Optional.ofNullable(serverProperties.get("version")))
            .handle(publishIfPresent())
            .map(Object::toString)
            .map(RabbitMQServerVersion::of)
            .timeout(Duration.ofSeconds(1))
            .onErrorResume(any -> Mono.empty());
    }
}
