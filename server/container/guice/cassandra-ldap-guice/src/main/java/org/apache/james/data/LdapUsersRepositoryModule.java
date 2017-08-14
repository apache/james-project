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
package org.apache.james.data;

import java.util.List;

import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.ldap.ReadOnlyUsersLDAPRepository;
import org.apache.james.utils.ConfigurationPerformer;
import org.apache.james.utils.ConfigurationProvider;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class LdapUsersRepositoryModule extends AbstractModule {

    @Override
    public void configure() {
        bind(ReadOnlyUsersLDAPRepository.class).in(Scopes.SINGLETON);
        bind(UsersRepository.class).to(ReadOnlyUsersLDAPRepository.class);

        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(LdapUsersRepositoryConfigurationPerformer.class);
    }

    @Singleton
    public static class LdapUsersRepositoryConfigurationPerformer implements ConfigurationPerformer {

        private final ConfigurationProvider configurationProvider;
        private final ReadOnlyUsersLDAPRepository usersRepository;

        @Inject
        public LdapUsersRepositoryConfigurationPerformer(ConfigurationProvider configurationProvider, ReadOnlyUsersLDAPRepository usersRepository) {
            this.configurationProvider = configurationProvider;
            this.usersRepository = usersRepository;
        }

        @Override
        public void initModule() {
            try {
                usersRepository.configure(configurationProvider.getConfiguration("usersrepository"));
                usersRepository.init();
            } catch (Exception e) {
                Throwables.propagate(e);
            }
        }

        @Override
        public List<Class<? extends Configurable>> forClasses() {
            return ImmutableList.of(ReadOnlyUsersLDAPRepository.class);
        }
    }

}
