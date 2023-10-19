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
import org.apache.james.adapter.mailbox.DelegationStoreAuthorizator;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.user.api.DelegationStore;
import org.apache.james.user.api.DelegationUsernameChangeTaskStep;
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.apache.james.user.cassandra.CassandraDelegationStore;
import org.apache.james.user.cassandra.CassandraRepositoryConfiguration;
import org.apache.james.user.cassandra.CassandraUsersDAO;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class CassandraDelegationStoreModule extends AbstractModule {
    @Override
    public void configure() {
        bind(DelegationStore.class).to(CassandraDelegationStore.class);
        bind(CassandraDelegationStore.UserExistencePredicate.class).to(CassandraDelegationStore.UserExistencePredicateImplementation.class);
        bind(Authorizator.class).to(DelegationStoreAuthorizator.class);
        Multibinder<CassandraModule> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraDataDefinitions.addBinding().toInstance(org.apache.james.user.cassandra.CassandraUsersRepositoryModule.MODULE);

        Multibinder.newSetBinder(binder(), UsernameChangeTaskStep.class)
            .addBinding().to(DelegationUsernameChangeTaskStep.class);
        bind(CassandraUsersDAO.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    public CassandraRepositoryConfiguration provideConfiguration(ConfigurationProvider configurationProvider) throws ConfigurationException {
        return CassandraRepositoryConfiguration.from(
            configurationProvider.getConfiguration("usersrepository"));
    }
}
