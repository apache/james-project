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

package org.apache.james.modules;

import jakarta.inject.Singleton;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.filesystem.api.JamesDirectoriesProvider;
import org.apache.james.modules.server.DNSServiceModule;
import org.apache.james.modules.server.DropWizardMetricsModule;
import org.apache.james.onami.lifecycle.PreDestroyModule;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.ExtensionModule;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;

public class CommonServicesModule extends AbstractModule {

    private final Configuration configuration;
    private final FileSystemImpl fileSystem;

    public CommonServicesModule(Configuration configuration) {
        this.configuration = configuration;
        this.fileSystem = new FileSystemImpl(configuration.directories());

    }
    
    @Override
    protected void configure() {
        install(new ExtensionModule());
        install(new StartUpChecksModule());
        install(new StartablesModule());
        install(new PreDestroyModule());
        install(new DNSServiceModule());
        install(new DropWizardMetricsModule());
        install(new CleanupTaskModule());
        install(new MimeMessageModule());
        install(new ClockModule());
        install(new PeriodicalHealthChecksModule());
        install(new ErrorMailRepositoryEmptyHealthCheckModule());
        install(RunArgumentsModule.EMPTY);

        bind(FileSystem.class).toInstance(fileSystem);
        bind(Configuration.class).toInstance(configuration);

        bind(ConfigurationProvider.class).toInstance(new FileConfigurationProvider(fileSystem, configuration));

        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(DataProbeImpl.class);
    }

    @Provides
    @Singleton
    public Configuration.ConfigurationPath configurationPath() {
        return configuration.configurationPath();
    }

    @Provides
    @Singleton
    public JamesDirectoriesProvider directories() {
        return configuration.directories();
    }

    @Provides
    @Singleton
    public PropertiesProvider providePropertiesProvider(FileSystem fileSystem, Configuration.ConfigurationPath configurationPrefix) {
        return new PropertiesProvider(fileSystem, configurationPrefix);
    }
}
