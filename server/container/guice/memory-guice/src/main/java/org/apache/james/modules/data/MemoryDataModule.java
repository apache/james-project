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
import org.apache.james.dlp.api.DLPConfigurationStore;
import org.apache.james.dlp.eventsourcing.EventSourcingDLPConfigurationStore;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailrepository.api.MailRepositoryUrlStore;
import org.apache.james.mailrepository.memory.MemoryMailRepositoryUrlStore;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.utils.ConfigurationPerformer;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class MemoryDataModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new SieveFileRepositoryModule());

        bind(EventSourcingDLPConfigurationStore.class).in(Scopes.SINGLETON);
        bind(DLPConfigurationStore.class).to(EventSourcingDLPConfigurationStore.class);

        bind(MemoryDomainList.class).in(Scopes.SINGLETON);
        bind(DomainList.class).to(MemoryDomainList.class);

        bind(MemoryRecipientRewriteTable.class).in(Scopes.SINGLETON);
        bind(RecipientRewriteTable.class).to(MemoryRecipientRewriteTable.class);

        bind(MemoryMailRepositoryUrlStore.class).in(Scopes.SINGLETON);
        bind(MailRepositoryUrlStore.class).to(MemoryMailRepositoryUrlStore.class);

        bind(MemoryUsersRepository.class).toInstance(MemoryUsersRepository.withVirtualHosting());
        bind(UsersRepository.class).to(MemoryUsersRepository.class);

        bind(EventSourcingDLPConfigurationStore.class).in(Scopes.SINGLETON);
        bind(DLPConfigurationStore.class).to(EventSourcingDLPConfigurationStore.class);

        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(MemoryDataConfigurationPerformer.class);
    }

    @Provides
    @Singleton
    public DomainListConfiguration provideDomainListConfiguration(ConfigurationProvider configurationProvider) {
        try {
            return DomainListConfiguration.from(configurationProvider.getConfiguration("domainlist"));
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    @Singleton
    public static class MemoryDataConfigurationPerformer implements ConfigurationPerformer {

        private final ConfigurationProvider configurationProvider;
        private final DomainListConfiguration domainListConfiguration;
        private final MemoryDomainList memoryDomainList;
        private final MemoryRecipientRewriteTable memoryRecipientRewriteTable;

        @Inject
        public MemoryDataConfigurationPerformer(ConfigurationProvider configurationProvider, DomainListConfiguration domainListConfiguration, MemoryDomainList memoryDomainList, MemoryRecipientRewriteTable memoryRecipientRewriteTable) {
            this.configurationProvider = configurationProvider;
            this.domainListConfiguration = domainListConfiguration;
            this.memoryDomainList = memoryDomainList;
            this.memoryRecipientRewriteTable = memoryRecipientRewriteTable;
        }

        @Override
        public void initModule() {
            try {
                memoryDomainList.configure(domainListConfiguration);
                memoryRecipientRewriteTable.configure(configurationProvider.getConfiguration("recipientrewritetable"));
            } catch (ConfigurationException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<Class<? extends Configurable>> forClasses() {
            return ImmutableList.of(MemoryDomainList.class, MemoryRecipientRewriteTable.class);
        }
    }
}
