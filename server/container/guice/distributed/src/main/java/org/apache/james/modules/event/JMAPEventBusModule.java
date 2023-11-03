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

import static org.apache.james.events.NamingStrategy.JMAP_NAMING_STRATEGY;

import javax.inject.Named;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventBusId;
import org.apache.james.events.EventBusReconnectionHandler;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.KeyReconnectionHandler;
import org.apache.james.events.RabbitEventBusConsumerHealthCheck;
import org.apache.james.events.RabbitMQEventBus;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.RoutingKeyConverter;
import org.apache.james.jmap.InjectionKeys;
import org.apache.james.jmap.change.Factory;
import org.apache.james.jmap.change.JmapEventSerializer;
import org.apache.james.jmap.pushsubscription.PushListener;
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

public class JMAPEventBusModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(EventBusId.class).annotatedWith(Names.named(InjectionKeys.JMAP)).toInstance(EventBusId.random());
    }

    @ProvidesIntoSet
    InitializationOperation workQueue(@Named(InjectionKeys.JMAP) RabbitMQEventBus instance, PushListener pushListener) {
        return InitilizationOperationBuilder
            .forClass(RabbitMQEventBus.class)
            .init(() -> {
                instance.start();
                instance.register(pushListener);
            });
    }

    @ProvidesIntoSet
    SimpleConnectionPool.ReconnectionHandler provideReconnectionHandler(@Named(InjectionKeys.JMAP) RabbitMQEventBus eventBus) {
        return new EventBusReconnectionHandler(eventBus);
    }

    @ProvidesIntoSet
    SimpleConnectionPool.ReconnectionHandler provideReconnectionHandler(@Named(InjectionKeys.JMAP) EventBusId eventBusId, RabbitMQConfiguration configuration) {
        return new KeyReconnectionHandler(JMAP_NAMING_STRATEGY, eventBusId, configuration);
    }

    @ProvidesIntoSet
    HealthCheck healthCheck(@Named(InjectionKeys.JMAP) RabbitMQEventBus eventBus,
                            SimpleConnectionPool connectionPool) {
        return new RabbitEventBusConsumerHealthCheck(eventBus, JMAP_NAMING_STRATEGY, connectionPool);
    }

    @Provides
    @Singleton
    @Named(InjectionKeys.JMAP)
    RabbitMQEventBus provideJmapEventBus(Sender sender, ReceiverProvider receiverProvider,
                                         JmapEventSerializer eventSerializer,
                                         RetryBackoffConfiguration retryBackoffConfiguration,
                                         EventDeadLetters eventDeadLetters,
                                         MetricFactory metricFactory, ReactorRabbitMQChannelPool channelPool,
                                         @Named(InjectionKeys.JMAP) EventBusId eventBusId,
                                         RabbitMQConfiguration configuration) {
        return new RabbitMQEventBus(
            JMAP_NAMING_STRATEGY,
            sender, receiverProvider, eventSerializer, retryBackoffConfiguration, new RoutingKeyConverter(ImmutableSet.of(new Factory())),
            eventDeadLetters, metricFactory, channelPool, eventBusId, configuration);
    }

    @Provides
    @Singleton
    @Named(InjectionKeys.JMAP)
    EventBus provideJmapEventBus(@Named(InjectionKeys.JMAP) RabbitMQEventBus rabbitMQEventBus) {
        return rabbitMQEventBus;
    }

    @ProvidesIntoSet
    EventBus registerEventBus(@Named(InjectionKeys.JMAP) EventBus eventBus) {
        return eventBus;
    }
}
