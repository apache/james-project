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

import org.apache.james.DefaultVacationService;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.vacation.api.NotificationRegistry;
import org.apache.james.vacation.api.VacationDeleteUserTaskStep;
import org.apache.james.vacation.api.VacationRepository;
import org.apache.james.vacation.api.VacationService;
import org.apache.james.vacation.cassandra.CassandraNotificationRegistry;
import org.apache.james.vacation.cassandra.CassandraVacationRepository;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class CassandraVacationModule extends AbstractModule {

    @Override
    public void configure() {
        bind(DefaultVacationService.class).in(Scopes.SINGLETON);
        bind(VacationService.class).to(DefaultVacationService.class);

        bind(CassandraVacationRepository.class).in(Scopes.SINGLETON);
        bind(VacationRepository.class).to(CassandraVacationRepository.class);

        bind(CassandraNotificationRegistry.class).in(Scopes.SINGLETON);
        bind(NotificationRegistry.class).to(CassandraNotificationRegistry.class);

        Multibinder<CassandraModule> cassandraVacationDefinitions = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraVacationDefinitions.addBinding().toInstance(org.apache.james.vacation.cassandra.CassandraVacationModule.MODULE);
        cassandraVacationDefinitions.addBinding().toInstance(org.apache.james.vacation.cassandra.CassandraNotificationRegistryModule.MODULE);

        Multibinder<DeleteUserDataTaskStep> deleteUserDataTaskSteps = Multibinder.newSetBinder(binder(), DeleteUserDataTaskStep.class);
        deleteUserDataTaskSteps.addBinding().to(VacationDeleteUserTaskStep.class);
    }

}
