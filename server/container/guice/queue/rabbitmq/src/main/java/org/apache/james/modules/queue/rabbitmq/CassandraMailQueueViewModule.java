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

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.blob.api.BlobReferenceSource;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.james.queue.rabbitmq.view.cassandra.BrowseStartDAO;
import org.apache.james.queue.rabbitmq.view.cassandra.BrowseStartHealthCheck;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueBrowser;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueMailDelete;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueMailStore;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueView;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewStartUpCheck;
import org.apache.james.queue.rabbitmq.view.cassandra.ContentStartDAO;
import org.apache.james.queue.rabbitmq.view.cassandra.DeletedMailsDAO;
import org.apache.james.queue.rabbitmq.view.cassandra.EnqueuedMailsDAO;
import org.apache.james.queue.rabbitmq.view.cassandra.MailQueueViewBlobReferenceSource;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfigurationModule;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.EventsourcingConfigurationManagement;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

public class CassandraMailQueueViewModule extends AbstractModule {
    public static final String RABBITMQ_CONFIGURATION_NAME = "rabbitmq";

    @Override
    protected void configure() {
        bind(EnqueuedMailsDAO.class).in(Scopes.SINGLETON);
        bind(DeletedMailsDAO.class).in(Scopes.SINGLETON);
        bind(BrowseStartDAO.class).in(Scopes.SINGLETON);
        bind(CassandraMailQueueBrowser.class).in(Scopes.SINGLETON);
        bind(CassandraMailQueueMailDelete.class).in(Scopes.SINGLETON);
        bind(CassandraMailQueueMailStore.class).in(Scopes.SINGLETON);
        bind(ContentStartDAO.class).in(Scopes.SINGLETON);

        Multibinder<CassandraModule> cassandraModuleBinder = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraModuleBinder.addBinding().toInstance(org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.MODULE);

        bind(EventsourcingConfigurationManagement.class).in(Scopes.SINGLETON);
        Multibinder<EventDTOModule<? extends Event, ? extends EventDTO>> eventDTOModuleBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<EventDTOModule<? extends Event, ? extends EventDTO>>() {});
        eventDTOModuleBinder.addBinding().toInstance(CassandraMailQueueViewConfigurationModule.MAIL_QUEUE_VIEW_CONFIGURATION);

        Multibinder.newSetBinder(binder(), StartUpCheck.class).addBinding().to(CassandraMailQueueViewStartUpCheck.class);

        Multibinder.newSetBinder(binder(), BlobReferenceSource.class)
            .addBinding().to(MailQueueViewBlobReferenceSource.class);

        Multibinder.newSetBinder(binder(), HealthCheck.class)
            .addBinding().to(BrowseStartHealthCheck.class);
    }

    @Provides
    @Singleton
    public MailQueueView.Factory provideMailQueueViewFactory(CassandraMailQueueView.Factory cassandraMailQueueViewFactory) {
        return cassandraMailQueueViewFactory;
    }

    @Provides
    @Singleton
    private CassandraMailQueueViewConfiguration getMailQueueViewConfiguration(@Named(RABBITMQ_CONFIGURATION_NAME) org.apache.commons.configuration2.Configuration configuration) {
        return CassandraMailQueueViewConfiguration.from(configuration);
    }
}
