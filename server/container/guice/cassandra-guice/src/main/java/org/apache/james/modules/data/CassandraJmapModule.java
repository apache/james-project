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
import org.apache.james.jmap.api.projections.EmailQueryView;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.jmap.api.projections.MessageFastViewProjectionHealthCheck;
import org.apache.james.jmap.api.vacation.NotificationRegistry;
import org.apache.james.jmap.api.vacation.VacationRepository;
import org.apache.james.jmap.cassandra.access.CassandraAccessModule;
import org.apache.james.jmap.cassandra.access.CassandraAccessTokenRepository;
import org.apache.james.jmap.cassandra.change.CassandraEmailChangeModule;
import org.apache.james.jmap.cassandra.change.CassandraMailboxChangeModule;
import org.apache.james.jmap.cassandra.filtering.FilteringRuleSetDefineDTOModules;
import org.apache.james.jmap.cassandra.projections.CassandraEmailQueryView;
import org.apache.james.jmap.cassandra.projections.CassandraEmailQueryViewModule;
import org.apache.james.jmap.cassandra.projections.CassandraMessageFastViewProjection;
import org.apache.james.jmap.cassandra.projections.CassandraMessageFastViewProjectionModule;
import org.apache.james.jmap.cassandra.vacation.CassandraNotificationRegistry;
import org.apache.james.jmap.cassandra.vacation.CassandraNotificationRegistryModule;
import org.apache.james.jmap.cassandra.vacation.CassandraVacationModule;
import org.apache.james.jmap.cassandra.vacation.CassandraVacationRepository;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

public class CassandraJmapModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(CassandraAccessTokenRepository.class).in(Scopes.SINGLETON);
        bind(AccessTokenRepository.class).to(CassandraAccessTokenRepository.class);

        bind(CassandraVacationRepository.class).in(Scopes.SINGLETON);
        bind(VacationRepository.class).to(CassandraVacationRepository.class);

        bind(CassandraNotificationRegistry.class).in(Scopes.SINGLETON);
        bind(NotificationRegistry.class).to(CassandraNotificationRegistry.class);

        bind(EventSourcingFilteringManagement.class).in(Scopes.SINGLETON);
        bind(FilteringManagement.class).to(EventSourcingFilteringManagement.class);

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
        cassandraDataDefinitions.addBinding().toInstance(CassandraVacationModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraNotificationRegistryModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraMessageFastViewProjectionModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraEmailQueryViewModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraMailboxChangeModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraEmailChangeModule.MODULE);

        Multibinder<EventDTOModule<? extends Event, ? extends EventDTO>> eventDTOModuleBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<EventDTOModule<? extends Event, ? extends EventDTO>>() {});
        eventDTOModuleBinder.addBinding().toInstance(FilteringRuleSetDefineDTOModules.FILTERING_RULE_SET_DEFINED);
    }
}
