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

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.lib.UsersDAO;
import org.apache.james.user.postgres.PostgresUserModule;
import org.apache.james.user.postgres.PostgresUsersDAO;
import org.apache.james.user.postgres.PostgresUsersRepository;
import org.apache.james.user.postgres.PostgresUsersRepositoryConfiguration;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class PostgresUsersRepositoryModule extends AbstractModule {
    @Override
    public void configure() {
        bind(PostgresUsersRepository.class).in(Scopes.SINGLETON);
        bind(UsersRepository.class).to(PostgresUsersRepository.class);

        bind(PostgresUsersDAO.class).in(Scopes.SINGLETON);
        bind(UsersDAO.class).to(PostgresUsersDAO.class);

        Multibinder<PostgresModule> postgresDataDefinitions = Multibinder.newSetBinder(binder(), PostgresModule.class);
        postgresDataDefinitions.addBinding().toInstance(PostgresUserModule.MODULE);
    }

    @Provides
    @Singleton
    public PostgresUsersRepositoryConfiguration provideConfiguration(ConfigurationProvider configurationProvider) throws ConfigurationException {
        return PostgresUsersRepositoryConfiguration.from(
            configurationProvider.getConfiguration("usersrepository"));
    }
}
