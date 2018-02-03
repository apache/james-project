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

import java.util.List;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.file.FileMailRepository;
import org.apache.james.utils.ConfigurationPerformer;
import org.apache.james.utils.ConfigurationProvider;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.InMemoryMailRepositoryStore;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.MailRepositoryProvider;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class MailStoreRepositoryModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(InMemoryMailRepositoryStore.class).in(Scopes.SINGLETON);
        bind(MailRepositoryStore.class).to(InMemoryMailRepositoryStore.class);

        Multibinder<MailRepositoryProvider> multibinder = Multibinder.newSetBinder(binder(), MailRepositoryProvider.class);
        multibinder.addBinding().to(FileMailRepositoryProvider.class);
        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(MailRepositoryStoreModuleConfigurationPerformer.class);
        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(MailRepositoryProbeImpl.class);
    }

    public static class FileMailRepositoryProvider implements MailRepositoryProvider {

        private final FileSystem fileSystem;

        @Inject
        public FileMailRepositoryProvider(FileSystem fileSystem) {
            this.fileSystem = fileSystem;
        }


        @Override
        public String canonicalName() {
            return FileMailRepository.class.getCanonicalName();
        }

        @Override
        public MailRepository provide(String url) {
            FileMailRepository fileMailRepository = new FileMailRepository();
            fileMailRepository.setFileSystem(fileSystem);
            return fileMailRepository;
        }
    }

    @Singleton
    public static class MailRepositoryStoreModuleConfigurationPerformer implements ConfigurationPerformer {

        private final ConfigurationProvider configurationProvider;
        private final InMemoryMailRepositoryStore javaMailRepositoryStore;

        @Inject
        public MailRepositoryStoreModuleConfigurationPerformer(ConfigurationProvider configurationProvider,
                InMemoryMailRepositoryStore javaMailRepositoryStore) {
            this.configurationProvider = configurationProvider;
            this.javaMailRepositoryStore = javaMailRepositoryStore;
        }

        @Override
        public void initModule() {
            try {
                javaMailRepositoryStore.configure(configurationProvider.getConfiguration("mailrepositorystore"));
                javaMailRepositoryStore.init();
            } catch (Exception e) {
                Throwables.propagate(e);
            }
        }

        @Override
        public List<Class<? extends Configurable>> forClasses() {
            return ImmutableList.of(InMemoryMailRepositoryStore.class);
        }
    }

}
