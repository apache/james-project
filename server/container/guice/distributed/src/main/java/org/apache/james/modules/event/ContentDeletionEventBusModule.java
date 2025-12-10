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

import static org.apache.james.events.NamingStrategy.CONTENT_DELETION_NAMING_STRATEGY;

import jakarta.inject.Named;

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
import org.apache.james.events.GroupRegistrationHandler;
import org.apache.james.events.KeyReconnectionHandler;
import org.apache.james.events.RabbitEventBusConsumerHealthCheck;
import org.apache.james.events.RabbitMQContentDeletionEventBusDeadLetterQueueHealthCheck;
import org.apache.james.events.RabbitMQEventBus;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.RoutingKeyConverter;
import org.apache.james.jmap.change.Factory;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Names;

import reactor.rabbitmq.Sender;

public class ContentDeletionEventBusModule extends AbstractModule {
    public static final String CONTENT_DELETION = "contentDeletion";

    @Override
    protected void configure() {
        bind(EventBusId.class).annotatedWith(Names.named(CONTENT_DELETION)).toInstance(EventBusId.random());
    }

    @ProvidesIntoSet
    InitializationOperation workQueue(@Named(CONTENT_DELETION) RabbitMQEventBus instance) {
        return InitilizationOperationBuilder
            .forClass(RabbitMQEventBus.class)
            .init(instance::start);
    }

    @ProvidesIntoSet
    SimpleConnectionPool.ReconnectionHandler provideReconnectionHandler(@Named(CONTENT_DELETION) RabbitMQEventBus eventBus) {
        return new EventBusReconnectionHandler(eventBus);
    }

    @ProvidesIntoSet
    SimpleConnectionPool.ReconnectionHandler provideReconnectionHandler(@Named(CONTENT_DELETION) EventBusId eventBusId, RabbitMQConfiguration configuration) {
        return new KeyReconnectionHandler(CONTENT_DELETION_NAMING_STRATEGY, eventBusId, configuration);
    }

    @ProvidesIntoSet
    HealthCheck healthCheck(@Named(CONTENT_DELETION) RabbitMQEventBus eventBus,
                            SimpleConnectionPool connectionPool) {
        return new RabbitEventBusConsumerHealthCheck(eventBus, CONTENT_DELETION_NAMING_STRATEGY, connectionPool,
            GroupRegistrationHandler.GROUP);
    }

    @ProvidesIntoSet
    HealthCheck contentDeletionEventBusDeadLetterQueueHealthCheck(RabbitMQConfiguration rabbitMQConfiguration) {
        return new RabbitMQContentDeletionEventBusDeadLetterQueueHealthCheck(rabbitMQConfiguration);
    }

    @Provides
    @Singleton
    @Named(CONTENT_DELETION)
    RabbitMQEventBus provideContentDeletionEventBus(Sender sender, ReceiverProvider receiverProvider,
                                                    MailboxEventSerializer eventSerializer,
                                                    RetryBackoffConfiguration retryBackoffConfiguration,
                                                    EventDeadLetters eventDeadLetters,
                                                    MetricFactory metricFactory, ReactorRabbitMQChannelPool channelPool,
                                                    @Named(CONTENT_DELETION) EventBusId eventBusId,
                                                    RabbitMQConfiguration configuration) {
        return new RabbitMQEventBus(
            CONTENT_DELETION_NAMING_STRATEGY,
            sender, receiverProvider, eventSerializer, retryBackoffConfiguration, new RoutingKeyConverter(ImmutableSet.of(new Factory())),
            eventDeadLetters, metricFactory, channelPool, eventBusId, configuration);
    }

    @Provides
    @Singleton
    @Named(CONTENT_DELETION)
    EventBus provideContentDeletionEventBus(@Named(CONTENT_DELETION) RabbitMQEventBus rabbitMQEventBus) {
        return rabbitMQEventBus;
    }

    @ProvidesIntoSet
    EventBus registerEventBus(@Named(CONTENT_DELETION) EventBus eventBus) {
        return eventBus;
    }
}
