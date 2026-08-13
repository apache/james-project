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
import org.apache.james.managesieveserver.netty.ManageSieveServerFactory;
import org.apache.james.managesieveserver.netty.ManageSieveServerFactory.ManageSieveSaslMechanismLoader;
import org.apache.james.protocols.api.sasl.SaslMechanismFactory;
import org.apache.james.protocols.lib.netty.CertificateReloadable;
import org.apache.james.protocols.sasl.BuiltInSaslMechanismFactories;
import org.apache.james.protocols.sasl.OauthBearerSaslMechanismFactory;
import org.apache.james.protocols.sasl.PlainSaslMechanismFactory;
import org.apache.james.protocols.sasl.XOauth2SaslMechanismFactory;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.util.LoggingLevel;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.GuiceSaslMechanismResolver;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.KeystoreCreator;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class ManageSieveServerModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new SieveModule());
        bind(ManageSieveServerFactory.class).in(Scopes.SINGLETON);
        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(SieveProbeImpl.class);

        Multibinder.newSetBinder(binder(), CertificateReloadable.Factory.class).addBinding().to(ManageSieveServerFactory.class);
    }

    @Provides
    @Singleton
    @ManageSieveDefaultSaslMechanismFactories
    ImmutableList<SaslMechanismFactory> provideDefaultManageSieveSaslMechanismFactories(OauthBearerSaslMechanismFactory oauthBearer,
                                                                                        XOauth2SaslMechanismFactory xoauth2) {
        return ImmutableList.of(new PlainSaslMechanismFactory(false), oauthBearer, xoauth2);
    }

    @Provides
    @Singleton
    ManageSieveSaslMechanismLoader provideManageSieveSaslMechanismLoader(GuiceSaslMechanismResolver resolver,
                                                                         @ManageSieveDefaultSaslMechanismFactories ImmutableList<SaslMechanismFactory> defaultFactories) {
        return configuration -> resolver.resolve(
            ManageSieveServerFactory.retrieveSaslMechanismFactoryClassNames(configuration),
            BuiltInSaslMechanismFactories.enabledForServer(defaultFactories, configuration),
            configuration);
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
