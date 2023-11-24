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
import org.apache.james.CoreDataModule;
import org.apache.james.DefaultVacationService;
import org.apache.james.dlp.api.DLPConfigurationStore;
import org.apache.james.dlp.eventsourcing.EventSourcingDLPConfigurationStore;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.lib.DomainCreator;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailrepository.api.MailRepositoryFactory;
import org.apache.james.mailrepository.api.MailRepositoryUrlStore;
import org.apache.james.mailrepository.api.Protocol;
import org.apache.james.mailrepository.memory.MailRepositoryStoreConfiguration;
import org.apache.james.mailrepository.memory.MemoryMailRepository;
import org.apache.james.mailrepository.memory.MemoryMailRepositoryFactory;
import org.apache.james.mailrepository.memory.MemoryMailRepositoryUrlStore;
import org.apache.james.rrt.api.AliasReverseResolver;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.lib.AliasReverseResolverImpl;
import org.apache.james.rrt.lib.CanSendFromImpl;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.util.date.DefaultZonedDateTimeProvider;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.vacation.api.NotificationRegistry;
import org.apache.james.vacation.api.VacationDeleteUserTaskStep;
import org.apache.james.vacation.api.VacationRepository;
import org.apache.james.vacation.api.VacationService;
import org.apache.james.vacation.memory.MemoryNotificationRegistry;
import org.apache.james.vacation.memory.MemoryVacationRepository;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class MemoryDataModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new SieveFileRepositoryModule());
        install(new CoreDataModule());

        bind(EventSourcingDLPConfigurationStore.class).in(Scopes.SINGLETON);
        bind(DLPConfigurationStore.class).to(EventSourcingDLPConfigurationStore.class);

        bind(MemoryDomainList.class).in(Scopes.SINGLETON);
        bind(DomainList.class).to(MemoryDomainList.class);

        bind(MemoryRecipientRewriteTable.class).in(Scopes.SINGLETON);
        bind(RecipientRewriteTable.class).to(MemoryRecipientRewriteTable.class);

        bind(AliasReverseResolverImpl.class).in(Scopes.SINGLETON);
        bind(AliasReverseResolver.class).to(AliasReverseResolverImpl.class);

        bind(CanSendFromImpl.class).in(Scopes.SINGLETON);
        bind(CanSendFrom.class).to(CanSendFromImpl.class);

        bind(MemoryMailRepositoryUrlStore.class).in(Scopes.SINGLETON);
        bind(MailRepositoryUrlStore.class).to(MemoryMailRepositoryUrlStore.class);

        bind(EventSourcingDLPConfigurationStore.class).in(Scopes.SINGLETON);
        bind(DLPConfigurationStore.class).to(EventSourcingDLPConfigurationStore.class);

        bind(DefaultVacationService.class).in(Scopes.SINGLETON);
        bind(VacationService.class).to(DefaultVacationService.class);

        bind(MemoryVacationRepository.class).in(Scopes.SINGLETON);
        bind(VacationRepository.class).to(MemoryVacationRepository.class);

        bind(MemoryNotificationRegistry.class).in(Scopes.SINGLETON);
        bind(NotificationRegistry.class).to(MemoryNotificationRegistry.class);

        bind(DefaultZonedDateTimeProvider.class).in(Scopes.SINGLETON);
        bind(ZonedDateTimeProvider.class).to(DefaultZonedDateTimeProvider.class);

        bind(MailRepositoryStoreConfiguration.Item.class)
            .toProvider(() -> new MailRepositoryStoreConfiguration.Item(
                ImmutableList.of(new Protocol("memory")),
                MemoryMailRepository.class.getName(),
                new BaseHierarchicalConfiguration()));

        Multibinder.newSetBinder(binder(), MailRepositoryFactory.class)
                .addBinding().to(MemoryMailRepositoryFactory.class);

        Multibinder<DeleteUserDataTaskStep> deleteUserDataTaskSteps = Multibinder.newSetBinder(binder(), DeleteUserDataTaskStep.class);
        deleteUserDataTaskSteps.addBinding().to(VacationDeleteUserTaskStep.class);
    }

    @ProvidesIntoSet
    InitializationOperation configureDomainList(DomainListConfiguration configuration, MemoryDomainList memoryDomainList) {
        return InitilizationOperationBuilder
            .forClass(DomainCreator.class)
            .init(() -> new DomainCreator(memoryDomainList, configuration).createConfiguredDomains());
    }

    @ProvidesIntoSet
    InitializationOperation configureRRT(ConfigurationProvider configurationProvider, MemoryRecipientRewriteTable memoryRecipientRewriteTable) {
        return InitilizationOperationBuilder
            .forClass(MemoryRecipientRewriteTable.class)
            .init(() -> memoryRecipientRewriteTable.configure(configurationProvider.getConfiguration("recipientrewritetable")));
    }
}
