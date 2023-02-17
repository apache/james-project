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

import javax.inject.Provider;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.filesystem.api.FileSystem;
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
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.utils.ClassName;
import org.apache.james.utils.GuiceGenericLoader;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.github.fge.lambdas.Throwing;
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
        bind(ImapProcessor.class).to(DefaultProcessor.class);

        bind(CapabilityProcessor.class).in(Scopes.SINGLETON);
        bind(AuthenticateProcessor.class).in(Scopes.SINGLETON);
        bind(SelectProcessor.class).in(Scopes.SINGLETON);
        bind(EnableProcessor.class).in(Scopes.SINGLETON);
        bind(MailboxTyper.class).to(DefaultMailboxTyper.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(ImapGuiceProbe.class);
    }

    @Provides
    @Singleton
    IMAPServerFactory provideServerFactory(FileSystem fileSystem, Provider<ImapDecoder> decoder, Provider<ImapEncoder> encoder, Provider<ImapProcessor> processor,
                                        MetricFactory metricFactory, GaugeRegistry gaugeRegistry) {
        return new IMAPServerFactory(fileSystem, decoder, encoder, processor, metricFactory, gaugeRegistry);
    }

    @Provides
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

    @Provides
    @Singleton
    ImapPackage providePackage(ConfigurationProvider configurationProvider, GuiceGenericLoader loader) {
        try {
            String[] imapPackages = configurationProvider.getConfiguration("imapserver")
                .getStringArray("imapPackages");

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
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    @Provides
    ImapDecoder provideImapDecoder(ImapCommandParserFactory imapCommandParserFactory, StatusResponseFactory statusResponseFactory) {
        return new DefaultImapDecoder(statusResponseFactory, imapCommandParserFactory);
    }

    @Provides
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

    @Provides
    @Singleton
    ImapCommandParserFactory provideImapCommandParserFactory(ImapPackage imapPackage, GuiceGenericLoader loader) {
        ImmutableMap<String, ImapCommandParser> decoders = imapPackage.decoders()
            .stream()
            .map(Throwing.function(loader::instantiate))
            .map(AbstractImapCommandParser.class::cast)
            .collect(ImmutableMap.toImmutableMap(
                parser -> parser.getCommand().getName(),
                Function.identity()));

        return new ImapParserFactory(decoders);
    }
}