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

package org.apache.james.modules.event;

import static org.apache.james.events.NamingStrategy.MAILBOX_EVENT_NAMING_STRATEGY;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.event.json.MailboxEventSerializer;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventBusId;
import org.apache.james.events.EventBusReconnectionHandler;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.KeyReconnectionHandler;
import org.apache.james.events.NamingStrategy;
import org.apache.james.events.RabbitEventBusConsumerHealthCheck;
import org.apache.james.events.RabbitMQEventBus;
import org.apache.james.events.RabbitMQMailboxEventBusDeadLetterQueueHealthCheck;
import org.apache.james.events.RegistrationKey;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.RoutingKeyConverter;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

import reactor.rabbitmq.Sender;

public class RabbitMQEventBusModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(NamingStrategy.class).toInstance(MAILBOX_EVENT_NAMING_STRATEGY);

        Multibinder.newSetBinder(binder(), RegistrationKey.Factory.class)
            .addBinding().to(MailboxIdRegistrationKey.Factory.class);

        bind(RetryBackoffConfiguration.class).toInstance(RetryBackoffConfiguration.DEFAULT);
        bind(EventBusId.class).toInstance(EventBusId.random());

        Multibinder<SimpleConnectionPool.ReconnectionHandler> reconnectionHandlerMultibinder = Multibinder.newSetBinder(binder(), SimpleConnectionPool.ReconnectionHandler.class);
        reconnectionHandlerMultibinder.addBinding().to(KeyReconnectionHandler.class);
        reconnectionHandlerMultibinder.addBinding().to(EventBusReconnectionHandler.class);

        Multibinder.newSetBinder(binder(), HealthCheck.class)
            .addBinding().to(RabbitMQMailboxEventBusDeadLetterQueueHealthCheck.class);
    }

    @ProvidesIntoSet
    HealthCheck healthCheck(RabbitMQEventBus eventBus, NamingStrategy namingStrategy,
                            SimpleConnectionPool connectionPool) {
        return new RabbitEventBusConsumerHealthCheck(eventBus, namingStrategy, connectionPool);
    }

    @ProvidesIntoSet
    InitializationOperation workQueue(RabbitMQEventBus instance) {
        return InitilizationOperationBuilder
            .forClass(RabbitMQEventBus.class)
            .init(instance::start);
    }

    @Provides
    @Singleton
    RabbitMQEventBus provideRabbitMQEventBus(NamingStrategy namingStrategy, Sender sender, ReceiverProvider receiverProvider, MailboxEventSerializer eventSerializer,
                                         RetryBackoffConfiguration retryBackoff,
                                         RoutingKeyConverter routingKeyConverter,
                                         EventDeadLetters eventDeadLetters, MetricFactory metricFactory, ReactorRabbitMQChannelPool channelPool,
                                         EventBusId eventBusId, RabbitMQConfiguration configuration) {
        return new RabbitMQEventBus(namingStrategy, sender, receiverProvider, eventSerializer, retryBackoff, routingKeyConverter,
            eventDeadLetters, metricFactory, channelPool, eventBusId, configuration);
    }

    @Provides
    @Singleton
    EventBus provideEventBus(RabbitMQEventBus rabbitMQEventBus) {
        return rabbitMQEventBus;
    }

    @ProvidesIntoSet
    EventBus registerEventBus(EventBus eventBus) {
        return eventBus;
    }
}
