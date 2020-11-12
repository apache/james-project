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

import java.io.FileNotFoundException;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQHealthCheck;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.rabbitmq.RabbitMQMailQueue;
import org.apache.james.queue.rabbitmq.RabbitMQMailQueueFactory;
import org.apache.james.queue.rabbitmq.view.RabbitMQMailQueueConfiguration;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.james.queue.rabbitmq.view.cassandra.BrowseStartDAO;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueBrowser;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueMailDelete;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueMailStore;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueView;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewStartUpCheck;
import org.apache.james.queue.rabbitmq.view.cassandra.DeletedMailsDAO;
import org.apache.james.queue.rabbitmq.view.cassandra.EnqueuedMailsDAO;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfigurationModule;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.EventsourcingConfigurationManagement;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.Sender;

public class RabbitMQModule extends AbstractModule {

    public static final String RABBITMQ_CONFIGURATION_NAME = "rabbitmq";

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQModule.class);

    @Override
    protected void configure() {
        bind(EnqueuedMailsDAO.class).in(Scopes.SINGLETON);
        bind(DeletedMailsDAO.class).in(Scopes.SINGLETON);
        bind(BrowseStartDAO.class).in(Scopes.SINGLETON);
        bind(CassandraMailQueueBrowser.class).in(Scopes.SINGLETON);
        bind(CassandraMailQueueMailDelete.class).in(Scopes.SINGLETON);
        bind(CassandraMailQueueMailStore.class).in(Scopes.SINGLETON);

        Multibinder<CassandraModule> cassandraModuleBinder = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraModuleBinder.addBinding().toInstance(CassandraMailQueueViewModule.MODULE);

        bind(EventsourcingConfigurationManagement.class).in(Scopes.SINGLETON);
        Multibinder<EventDTOModule<? extends Event, ? extends EventDTO>> eventDTOModuleBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<EventDTOModule<? extends Event, ? extends EventDTO>>() {});
        eventDTOModuleBinder.addBinding().toInstance(CassandraMailQueueViewConfigurationModule.MAIL_QUEUE_VIEW_CONFIGURATION);

        Multibinder.newSetBinder(binder(), StartUpCheck.class).addBinding().to(CassandraMailQueueViewStartUpCheck.class);
        Multibinder.newSetBinder(binder(), HealthCheck.class).addBinding().to(RabbitMQHealthCheck.class);

        bind(ReactorRabbitMQChannelPool.Configuration.class).toInstance(ReactorRabbitMQChannelPool.Configuration.DEFAULT);
    }

    @Provides
    @Singleton
    public MailQueueView.Factory provideMailQueueViewFactory(CassandraMailQueueView.Factory cassandraMailQueueViewFactory) {
        return cassandraMailQueueViewFactory;
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
    @Named(RABBITMQ_CONFIGURATION_NAME)
    @Singleton
    private org.apache.commons.configuration2.Configuration getConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return propertiesProvider.getConfiguration(RABBITMQ_CONFIGURATION_NAME);
        } catch (FileNotFoundException e) {
            LOGGER.error("Could not find " + RABBITMQ_CONFIGURATION_NAME + " configuration file.");
            throw new RuntimeException(e);
        }
    }

    @Provides
    @Singleton
    private RabbitMQConfiguration getMailQueueConfiguration(@Named(RABBITMQ_CONFIGURATION_NAME) org.apache.commons.configuration2.Configuration configuration) {
        return RabbitMQConfiguration.from(configuration);
    }

    @Provides
    @Singleton
    private CassandraMailQueueViewConfiguration getMailQueueViewConfiguration(@Named(RABBITMQ_CONFIGURATION_NAME) org.apache.commons.configuration2.Configuration configuration) {
        return CassandraMailQueueViewConfiguration.from(configuration);
    }

    @Provides
    @Singleton
    private RabbitMQMailQueueConfiguration getMailQueueSizeConfiguration(@Named(RABBITMQ_CONFIGURATION_NAME) org.apache.commons.configuration2.Configuration configuration) {
        return RabbitMQMailQueueConfiguration.from(configuration);
    }

    @Provides
    @Singleton
    ReactorRabbitMQChannelPool provideReactorRabbitMQChannelPool(SimpleConnectionPool simpleConnectionPool, ReactorRabbitMQChannelPool.Configuration configuration) {
        ReactorRabbitMQChannelPool channelPool = new ReactorRabbitMQChannelPool(
            simpleConnectionPool.getResilientConnection(),
            configuration);
        channelPool.start();
        return channelPool;
    }

    @Provides
    @Singleton
    public Sender provideRabbitMQSender(ReactorRabbitMQChannelPool channelPool) {
        return channelPool.getSender();
    }

    @Provides
    @Singleton
    public ReceiverProvider provideRabbitMQReceiver(SimpleConnectionPool simpleConnectionPool) {
        return () -> RabbitFlux.createReceiver(new ReceiverOptions().connectionMono(simpleConnectionPool.getResilientConnection()));
    }
}
