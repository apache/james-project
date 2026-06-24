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

import java.util.Arrays;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.ProtocolConfigurationSanitizer;
import org.apache.james.RunArguments;
import org.apache.james.core.ConnectionDescriptionSupplier;
import org.apache.james.core.Disconnector;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.lifecycle.api.ConfigurationSanitizer;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismFactory;
import org.apache.james.protocols.lib.handler.ProtocolHandlerLoader;
import org.apache.james.protocols.lib.netty.CertificateReloadable;
import org.apache.james.protocols.netty.Encryption;
import org.apache.james.protocols.sasl.BuiltInSaslMechanismFactories;
import org.apache.james.protocols.sasl.JamesSaslAuthenticator;
import org.apache.james.protocols.sasl.OauthBearerSaslMechanismFactory;
import org.apache.james.protocols.sasl.PlainSaslMechanismFactory;
import org.apache.james.protocols.sasl.XOauth2SaslMechanismFactory;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.smtpserver.SendMailHandler;
import org.apache.james.smtpserver.netty.SMTPServer.AuthAnnouncementConfiguration;
import org.apache.james.smtpserver.netty.SMTPServerFactory;
import org.apache.james.smtpserver.netty.SMTPServerFactory.SmtpSaslMechanismLoader;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.GuiceSaslMechanismResolver;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.KeystoreCreator;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class SMTPServerModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new JSPFModule());
        bind(SaslAuthenticator.class).to(JamesSaslAuthenticator.class);

        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(SmtpGuiceProbe.class);

        Multibinder.newSetBinder(binder(), CertificateReloadable.Factory.class).addBinding().to(SMTPServerFactory.class);
        Multibinder.newSetBinder(binder(), Disconnector.class).addBinding().to(SMTPServerFactory.class);
        Multibinder.newSetBinder(binder(), ConnectionDescriptionSupplier.class).addBinding().to(SMTPServerFactory.class);
    }

    @Provides
    @Singleton
    SMTPServerFactory provideSmtpServerFactory(DNSService dns,
                                               ProtocolHandlerLoader protocolHandlerLoader,
                                               FileSystem fileSystem,
                                               MetricFactory metricFactory,
                                               SmtpSaslMechanismLoader saslMechanismLoader,
                                               SaslAuthenticator saslAuthenticator,
                                               Encryption.Factory encryptionFactory) {
        SMTPServerFactory smtpServerFactory = new SMTPServerFactory(dns, protocolHandlerLoader, fileSystem,
            metricFactory, saslMechanismLoader, saslAuthenticator);
        smtpServerFactory.setEncryptionFactory(encryptionFactory);
        return smtpServerFactory;
    }

    @Provides
    @Singleton
    @SmtpDefaultSaslMechanismFactories
    ImmutableList<SaslMechanismFactory> provideDefaultSmtpSaslMechanismFactories(OauthBearerSaslMechanismFactory oauthBearer,
                                                                                 XOauth2SaslMechanismFactory xoauth2) {
        return ImmutableList.of(new PlainSaslMechanismFactory(AuthAnnouncementConfiguration.REQUIRE_SSL_DEFAULT), oauthBearer, xoauth2);
    }

    @Provides
    @Singleton
    SmtpSaslMechanismLoader provideSmtpSaslMechanismLoader(GuiceSaslMechanismResolver saslMechanismResolver,
                                                           @SmtpDefaultSaslMechanismFactories ImmutableList<SaslMechanismFactory> defaultSaslMechanismFactories) {
        return configuration -> retrieveSaslMechanisms(saslMechanismResolver, defaultSaslMechanismFactories, configuration);
    }

    private ImmutableList<SaslMechanism> retrieveSaslMechanisms(GuiceSaslMechanismResolver saslMechanismResolver,
                                                                ImmutableList<SaslMechanismFactory> defaultSaslMechanismFactories,
                                                                HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        ImmutableList<String> mechanismFactoryClassNames = retrieveSaslMechanismFactoryClassNames(configuration);
        ImmutableList<SaslMechanismFactory> enabledDefaultFactories =
            BuiltInSaslMechanismFactories.enabledForServer(defaultSaslMechanismFactories, configuration);
        return saslMechanismResolver.resolve(mechanismFactoryClassNames, enabledDefaultFactories, configuration);
    }

    ImmutableList<String> retrieveSaslMechanismFactoryClassNames(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        if (!configuration.containsKey("auth.saslMechanisms")) {
            return ImmutableList.of();
        }

        ImmutableList<String> mechanismFactoryClassNames = Arrays.stream(configuration.getStringArray("auth.saslMechanisms"))
            .flatMap(value -> Arrays.stream(value.split(",")))
            .map(String::trim)
            .collect(ImmutableList.toImmutableList());

        if (mechanismFactoryClassNames.isEmpty() || mechanismFactoryClassNames.stream().anyMatch(StringUtils::isBlank)) {
            throw new ConfigurationException("auth.saslMechanisms must not be blank when configured");
        }
        return mechanismFactoryClassNames;
    }

    @ProvidesIntoSet
    InitializationOperation configureSmtp(ConfigurationProvider configurationProvider,
                                        SMTPServerFactory smtpServerFactory,
                                        SendMailHandler sendMailHandler) {
        return InitilizationOperationBuilder
            .forClass(SMTPServerFactory.class)
            .init(() -> {
                smtpServerFactory.configure(configurationProvider.getConfiguration("smtpserver"));
                smtpServerFactory.init();
                sendMailHandler.init(null);
            });
    }

    @ProvidesIntoSet
    ConfigurationSanitizer configurationSanitizer(ConfigurationProvider configurationProvider, KeystoreCreator keystoreCreator,
                                                      FileSystem fileSystem, RunArguments runArguments) {
        return new ProtocolConfigurationSanitizer(configurationProvider, keystoreCreator, fileSystem, runArguments, "smtpserver");
    }
}
