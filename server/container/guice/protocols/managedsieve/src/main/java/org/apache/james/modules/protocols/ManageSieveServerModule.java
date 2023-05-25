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
package org.apache.james.modules.protocols;

import org.apache.james.ProtocolConfigurationSanitizer;
import org.apache.james.RunArguments;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.lifecycle.api.ConfigurationSanitizer;
import org.apache.james.managesieve.api.commands.CoreCommands;
import org.apache.james.managesieve.core.CoreProcessor;
import org.apache.james.managesieveserver.netty.ManageSieveServerFactory;
import org.apache.james.protocols.lib.netty.AbstractServerFactory;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.util.LoggingLevel;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.KeystoreCreator;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class ManageSieveServerModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new SieveModule());
        bind(ManageSieveServerFactory.class).in(Scopes.SINGLETON);
        bind(CoreCommands.class).to(CoreProcessor.class);
        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(SieveProbeImpl.class);

        Multibinder.newSetBinder(binder(), AbstractServerFactory.class).addBinding().to(ManageSieveServerFactory.class);
    }

    @ProvidesIntoSet
    InitializationOperation configureManageSieve(ConfigurationProvider configurationProvider, ManageSieveServerFactory manageSieveServerFactory) {
        return InitilizationOperationBuilder
            .forClass(ManageSieveServerFactory.class)
            .init(() -> {
                manageSieveServerFactory.configure(configurationProvider.getConfiguration("managesieveserver", LoggingLevel.INFO));
                manageSieveServerFactory.init();
            });
    }

    @ProvidesIntoSet
    ConfigurationSanitizer configurationSanitizer(ConfigurationProvider configurationProvider, KeystoreCreator keystoreCreator,
                                                      FileSystem fileSystem, RunArguments runArguments) {
        return new ProtocolConfigurationSanitizer(configurationProvider, keystoreCreator, fileSystem, runArguments, "managesieveserver");
    }
}