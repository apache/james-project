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

import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.event.json.MailboxEventSerializer;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventBusId;
import org.apache.james.events.EventSerializer;
import org.apache.james.events.KeyReconnectionHandler;
import org.apache.james.events.MailboxIdRegistrationKey;
import org.apache.james.events.RabbitMQEventBus;
import org.apache.james.events.RegistrationKey;
import org.apache.james.events.RetryBackoffConfiguration;
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

        bind(RabbitMQEventBus.class).in(Scopes.SINGLETON);
        bind(EventBus.class).to(RabbitMQEventBus.class);

        Multibinder.newSetBinder(binder(), RegistrationKey.Factory.class)
            .addBinding().to(MailboxIdRegistrationKey.Factory.class);

        bind(RetryBackoffConfiguration.class).toInstance(RetryBackoffConfiguration.DEFAULT);
        bind(EventBusId.class).toInstance(EventBusId.random());

        Multibinder<SimpleConnectionPool.ReconnectionHandler> reconnectionHandlerMultibinder = Multibinder.newSetBinder(binder(), SimpleConnectionPool.ReconnectionHandler.class);
        reconnectionHandlerMultibinder.addBinding().to(KeyReconnectionHandler.class);
    }

    @ProvidesIntoSet
    InitializationOperation workQueue(RabbitMQEventBus instance) {
        return InitilizationOperationBuilder
            .forClass(RabbitMQEventBus.class)
            .init(instance::start);
    }
}
