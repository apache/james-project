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
package org.apache.james.task.eventsourcing.distributed;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.AcknowledgableDelivery;
import reactor.rabbitmq.ConsumeOptions;
import reactor.rabbitmq.RabbitFluxException;
import reactor.rabbitmq.ReceiverOptions;

/**
 * Taken from {@link reactor.rabbitmq.Receiver}
 * In order to be able to set the `exclusive` parameter to `true`
 * to the `channel.basicConsume` method.
 *
 * @deprecated to remove once the parallel execution of task has been implemented
 */
@Deprecated
public class RabbitMQExclusiveConsumer implements Closeable {
    private static final Function<Connection, Channel> CHANNEL_CREATION_FUNCTION = new RabbitMQExclusiveConsumer.ChannelCreationFunction();
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQExclusiveConsumer.class);

    private static final boolean NON_LOCAL = true;
    private static final boolean EXCLUSIVE = true;

    private static class ChannelCreationFunction implements Function<Connection, Channel> {

        @Override
        public Channel apply(Connection connection) {
            try {
                return connection.createChannel();
            } catch (IOException e) {
                throw new RabbitFluxException("Error while creating channel", e);
            }
        }
    }

    private Mono<? extends Connection> connectionMono;
    private final AtomicBoolean hasConnection;
    private final Scheduler connectionSubscriptionScheduler;
    private final boolean privateConnectionSubscriptionScheduler;

    public RabbitMQExclusiveConsumer(ReceiverOptions options) {
        this.privateConnectionSubscriptionScheduler = options.getConnectionSubscriptionScheduler() == null;
        this.connectionSubscriptionScheduler = options.getConnectionSubscriptionScheduler() == null ?
            createScheduler("rabbitmq-receiver-connection-subscription") : options.getConnectionSubscriptionScheduler();
        hasConnection = new AtomicBoolean(false);
        this.connectionMono = options.getConnectionMono() != null ? options.getConnectionMono() :
            Mono.fromCallable(() -> options.getConnectionFactory().newConnection())
                .doOnSubscribe(c -> hasConnection.set(true))
                .subscribeOn(this.connectionSubscriptionScheduler)
                .cache();
    }

    protected Scheduler createScheduler(String name) {
        return Schedulers.newElastic(name);
    }


    public Flux<AcknowledgableDelivery> consumeExclusiveManualAck(final String queue, ConsumeOptions options) {
        // TODO track flux so it can be disposed when the sender is closed?
        // could be also developer responsibility
        return Flux.create(emitter -> connectionMono.map(CHANNEL_CREATION_FUNCTION).subscribe(channel -> {
            try {
                if (options.getQos() != 0) {
                    channel.basicQos(options.getQos());
                }

                DeliverCallback deliverCallback = (consumerTag, message) -> {
                    AcknowledgableDelivery delivery = new AcknowledgableDelivery(message, channel, options.getExceptionHandler());
                    if (options.getHookBeforeEmitBiFunction().apply(emitter.requestedFromDownstream(), delivery)) {
                        emitter.next(delivery);
                    }
                    if (options.getStopConsumingBiFunction().apply(emitter.requestedFromDownstream(), message)) {
                        emitter.complete();
                    }
                };

                AtomicBoolean basicCancel = new AtomicBoolean(true);
                CancelCallback cancelCallback = consumerTag -> {
                    LOGGER.info("Flux consumer {} has been cancelled", consumerTag);
                    basicCancel.set(false);
                    emitter.complete();
                };

                completeOnChannelShutdown(channel, emitter);

                Map<String, Object> arguments = ImmutableMap.of();
                final String consumerTag = channel.basicConsume(queue, false, UUID.randomUUID().toString(), !NON_LOCAL, EXCLUSIVE, arguments, deliverCallback, cancelCallback);
                AtomicBoolean cancelled = new AtomicBoolean(false);
                LOGGER.info("Consumer {} consuming from {} has been registered", consumerTag, queue);
                emitter.onDispose(() -> {
                    LOGGER.info("Cancelling consumer {} consuming from {}", consumerTag, queue);
                    if (cancelled.compareAndSet(false, true)) {
                        try {
                            if (channel.isOpen() && channel.getConnection().isOpen()) {
                                if (basicCancel.compareAndSet(true, false)) {
                                    channel.basicCancel(consumerTag);
                                }
                                channel.close();
                            }
                        } catch (TimeoutException | IOException e) {
                            // Not sure what to do, not much we can do,
                            // logging should be enough.
                            // Maybe one good reason to introduce an exception handler to choose more easily.
                            LOGGER.warn("Error while closing channel: " + e.getMessage());
                        }
                    }
                });
            } catch (IOException e) {
                throw new RabbitFluxException(e);
            }
        }, emitter::error), options.getOverflowStrategy());
    }

    protected void completeOnChannelShutdown(Channel channel, FluxSink<?> emitter) {
        channel.addShutdownListener(reason -> {
            if (!AutorecoveringConnection.DEFAULT_CONNECTION_RECOVERY_TRIGGERING_CONDITION.test(reason)) {
                emitter.complete();
            }
        });
    }

    public void close() {
        if (hasConnection.getAndSet(false)) {
            try {
                // FIXME use timeout on block (should be a parameter of the Receiver)
                connectionMono.block().close();
            } catch (IOException e) {
                throw new RabbitFluxException(e);
            }
        }
        if (privateConnectionSubscriptionScheduler) {
            this.connectionSubscriptionScheduler.dispose();
        }
    }
}
