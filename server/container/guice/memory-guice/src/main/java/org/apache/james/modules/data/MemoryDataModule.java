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

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.dlp.api.DLPConfigurationStore;
import org.apache.james.dlp.eventsourcing.EventSourcingDLPConfigurationStore;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailrepository.api.MailRepositoryUrlStore;
import org.apache.james.mailrepository.api.Protocol;
import org.apache.james.mailrepository.memory.MailRepositoryStoreConfiguration;
import org.apache.james.mailrepository.memory.MemoryMailRepository;
import org.apache.james.mailrepository.memory.MemoryMailRepositoryUrlStore;
import org.apache.james.modules.server.MailStoreRepositoryModule;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.ReverseRecipientRewriteTable;
import org.apache.james.rrt.lib.CanSendFromImpl;
import org.apache.james.rrt.lib.ReverseRecipientRewriteTableImpl;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;

public class MemoryDataModule extends AbstractModule {
    private static final MailRepositoryStoreConfiguration.Item MEMORY_MAILREPOSITORY_DEFAULT_DECLARATION = new MailRepositoryStoreConfiguration.Item(
        ImmutableList.of(new Protocol("memory")),
        MemoryMailRepository.class.getName(),
        new BaseHierarchicalConfiguration());

    @Override
    protected void configure() {
        install(new SieveFileRepositoryModule());

        bind(EventSourcingDLPConfigurationStore.class).in(Scopes.SINGLETON);
        bind(DLPConfigurationStore.class).to(EventSourcingDLPConfigurationStore.class);

        bind(MemoryDomainList.class).in(Scopes.SINGLETON);
        bind(DomainList.class).to(MemoryDomainList.class);

        bind(MemoryRecipientRewriteTable.class).in(Scopes.SINGLETON);
        bind(RecipientRewriteTable.class).to(MemoryRecipientRewriteTable.class);

        bind(ReverseRecipientRewriteTableImpl.class).in(Scopes.SINGLETON);
        bind(ReverseRecipientRewriteTable.class).to(ReverseRecipientRewriteTableImpl.class);

        bind(CanSendFromImpl.class).in(Scopes.SINGLETON);
        bind(CanSendFrom.class).to(CanSendFromImpl.class);

        bind(MemoryMailRepositoryUrlStore.class).in(Scopes.SINGLETON);
        bind(MailRepositoryUrlStore.class).to(MemoryMailRepositoryUrlStore.class);

        bind(EventSourcingDLPConfigurationStore.class).in(Scopes.SINGLETON);
        bind(DLPConfigurationStore.class).to(EventSourcingDLPConfigurationStore.class);

        bind(UsersRepository.class).to(MemoryUsersRepository.class);

        bind(MailStoreRepositoryModule.DefaultItemSupplier.class).toInstance(() -> MEMORY_MAILREPOSITORY_DEFAULT_DECLARATION);
    }

    @Provides
    @Singleton
    public MemoryUsersRepository providesUsersRepository(DomainList domainList) {
        return MemoryUsersRepository.withVirtualHosting(domainList);
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

    @ProvidesIntoSet
    InitializationOperation configureDomainList(DomainListConfiguration domainListConfiguration, MemoryDomainList memoryDomainList) {
        return InitilizationOperationBuilder
            .forClass(MemoryDomainList.class)
            .init(() -> memoryDomainList.configure(domainListConfiguration));
    }

    @ProvidesIntoSet
    InitializationOperation configureRRT(ConfigurationProvider configurationProvider, MemoryRecipientRewriteTable memoryRecipientRewriteTable) {
        return InitilizationOperationBuilder
            .forClass(MemoryRecipientRewriteTable.class)
            .init(() -> memoryRecipientRewriteTable.configure(configurationProvider.getConfiguration("recipientrewritetable")));
    }
}
