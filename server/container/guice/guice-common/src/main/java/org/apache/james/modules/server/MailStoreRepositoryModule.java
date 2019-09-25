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

package org.apache.james.modules.server;

import java.util.function.Supplier;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.memory.MailRepositoryLoader;
import org.apache.james.mailrepository.memory.MailRepositoryStoreConfiguration;
import org.apache.james.mailrepository.memory.MemoryMailRepositoryStore;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.InitialisationOperation;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class MailStoreRepositoryModule extends AbstractModule {

    public static final Logger LOGGER = LoggerFactory.getLogger(MailStoreRepositoryModule.class);

    public interface DefaultItemSupplier extends Supplier<MailRepositoryStoreConfiguration.Item> {}

    @Override
    protected void configure() {
        bind(MemoryMailRepositoryStore.class).in(Scopes.SINGLETON);
        bind(MailRepositoryStore.class).to(MemoryMailRepositoryStore.class);

        bind(GuiceMailRepositoryLoader.class).in(Scopes.SINGLETON);
        bind(MailRepositoryLoader.class).to(GuiceMailRepositoryLoader.class);

        Multibinder.newSetBinder(binder(), InitialisationOperation.class).addBinding().to(MailRepositoryStoreModuleInitialisationOperation.class);
        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(MailRepositoryProbeImpl.class);
    }

    @Provides
    @Singleton
    MailRepositoryStoreConfiguration provideConfiguration(ConfigurationProvider configurationProvider, DefaultItemSupplier defaultItemSupplier) throws ConfigurationException {
        HierarchicalConfiguration<ImmutableNode> configuration = configurationProvider.getConfiguration("mailrepositorystore");
        MailRepositoryStoreConfiguration userConfiguration = MailRepositoryStoreConfiguration.parse(configuration);
        if (!userConfiguration.getItems().isEmpty()) {
            return userConfiguration;
        }
        LOGGER.warn("Empty MailRepository store configuration supplied. Defaulting to default configuration for this product");
        return MailRepositoryStoreConfiguration.forItems(defaultItemSupplier.get());
    }

    @Singleton
    public static class MailRepositoryStoreModuleInitialisationOperation implements InitialisationOperation {
        private final MemoryMailRepositoryStore javaMailRepositoryStore;

        @Inject
        public MailRepositoryStoreModuleInitialisationOperation(MemoryMailRepositoryStore javaMailRepositoryStore) {
            this.javaMailRepositoryStore = javaMailRepositoryStore;
        }

        @Override
        public void initModule() throws Exception {
            javaMailRepositoryStore.init();
        }

        @Override
        public Class<? extends Startable> forClass() {
            return MemoryMailRepositoryStore.class;
        }
    }

}
