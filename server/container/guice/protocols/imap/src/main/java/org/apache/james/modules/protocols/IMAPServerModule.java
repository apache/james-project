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
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.ProtocolConfigurationSanitizer;
import org.apache.james.RunArguments;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.imap.ImapSuite;
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
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.imap.processor.AuthenticateProcessor;
import org.apache.james.imap.processor.CapabilityImplementingProcessor;
import org.apache.james.imap.processor.CapabilityProcessor;
import org.apache.james.imap.processor.DefaultProcessor;
import org.apache.james.imap.processor.EnableProcessor;
import org.apache.james.imap.processor.PermitEnableCapabilityProcessor;
import org.apache.james.imap.processor.SelectProcessor;
import org.apache.james.imap.processor.base.AbstractProcessor;
import org.apache.james.imap.processor.base.UnknownRequestProcessor;
import org.apache.james.imapserver.netty.IMAPServerFactory;
import org.apache.james.lifecycle.api.ConfigurationSanitizer;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.lib.netty.CertificateReloadable;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.utils.ClassName;
import org.apache.james.utils.GuiceGenericLoader;
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
        bind(EnableProcessor.class).in(Scopes.SINGLETON);
        bind(MailboxTyper.class).to(DefaultMailboxTyper.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(ImapGuiceProbe.class);

        Multibinder.newSetBinder(binder(), CertificateReloadable.Factory.class).addBinding().to(IMAPServerFactory.class);
    }

    @Provides
    @Singleton
    IMAPServerFactory provideServerFactory(FileSystem fileSystem,
                                           GuiceGenericLoader loader,
                                           StatusResponseFactory statusResponseFactory,
                                           MetricFactory metricFactory,
                                           GaugeRegistry gaugeRegistry) {
        return new IMAPServerFactory(fileSystem, imapSuiteLoader(loader, statusResponseFactory), metricFactory, gaugeRegistry);
    }

    DefaultProcessor provideClassImapProcessors(ImapPackage imapPackage, GuiceGenericLoader loader, StatusResponseFactory statusResponseFactory) {
        ImmutableMap<Class, ImapProcessor> processors = imapPackage.processors()
            .stream()
            .map(Throwing.function(loader::instantiate))
            .map(AbstractProcessor.class::cast)
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

    private ImapPackage retrievePackages(GuiceGenericLoader loader, HierarchicalConfiguration<ImmutableNode> configuration) {
        String[] imapPackages = configuration.getStringArray("imapPackages");

        ImmutableList<ImapPackage> packages = Optional.ofNullable(imapPackages)
            .stream()
            .flatMap(Arrays::stream)
            .map(ClassName::new)
            .map(Throwing.function(loader::instantiate))
            .map(ImapPackage.class::cast)
            .collect(ImmutableList.toImmutableList());

        if (packages.isEmpty()) {
            return ImapPackage.DEFAULT;
        }
        return ImapPackage.and(packages);
    }

    private ThrowingFunction<HierarchicalConfiguration<ImmutableNode>, ImapSuite> imapSuiteLoader(GuiceGenericLoader loader,
                                                                                                  StatusResponseFactory statusResponseFactory) {
        return configuration -> {
            ImapPackage imapPackage = retrievePackages(loader, configuration);
            DefaultProcessor processor = provideClassImapProcessors(imapPackage, loader, statusResponseFactory);
            ImapEncoder encoder = provideImapEncoder(imapPackage, loader);

            ImapParserFactory imapParserFactory = provideImapCommandParserFactory(imapPackage, loader);

            UidCommandParser uidParser = new UidCommandParser(imapParserFactory, statusResponseFactory);
            DefaultImapDecoder decoder = new DefaultImapDecoder(statusResponseFactory,
                    imapParserFactory.union(new ImapParserFactory(ImmutableMap.of(uidParser.getCommand().getName(), uidParser))));

            return new ImapSuite(decoder, encoder, processor);
        };
    }

    ImapDecoder provideImapDecoder(ImapCommandParserFactory imapCommandParserFactory, StatusResponseFactory statusResponseFactory) {
        return new DefaultImapDecoder(statusResponseFactory, imapCommandParserFactory);
    }

    ImapEncoder provideImapEncoder(ImapPackage imapPackage, GuiceGenericLoader loader) {
        Stream<ImapResponseEncoder> encoders = imapPackage.encoders()
            .stream()
            .map(Throwing.function(loader::instantiate))
            .map(ImapResponseEncoder.class::cast);

        return new DefaultImapEncoderFactory.DefaultImapEncoder(encoders, new EndImapEncoder());
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

    ImapParserFactory provideImapCommandParserFactory(ImapPackage imapPackage, GuiceGenericLoader loader) {
        ImmutableMap<String, ImapCommandParser> decoders = imapPackage.decoders()
            .stream()
            .filter(className -> !className.equals(new ClassName(UidCommandParser.class.getName())))
            .map(Throwing.function(loader::instantiate))
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