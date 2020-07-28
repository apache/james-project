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

package org.apache.james;

import java.io.File;
import java.util.Optional;

import org.apache.james.server.core.configuration.Configuration;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Module;

public class JamesServerBuilder<T extends Configuration> {
    private static final boolean DEFAULT_AUTO_START = true;

    @FunctionalInterface
    public interface ConfigurationProvider<T extends Configuration> {
        T buildConfiguration(File tempDirectory);
    }

    @FunctionalInterface
    public interface ServerProvider<T extends Configuration>  {
        GuiceJamesServer buildServer(T configuration);
    }


    public static ConfigurationProvider<Configuration.Basic> defaultConfigurationProvider() {
        return tmpDir ->
            Configuration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .build();
    }

    private final ImmutableList.Builder<GuiceModuleTestExtension> extensions;
    private final TemporaryFolderRegistrableExtension folderRegistrableExtension;
    private final ImmutableList.Builder<Module> overrideModules;
    private ServerProvider server;
    private ConfigurationProvider configuration;
    private Optional<Boolean> autoStart;
    private JamesServerExtension.Lifecycle lifecycle;

    public JamesServerBuilder(ConfigurationProvider configurationProvider) {
        configuration = configurationProvider;
        extensions = ImmutableList.builder();
        folderRegistrableExtension = new TemporaryFolderRegistrableExtension();
        autoStart = Optional.empty();
        lifecycle = JamesServerExtension.Lifecycle.PER_TEST;
        overrideModules = ImmutableList.builder();
    }


    public JamesServerBuilder<T> extensions(GuiceModuleTestExtension... extensions) {
        this.extensions.add(extensions);
        return this;
    }

    public JamesServerBuilder<T> extension(GuiceModuleTestExtension extension) {
        return this.extensions(extension);
    }

    public JamesServerBuilder<T> server(ServerProvider<T> server) {
        this.server = server;
        return this;
    }

    public JamesServerBuilder<T> overrideServerModule(Module module) {
        this.overrideModules.add(module);
        return this;
    }

    public JamesServerBuilder<T> disableAutoStart() {
        this.autoStart = Optional.of(false);
        return this;
    }

    public JamesServerBuilder<T> lifeCycle(JamesServerExtension.Lifecycle lifecycle) {
        this.lifecycle = lifecycle;
        return this;
    }

    public JamesServerExtension build() {
        Preconditions.checkNotNull(server);
        JamesServerExtension.AwaitCondition awaitCondition = () -> extensions.build().forEach(GuiceModuleTestExtension::await);

        return new JamesServerExtension(buildAggregateJunitExtension(), file -> overrideServerWithExtensionsModules(file, configuration),
            awaitCondition, autoStart.orElse(DEFAULT_AUTO_START), lifecycle);
    }

    private AggregateJunitExtension buildAggregateJunitExtension() {
        ImmutableList<GuiceModuleTestExtension> extensions = this.extensions.build();
        return new AggregateJunitExtension(
            ImmutableList.<RegistrableExtension>builder()
                .addAll(extensions)
                .add(folderRegistrableExtension)
                .build());
    }

    private GuiceJamesServer overrideServerWithExtensionsModules(File file, ConfigurationProvider configurationProvider) {
        ImmutableList<Module> modules = extensions.build()
            .stream()
            .map(GuiceModuleTestExtension::getModule)
            .collect(Guavate.toImmutableList());

        return server
            .buildServer(configurationProvider.buildConfiguration(file))
            .overrideWith(modules)
            .overrideWith((binder -> binder.bind(CleanupTasksPerformer.class).asEagerSingleton()))
            .overrideWith(overrideModules.build());
    }

}
