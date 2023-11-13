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
package org.apache.james.modules.queue.rabbitmq;

import static org.apache.james.modules.queue.rabbitmq.RabbitMQModule.RABBITMQ_CONFIGURATION_NAME;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.rabbitmq.RabbitMQMailQueue;
import org.apache.james.queue.rabbitmq.RabbitMQMailQueueConsumerHealthCheck;
import org.apache.james.queue.rabbitmq.RabbitMQMailQueueDeadLetterQueueHealthCheck;
import org.apache.james.queue.rabbitmq.RabbitMQMailQueueFactory;
import org.apache.james.queue.rabbitmq.view.RabbitMQMailQueueConfiguration;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;

public class RabbitMQMailQueueModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder<SimpleConnectionPool.ReconnectionHandler> reconnectionHandlerMultibinder = Multibinder.newSetBinder(binder(), SimpleConnectionPool.ReconnectionHandler.class);
        reconnectionHandlerMultibinder.addBinding().to(SpoolerReconnectionHandler.class);
        Multibinder<HealthCheck> healthCheckMultiBinder = Multibinder.newSetBinder(binder(), HealthCheck.class);
        healthCheckMultiBinder.addBinding().to(RabbitMQMailQueueDeadLetterQueueHealthCheck.class);

        Multibinder.newSetBinder(binder(), HealthCheck.class).addBinding()
            .to(RabbitMQMailQueueConsumerHealthCheck.class);
    }

    @Provides
    @Singleton
    public MailQueueFactory<RabbitMQMailQueue> provideRabbitMQMailQueueFactoryProxy(RabbitMQMailQueueFactory queueFactory) {
        return queueFactory;
    }

    @Provides
    @Singleton
    public MailQueueFactory<? extends ManageableMailQueue> provideRabbitMQManageableMailQueueFactory(MailQueueFactory<RabbitMQMailQueue> queueFactory) {
        return queueFactory;
    }

    @Provides
    @Singleton
    public MailQueueFactory<?> provideRabbitMQMailQueueFactory(MailQueueFactory<RabbitMQMailQueue> queueFactory) {
        return queueFactory;
    }

    @Provides
    @Singleton
    public MailQueueFactory<? extends MailQueue> provideMailQueueFactoryGenerics(MailQueueFactory<RabbitMQMailQueue> queueFactory) {
        return queueFactory;
    }

    @Provides
    @Singleton
    private RabbitMQMailQueueConfiguration getMailQueueSizeConfiguration(@Named(RABBITMQ_CONFIGURATION_NAME) org.apache.commons.configuration2.Configuration configuration) {
        return RabbitMQMailQueueConfiguration.from(configuration);
    }
}
