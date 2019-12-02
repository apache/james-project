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
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.jmap.api.access.AccessTokenRepository;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.jmap.api.filtering.impl.EventSourcingFilteringManagement;
import org.apache.james.jmap.api.projections.MessagePreviewStore;
import org.apache.james.jmap.api.vacation.NotificationRegistry;
import org.apache.james.jmap.api.vacation.VacationRepository;
import org.apache.james.jmap.cassandra.access.CassandraAccessModule;
import org.apache.james.jmap.cassandra.access.CassandraAccessTokenRepository;
import org.apache.james.jmap.cassandra.filtering.FilteringRuleSetDefineDTOModules;
import org.apache.james.jmap.cassandra.projections.CassandraMessagePreviewModule;
import org.apache.james.jmap.cassandra.projections.CassandraMessagePreviewStore;
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

        bind(CassandraMessagePreviewStore.class).in(Scopes.SINGLETON);
        bind(MessagePreviewStore.class).to(CassandraMessagePreviewStore.class);

        Multibinder<CassandraModule> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraDataDefinitions.addBinding().toInstance(CassandraAccessModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraVacationModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraNotificationRegistryModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraMessagePreviewModule.MODULE);

        Multibinder<EventDTOModule<?, ?>> eventDTOModuleBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<EventDTOModule<?, ?>>() {});
        eventDTOModuleBinder.addBinding().toInstance(FilteringRuleSetDefineDTOModules.FILTERING_RULE_SET_DEFINED);
    }
}
