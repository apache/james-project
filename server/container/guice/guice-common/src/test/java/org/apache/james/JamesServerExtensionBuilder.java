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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.james.server.core.configuration.Configuration;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Module;

public class JamesServerExtensionBuilder {

    @FunctionalInterface
    interface ExtensionProvider {
        GuiceModuleTestExtension buildExtension(File tempDirectory);
    }

    @FunctionalInterface
    interface ConfigurationProvider {
        Configuration buildConfiguration(File tempDirectory);
    }

    @FunctionalInterface
    interface ServerProvider {
        GuiceJamesServer buildServer(Configuration configuration);
    }

    private final ImmutableList.Builder<GuiceModuleTestExtension> extensions;
    private final TemporaryFolderRegistrableExtension folderRegistrableExtension;
    private ServerProvider server;
    private Optional<ConfigurationProvider> configuration;

    JamesServerExtensionBuilder() {
        configuration = Optional.empty();
        extensions = ImmutableList.builder();
        folderRegistrableExtension = new TemporaryFolderRegistrableExtension();
    }

    public JamesServerExtensionBuilder extensions(GuiceModuleTestExtension... extensions) {
        this.extensions.add(extensions);
        return this;
    }

    public JamesServerExtensionBuilder extension(Supplier<GuiceModuleTestExtension> extension) {
        this.extensions.add(extension.get());
        return this;
    }

    public JamesServerExtensionBuilder extension(ExtensionProvider extension) {
        this.extensions.add(extension.buildExtension(createTmpDir()));
        return this;
    }

    public JamesServerExtensionBuilder configuration(ConfigurationProvider configuration) throws UncheckedIOException {
        this.configuration = Optional.of(configuration);
        return this;
    }

    public JamesServerExtensionBuilder server(ServerProvider server) {
        this.server = server;
        return this;
    }

    public JamesServerExtension build() {
        Preconditions.checkNotNull(server);
        ConfigurationProvider configuration = this.configuration.orElse(defaultConfigurationProvider());
        return new JamesServerExtension(buildAggregateJunitExtension(), () -> overrideServerWithExtensionsModules(configuration));
    }

    private ConfigurationProvider defaultConfigurationProvider() {
        return tmpDir ->
            Configuration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .build();
    }

    private File createTmpDir() {
        try {
            return folderRegistrableExtension.getTemporaryFolder().newFolder();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private AggregateJunitExtension buildAggregateJunitExtension() {
        ImmutableList<GuiceModuleTestExtension> extensions = this.extensions.build();
        return new AggregateJunitExtension(
            ImmutableList.<RegistrableExtension>builder()
                .addAll(extensions)
                .add(folderRegistrableExtension)
                .build());
    }

    private GuiceJamesServer overrideServerWithExtensionsModules(ConfigurationProvider configurationProvider) {
        ImmutableList<Module> modules = extensions.build().stream().map(x -> x.getModule()).collect(Guavate.toImmutableList());
        return server.buildServer(configurationProvider.buildConfiguration(createTmpDir())).overrideWith(modules);
    }

}
