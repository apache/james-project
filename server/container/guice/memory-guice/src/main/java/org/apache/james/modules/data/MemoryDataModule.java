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

import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.utils.ConfigurationPerformer;
import org.apache.james.utils.ConfigurationProvider;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class MemoryDataModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new SieveFileRepositoryModule());

        bind(MemoryDomainList.class).in(Scopes.SINGLETON);
        bind(DomainList.class).to(MemoryDomainList.class);

        bind(MemoryRecipientRewriteTable.class).in(Scopes.SINGLETON);
        bind(RecipientRewriteTable.class).to(MemoryRecipientRewriteTable.class);

        bind(MemoryUsersRepository.class).toInstance(MemoryUsersRepository.withVirtualHosting());
        bind(UsersRepository.class).to(MemoryUsersRepository.class);

        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(MemoryDataConfigurationPerformer.class);
    }

    @Singleton
    public static class MemoryDataConfigurationPerformer implements ConfigurationPerformer {

        private final ConfigurationProvider configurationProvider;
        private final MemoryDomainList memoryDomainList;
        private final MemoryRecipientRewriteTable memoryRecipientRewriteTable;

        @Inject
        public MemoryDataConfigurationPerformer(ConfigurationProvider configurationProvider, MemoryDomainList memoryDomainList, MemoryRecipientRewriteTable memoryRecipientRewriteTable) {
            this.configurationProvider = configurationProvider;
            this.memoryDomainList = memoryDomainList;
            this.memoryRecipientRewriteTable = memoryRecipientRewriteTable;
        }

        @Override
        public void initModule() {
            try {
                memoryDomainList.configure(configurationProvider.getConfiguration("domainlist"));
                memoryRecipientRewriteTable.configure(configurationProvider.getConfiguration("recipientrewritetable"));
            } catch (ConfigurationException e) {
                Throwables.propagate(e);
            }
        }

        @Override
        public List<Class<? extends Configurable>> forClasses() {
            return ImmutableList.of(MemoryDomainList.class, MemoryRecipientRewriteTable.class);
        }
    }
}
