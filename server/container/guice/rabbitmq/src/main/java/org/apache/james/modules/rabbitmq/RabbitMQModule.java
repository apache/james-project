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
package org.apache.james.modules.rabbitmq;

import java.io.FileNotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import javax.inject.Singleton;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.backend.rabbitmq.RabbitMQChannelPool;
import org.apache.james.backend.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backend.rabbitmq.SimpleChannelPool;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.rabbitmq.RabbitMQMailQueueFactory;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.james.queue.rabbitmq.view.cassandra.BrowseStartDAO;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueBrowser;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueMailDelete;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueMailStore;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueView;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule;
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
import com.google.inject.multibindings.Multibinder;

public class RabbitMQModule extends AbstractModule {

    public static final String RABBITMQ_CONFIGURATION_NAME = "rabbitmq";

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQModule.class);

    @Override
    protected void configure() {
        bind(Clock.class).toInstance(Clock.systemUTC());
        bind(ThreadLocalRandom.class).toInstance(ThreadLocalRandom.current());
        bind(CassandraMailQueueViewConfiguration.class).toInstance(CassandraMailQueueViewConfiguration
                .builder()
                .bucketCount(1)
                .updateBrowseStartPace(1000)
                .sliceWindow(Duration.ofHours(1))
                .build());

        bind(EnqueuedMailsDAO.class).in(Scopes.SINGLETON);
        bind(DeletedMailsDAO.class).in(Scopes.SINGLETON);
        bind(BrowseStartDAO.class).in(Scopes.SINGLETON);
        bind(CassandraMailQueueBrowser.class).in(Scopes.SINGLETON);
        bind(CassandraMailQueueMailDelete.class).in(Scopes.SINGLETON);
        bind(CassandraMailQueueMailStore.class).in(Scopes.SINGLETON);

        bind(SimpleChannelPool.class).in(Scopes.SINGLETON);
        bind(RabbitMQChannelPool.class).to(SimpleChannelPool.class);

        Multibinder<CassandraModule> cassandraModuleBinder = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraModuleBinder.addBinding().toInstance(CassandraMailQueueViewModule.MODULE);

        bind(EventsourcingConfigurationManagement.class).in(Scopes.SINGLETON);
        Multibinder<EventDTOModule> eventDTOModuleBinder = Multibinder.newSetBinder(binder(), EventDTOModule.class);
        eventDTOModuleBinder.addBinding().toInstance(CassandraMailQueueViewConfigurationModule.MAIL_QUEUE_VIEW_CONFIGURATION);
    }

    @Provides
    @Singleton
    public MailQueueView.Factory bindMailQueueViewFactory(CassandraMailQueueView.Factory cassandraMailQueueViewFactory) {
        return cassandraMailQueueViewFactory;
    }

    @Provides
    @Singleton
    public MailQueueFactory<?> bindRabbitMQQueueFactory(RabbitMQMailQueueFactory queueFactory) {
        return queueFactory;
    }

    @Provides
    @Singleton
    private RabbitMQConfiguration getMailQueueConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration(RABBITMQ_CONFIGURATION_NAME);
            return RabbitMQConfiguration.from(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.error("Could not find " + RABBITMQ_CONFIGURATION_NAME + " configuration file.");
            throw new RuntimeException(e);
        }
    }
}
