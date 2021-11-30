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

package org.apache.james.modules.data;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.jmap.api.access.AccessTokenRepository;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.jmap.api.filtering.impl.EventSourcingFilteringManagement;
import org.apache.james.jmap.api.identity.CustomIdentityDAO;
import org.apache.james.jmap.api.projections.EmailQueryView;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.jmap.api.projections.MessageFastViewProjectionHealthCheck;
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionRepository;
import org.apache.james.jmap.api.upload.UploadRepository;
import org.apache.james.jmap.cassandra.access.CassandraAccessModule;
import org.apache.james.jmap.cassandra.access.CassandraAccessTokenRepository;
import org.apache.james.jmap.cassandra.change.CassandraEmailChangeModule;
import org.apache.james.jmap.cassandra.change.CassandraMailboxChangeModule;
import org.apache.james.jmap.cassandra.filtering.FilteringRuleSetDefineDTOModules;
import org.apache.james.jmap.cassandra.identity.CassandraCustomIdentityDAO;
import org.apache.james.jmap.cassandra.identity.CassandraCustomIdentityModule;
import org.apache.james.jmap.cassandra.projections.CassandraEmailQueryView;
import org.apache.james.jmap.cassandra.projections.CassandraEmailQueryViewModule;
import org.apache.james.jmap.cassandra.projections.CassandraMessageFastViewProjection;
import org.apache.james.jmap.cassandra.projections.CassandraMessageFastViewProjectionModule;
import org.apache.james.jmap.cassandra.pushsubscription.CassandraPushSubscriptionModule;
import org.apache.james.jmap.cassandra.pushsubscription.CassandraPushSubscriptionRepository;
import org.apache.james.jmap.cassandra.upload.CassandraUploadRepository;
import org.apache.james.jmap.cassandra.upload.UploadConfiguration;
import org.apache.james.jmap.cassandra.upload.UploadDAO;
import org.apache.james.jmap.cassandra.upload.UploadModule;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

public class CassandraJmapModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(CassandraAccessTokenRepository.class).in(Scopes.SINGLETON);
        bind(AccessTokenRepository.class).to(CassandraAccessTokenRepository.class);

        bind(CassandraUploadRepository.class).in(Scopes.SINGLETON);
        bind(UploadDAO.class).in(Scopes.SINGLETON);
        bind(UploadRepository.class).to(CassandraUploadRepository.class);
        bind(UploadConfiguration.class).toInstance(UploadConfiguration.SINGLETON);

        bind(CassandraCustomIdentityDAO.class).in(Scopes.SINGLETON);
        bind(CustomIdentityDAO.class).to(CassandraCustomIdentityDAO.class);

        bind(EventSourcingFilteringManagement.class).in(Scopes.SINGLETON);
        bind(FilteringManagement.class).to(EventSourcingFilteringManagement.class);

        bind(CassandraPushSubscriptionRepository.class).in(Scopes.SINGLETON);
        bind(PushSubscriptionRepository.class).to(CassandraPushSubscriptionRepository.class);

        bind(CassandraMessageFastViewProjection.class).in(Scopes.SINGLETON);
        bind(MessageFastViewProjection.class).to(CassandraMessageFastViewProjection.class);
        bind(MessageFastViewProjectionHealthCheck.class).in(Scopes.SINGLETON);
        Multibinder.newSetBinder(binder(), HealthCheck.class)
            .addBinding()
            .to(MessageFastViewProjectionHealthCheck.class);

        bind(CassandraEmailQueryView.class).in(Scopes.SINGLETON);
        bind(EmailQueryView.class).to(CassandraEmailQueryView.class);

        Multibinder<CassandraModule> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraDataDefinitions.addBinding().toInstance(CassandraAccessModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraMessageFastViewProjectionModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraEmailQueryViewModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraMailboxChangeModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraEmailChangeModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(UploadModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraPushSubscriptionModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraCustomIdentityModule.MODULE());

        Multibinder<EventDTOModule<? extends Event, ? extends EventDTO>> eventDTOModuleBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<EventDTOModule<? extends Event, ? extends EventDTO>>() {});
        eventDTOModuleBinder.addBinding().toInstance(FilteringRuleSetDefineDTOModules.FILTERING_RULE_SET_DEFINED);
    }
}
