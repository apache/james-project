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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.ProtocolConfigurationSanitizer;
import org.apache.james.RunArguments;
import org.apache.james.core.ConnectionDescriptionSupplier;
import org.apache.james.core.Disconnector;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.imap.ImapSuite;
import org.apache.james.imap.api.ConnectionCheckFactory;
import org.apache.james.imap.api.display.Localizer;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.DefaultMailboxTyper;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.MailboxTyper;
import org.apache.james.imap.decode.ImapCommandParser;
import org.apache.james.imap.decode.ImapCommandParserFactory;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.decode.base.AbstractImapCommandParser;
import org.apache.james.imap.decode.main.DefaultImapDecoder;
import org.apache.james.imap.decode.parser.ImapParserFactory;
import org.apache.james.imap.decode.parser.UidCommandParser;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.imap.encode.ImapResponseEncoder;
import org.apache.james.imap.encode.base.EndImapEncoder;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.encode.main.DefaultLocalizer;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.imap.processor.AuthenticateProcessor;
import org.apache.james.imap.processor.CapabilityImplementingProcessor;
import org.apache.james.imap.processor.CapabilityProcessor;
import org.apache.james.imap.processor.DefaultProcessor;
import org.apache.james.imap.processor.EnableProcessor;
import org.apache.james.imap.processor.NamespaceSupplier;
import org.apache.james.imap.processor.PermitEnableCapabilityProcessor;
import org.apache.james.imap.processor.SelectProcessor;
import org.apache.james.imap.processor.StatusProcessor;
import org.apache.james.imap.processor.base.AbstractProcessor;
import org.apache.james.imap.processor.base.UnknownRequestProcessor;
import org.apache.james.imap.processor.fetch.FetchProcessor;
import org.apache.james.imap.processor.sasl.ImapBearerTokenSaslAuthenticationServiceFactory;
import org.apache.james.imap.processor.sasl.ImapPasswordSaslAuthenticationServiceFactory;
import org.apache.james.imapserver.netty.IMAPHealthCheck;
import org.apache.james.imapserver.netty.IMAPServerFactory;
import org.apache.james.lifecycle.api.ConfigurationSanitizer;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.sasl.SaslAuthenticationServiceFactory;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismLoader;
import org.apache.james.protocols.api.sasl.SaslMechanismRegistry;
import org.apache.james.protocols.lib.netty.CertificateReloadable;
import org.apache.james.protocols.netty.Encryption;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.utils.ClassName;
import org.apache.james.utils.GuiceLoader;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.KeystoreCreator;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.ThrowingFunction;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class IMAPServerModule extends AbstractModule {
    private static Stream<Pair<Class, AbstractProcessor>> asPairStream(AbstractProcessor p) {
        return p.acceptableClasses()
            .stream().map(clazz -> Pair.of(clazz, p));
    }

    @Override
    protected void configure() {
        bind(Localizer.class).to(DefaultLocalizer.class);
        bind(UnpooledStatusResponseFactory.class).in(Scopes.SINGLETON);
        bind(StatusResponseFactory.class).to(UnpooledStatusResponseFactory.class);

        bind(CapabilityProcessor.class).in(Scopes.SINGLETON);
        bind(AuthenticateProcessor.class).in(Scopes.SINGLETON);
        bind(SelectProcessor.class).in(Scopes.SINGLETON);
        bind(StatusProcessor.class).in(Scopes.SINGLETON);
        bind(EnableProcessor.class).in(Scopes.SINGLETON);
        bind(DefaultImapSaslMechanismClassNamesProvider.class).to(JamesDefaultImapSaslMechanismClassNamesProvider.class);
        bind(NamespaceSupplier.class).to(NamespaceSupplier.Default.class).in(Scopes.SINGLETON);
        bind(PathConverter.Factory.class).to(PathConverter.Factory.Default.class).in(Scopes.SINGLETON);
        bind(MailboxTyper.class).to(DefaultMailboxTyper.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(ImapGuiceProbe.class);

        Multibinder.newSetBinder(binder(), CertificateReloadable.Factory.class).addBinding().to(IMAPServerFactory.class);
        bind(ConnectionCheckFactory.class).to(ConnectionCheckFactoryImpl.class);

        Multibinder.newSetBinder(binder(), HealthCheck.class).addBinding().to(IMAPHealthCheck.class);

        Multibinder.newSetBinder(binder(), Disconnector.class).addBinding().to(IMAPServerFactory.class);
        Multibinder.newSetBinder(binder(), ConnectionDescriptionSupplier.class).addBinding().to(IMAPServerFactory.class);
    }

    @Provides
    @Singleton
    IMAPServerFactory provideServerFactory(FileSystem fileSystem,
                                           GuiceLoader guiceLoader,
                                           SaslMechanismLoader saslMechanismLoader,
                                           Set<ImapSaslAuthenticationServiceFactoryProvider> saslAuthenticationServiceFactoryProviders,
                                           DefaultImapSaslMechanismClassNamesProvider defaultImapSaslMechanismClassNamesProvider,
                                           StatusResponseFactory statusResponseFactory,
                                           MetricFactory metricFactory,
                                           GaugeRegistry gaugeRegistry,
                                           ConnectionCheckFactory connectionCheckFactory,
                                           Encryption.Factory encryptionFactory) {
        IMAPServerFactory factory = new IMAPServerFactory(fileSystem, imapSuiteLoader(guiceLoader, saslMechanismLoader,
            saslAuthenticationServiceFactoryProviders, defaultImapSaslMechanismClassNamesProvider, statusResponseFactory), metricFactory, gaugeRegistry, connectionCheckFactory);
        factory.setEncryptionFactory(encryptionFactory);
        return factory;
    }

    DefaultProcessor provideClassImapProcessors(ImapPackage imapPackage, GuiceLoader guiceLoader,
                                                SaslMechanismRegistry saslMechanisms, StatusResponseFactory statusResponseFactory) {
        ImmutableMap<Class, ImapProcessor> processors = imapPackage.processors()
            .stream()
            .map(Throwing.function(guiceLoader::instantiate))
            .map(AbstractProcessor.class::cast)
            .map(processor -> configureSaslMechanisms(processor, saslMechanisms))
            .flatMap(IMAPServerModule::asPairStream)
            .collect(ImmutableMap.toImmutableMap(
                Pair::getLeft,
                Pair::getRight));

        Optional<EnableProcessor> enableProcessor = processors.values()
            .stream()
            .filter(EnableProcessor.class::isInstance)
            .map(EnableProcessor.class::cast)
            .findFirst();

        Optional<CapabilityProcessor> capabilityProcessor = processors.values()
            .stream()
            .filter(CapabilityProcessor.class::isInstance)
            .map(CapabilityProcessor.class::cast)
            .findFirst();

        enableProcessor.ifPresent(processor -> configureEnable(processor, processors));
        capabilityProcessor.ifPresent(processor -> configureCapability(processor, processors));

        return new DefaultProcessor(processors, new UnknownRequestProcessor(statusResponseFactory));
    }

    private AbstractProcessor configureSaslMechanisms(AbstractProcessor processor, SaslMechanismRegistry saslMechanisms) {
        if (processor instanceof AuthenticateProcessor authenticateProcessor) {
            authenticateProcessor.configureSaslMechanisms(saslMechanisms);
        }
        return processor;
    }

    private ImapPackage retrievePackages(GuiceLoader guiceLoader, HierarchicalConfiguration<ImmutableNode> configuration) {
        String[] imapPackages = configuration.getStringArray("imapPackages");

        ImmutableList<ImapPackage> packages = Optional.ofNullable(imapPackages)
            .stream()
            .flatMap(Arrays::stream)
            .map(ClassName::new)
            .map(Throwing.function(guiceLoader::instantiate))
            .map(ImapPackage.class::cast)
            .collect(ImmutableList.toImmutableList());

        if (packages.isEmpty()) {
            return ImapPackage.DEFAULT;
        }
        return ImapPackage.and(packages);
    }

    private SaslMechanismRegistry retrieveSaslMechanisms(SaslMechanismLoader saslMechanismLoader,
                                                         Set<ImapSaslAuthenticationServiceFactoryProvider> saslAuthenticationServiceFactoryProviders,
                                                         DefaultImapSaslMechanismClassNamesProvider defaultImapSaslMechanismClassNamesProvider,
                                                         HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        ImmutableList<String> mechanismClassNames = retrieveSaslMechanismClassNames(configuration, defaultImapSaslMechanismClassNamesProvider);
        ImmutableList<SaslMechanism> mechanisms = saslMechanismLoader.load(mechanismClassNames);
        ImmutableList<SaslAuthenticationServiceFactory<?>> saslAuthenticationServiceFactories =
            retrieveSaslAuthenticationServiceFactories(configuration, saslAuthenticationServiceFactoryProviders);
        return new SaslMechanismRegistry(mechanisms, saslAuthenticationServiceFactories);
    }

    ImmutableList<SaslAuthenticationServiceFactory<?>> retrieveSaslAuthenticationServiceFactories(HierarchicalConfiguration<ImmutableNode> configuration,
                                                                                                  Set<ImapSaslAuthenticationServiceFactoryProvider> providers) throws ConfigurationException {
        ImmutableList.Builder<SaslAuthenticationServiceFactory<?>> factories = ImmutableList.builder();
        for (ImapSaslAuthenticationServiceFactoryProvider provider : providers) {
            factories.addAll(provider.provide(configuration));
        }
        return factories.build();
    }

    ImmutableList<String> retrieveSaslMechanismClassNames(HierarchicalConfiguration<ImmutableNode> configuration,
                                                          DefaultImapSaslMechanismClassNamesProvider defaultImapSaslMechanismClassNamesProvider) throws ConfigurationException {
        if (!configuration.containsKey("auth.saslMechanisms")) {
            return defaultImapSaslMechanismClassNamesProvider.resolve(configuration);
        }

        ImmutableList<String> mechanismClassNames = Arrays.stream(configuration.getStringArray("auth.saslMechanisms"))
            .flatMap(value -> Arrays.stream(value.split(",")))
            .map(String::trim)
            .collect(ImmutableList.toImmutableList());

        if (mechanismClassNames.isEmpty() || mechanismClassNames.stream().anyMatch(StringUtils::isBlank)) {
            throw new ConfigurationException("auth.saslMechanisms must not be blank when configured");
        }
        return mechanismClassNames;
    }

    private ThrowingFunction<HierarchicalConfiguration<ImmutableNode>, ImapSuite> imapSuiteLoader(GuiceLoader guiceLoader,
                                                                                                  SaslMechanismLoader saslMechanismLoader,
                                                                                                  Set<ImapSaslAuthenticationServiceFactoryProvider> saslAuthenticationServiceFactoryProviders,
                                                                                                  DefaultImapSaslMechanismClassNamesProvider defaultImapSaslMechanismClassNamesProvider,
                                                                                                  StatusResponseFactory statusResponseFactory) {
        return configuration -> {
            ImapPackage imapPackage = retrievePackages(guiceLoader, configuration);
            SaslMechanismRegistry saslMechanisms = retrieveSaslMechanisms(saslMechanismLoader, saslAuthenticationServiceFactoryProviders,
                defaultImapSaslMechanismClassNamesProvider, configuration);
            DefaultProcessor processor = provideClassImapProcessors(imapPackage, guiceLoader, saslMechanisms, statusResponseFactory);
            ImapEncoder encoder = provideImapEncoder(imapPackage, guiceLoader);

            ImapParserFactory imapParserFactory = provideImapCommandParserFactory(imapPackage, guiceLoader);

            UidCommandParser uidParser = new UidCommandParser(imapParserFactory, statusResponseFactory);
            DefaultImapDecoder decoder = new DefaultImapDecoder(statusResponseFactory,
                    imapParserFactory.union(new ImapParserFactory(ImmutableMap.of(uidParser.getCommand().getName(), uidParser))));

            return new ImapSuite(decoder, encoder, processor);
        };
    }

    ImapDecoder provideImapDecoder(ImapCommandParserFactory imapCommandParserFactory, StatusResponseFactory statusResponseFactory) {
        return new DefaultImapDecoderFactory(imapCommandParserFactory, statusResponseFactory).buildImapDecoder();
    }

    ImapEncoder provideImapEncoder(ImapPackage imapPackage, GuiceLoader guiceLoader) {
        Stream<ImapResponseEncoder> encoders = imapPackage.encoders()
            .stream()
            .map(Throwing.function(guiceLoader::instantiate))
            .map(ImapResponseEncoder.class::cast);

        return new DefaultImapEncoderFactory.DefaultImapEncoder(encoders, new EndImapEncoder());
    }

    @ProvidesIntoSet
    ImapSaslAuthenticationServiceFactoryProvider provideDefaultImapSaslAuthenticationServiceFactoryProvider(MailboxManager mailboxManager) {
        return configuration -> ImmutableList.of(
            new ImapPasswordSaslAuthenticationServiceFactory(mailboxManager),
            new ImapBearerTokenSaslAuthenticationServiceFactory(mailboxManager));
    }

    @ProvidesIntoSet
    InitializationOperation configureImap(ConfigurationProvider configurationProvider, IMAPServerFactory imapServerFactory) {
        return InitilizationOperationBuilder
            .forClass(IMAPServerFactory.class)
            .init(() -> {
                imapServerFactory.configure(configurationProvider.getConfiguration("imapserver"));
                imapServerFactory.init();
            });
    }

    @Provides
    FetchProcessor.LocalCacheConfiguration provideFetchLocalCacheConfiguration(ConfigurationProvider configurationProvider) throws ConfigurationException {
            return FetchProcessor.LocalCacheConfiguration.from(configurationProvider.getConfiguration("imapserver"));
    }

    private void configureEnable(EnableProcessor enableProcessor, ImmutableMap<Class, ImapProcessor> processorMap) {
        processorMap.values().stream()
            .filter(PermitEnableCapabilityProcessor.class::isInstance)
            .map(PermitEnableCapabilityProcessor.class::cast)
            .forEach(enableProcessor::addProcessor);
    }

    private void configureCapability(CapabilityProcessor capabilityProcessor, ImmutableMap<Class, ImapProcessor> processorMap) {
        processorMap.values().stream()
            .filter(CapabilityImplementingProcessor.class::isInstance)
            .map(CapabilityImplementingProcessor.class::cast)
            .forEach(capabilityProcessor::addProcessor);
    }

    ImapParserFactory provideImapCommandParserFactory(ImapPackage imapPackage, GuiceLoader guiceLoader) {
        ImmutableMap<String, ImapCommandParser> decoders = imapPackage.decoders()
            .stream()
            .filter(className -> !className.equals(new ClassName(UidCommandParser.class.getName())))
            .map(Throwing.function(guiceLoader::instantiate))
            .map(AbstractImapCommandParser.class::cast)
            .collect(ImmutableMap.toImmutableMap(
                parser -> parser.getCommand().getName(),
                Function.identity()));

        return new ImapParserFactory(decoders);
    }

    @ProvidesIntoSet
    ConfigurationSanitizer configurationSanitizer(ConfigurationProvider configurationProvider, KeystoreCreator keystoreCreator,
                                                      FileSystem fileSystem, RunArguments runArguments) {
        return new ProtocolConfigurationSanitizer(configurationProvider, keystoreCreator, fileSystem, runArguments, "imapserver");
    }
}
