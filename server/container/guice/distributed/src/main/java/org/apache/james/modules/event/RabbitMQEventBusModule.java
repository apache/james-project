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

import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.event.json.MailboxEventSerializer;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventBusId;
import org.apache.james.events.EventBusReconnectionHandler;
import org.apache.james.events.EventSerializer;
import org.apache.james.events.KeyReconnectionHandler;
import org.apache.james.events.NamingStrategy;
import org.apache.james.events.RabbitEventBusConsumerHealthCheck;
import org.apache.james.events.RabbitMQEventBus;
import org.apache.james.events.RabbitMQEventBusDeadLetterQueueHealthCheck;
import org.apache.james.events.RegistrationKey;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class RabbitMQEventBusModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(MailboxEventSerializer.class).in(Scopes.SINGLETON);
        bind(EventSerializer.class).to(MailboxEventSerializer.class);

        bind(NamingStrategy.class).toInstance(MAILBOX_EVENT_NAMING_STRATEGY);
        bind(RabbitMQEventBus.class).in(Scopes.SINGLETON);
        bind(EventBus.class).to(RabbitMQEventBus.class);

        Multibinder.newSetBinder(binder(), EventBus.class)
            .addBinding()
            .to(EventBus.class);

        Multibinder.newSetBinder(binder(), RegistrationKey.Factory.class)
            .addBinding().to(MailboxIdRegistrationKey.Factory.class);

        bind(RetryBackoffConfiguration.class).toInstance(RetryBackoffConfiguration.DEFAULT);
        bind(EventBusId.class).toInstance(EventBusId.random());

        Multibinder<SimpleConnectionPool.ReconnectionHandler> reconnectionHandlerMultibinder = Multibinder.newSetBinder(binder(), SimpleConnectionPool.ReconnectionHandler.class);
        reconnectionHandlerMultibinder.addBinding().to(KeyReconnectionHandler.class);
        reconnectionHandlerMultibinder.addBinding().to(EventBusReconnectionHandler.class);

        Multibinder.newSetBinder(binder(), HealthCheck.class)
            .addBinding().to(RabbitMQEventBusDeadLetterQueueHealthCheck.class);
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
}
