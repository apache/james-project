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

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.mailetcontainer.AutomaticallySentMailDetectorImpl;
import org.apache.james.mailetcontainer.api.LocalResources;
import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.mailetcontainer.api.MailetLoader;
import org.apache.james.mailetcontainer.api.MatcherLoader;
import org.apache.james.mailetcontainer.api.jmx.MailSpoolerMBean;
import org.apache.james.mailetcontainer.impl.CompositeProcessorImpl;
import org.apache.james.mailetcontainer.impl.JamesMailSpooler;
import org.apache.james.mailetcontainer.impl.JamesMailetContext;
import org.apache.james.mailetcontainer.impl.LocalResourcesImpl;
import org.apache.james.mailetcontainer.impl.MailetProcessorImpl;
import org.apache.james.mailetcontainer.impl.MatcherMailetPair;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.transport.mailets.RemoveMimeHeader;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.GuiceMailetLoader;
import org.apache.james.utils.GuiceMatcherLoader;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.MailetConfigurationOverride;
import org.apache.james.utils.SpoolerProbe;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetContext;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.AutomaticallySentMailDetector;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class MailetContainerModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailetContainerModule.class);
    private static final boolean MAILET_CONTAINER_CHECK_ENABLED = Boolean.parseBoolean(System.getProperty("james.mailet.container.check.enabled", "true"));

    public static final ProcessorsCheck.Impl BCC_Check = new ProcessorsCheck.Impl(
            "transport",
            All.class,
            RemoveMimeHeader.class,
            pair -> {
                if (pair.getMailet() instanceof GenericMailet genericMailet) {
                    return Arrays
                            .asList(genericMailet.getMailetConfig().getInitParameter("name").toLowerCase().split(","))
                            .contains("bcc");
                } else {
                    return false;
                }
            },
            "Should be configured to remove Bcc header");

    @Override
    protected void configure() {
        bind(CompositeProcessorImpl.class).in(Scopes.SINGLETON);
        bind(MailProcessor.class).to(CompositeProcessorImpl.class);

        bind(JamesMailSpooler.class).in(Scopes.SINGLETON);
        bind(MailSpoolerMBean.class).to(JamesMailSpooler.class);

        bind(JamesMailetContext.class).in(Scopes.SINGLETON);
        bind(MailetContext.class).to(JamesMailetContext.class);

        bind(LocalResourcesImpl.class).in(Scopes.SINGLETON);
        bind(LocalResources.class).to(LocalResourcesImpl.class);

        bind(AutomaticallySentMailDetectorImpl.class).in(Scopes.SINGLETON);
        bind(AutomaticallySentMailDetector.class).to(AutomaticallySentMailDetectorImpl.class);

        bind(MailetLoader.class).to(GuiceMailetLoader.class);
        bind(MatcherLoader.class).to(GuiceMatcherLoader.class);

        Multibinder.newSetBinder(binder(), MailetConfigurationOverride.class);
        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(SpoolerProbe.class);
        Multibinder<InitializationOperation> initialisationOperations = Multibinder.newSetBinder(binder(), InitializationOperation.class);
        initialisationOperations.addBinding().to(MailetModuleInitializationOperation.class);

        Multibinder<ProcessorsCheck> transportProcessorChecks = Multibinder.newSetBinder(binder(), ProcessorsCheck.class);
        transportProcessorChecks.addBinding().toInstance(BCC_Check);
    }

    @Provides
    @Singleton
    public JamesMailSpooler.Configuration spoolerConfiguration(MailRepositoryStore mailRepositoryStore, ConfigurationProvider configurationProvider) {
        HierarchicalConfiguration<ImmutableNode> conf = getJamesSpoolerConfiguration(configurationProvider);
        return JamesMailSpooler.Configuration.from(mailRepositoryStore, conf);
    }

    private HierarchicalConfiguration<ImmutableNode> getJamesSpoolerConfiguration(ConfigurationProvider configurationProvider) {
        try {
            return configurationProvider.getConfiguration("mailetcontainer")
                    .configurationAt("spooler");
        } catch (Exception e) {
            LOGGER.warn("Could not locate configuration for James Spooler. Assuming empty configuration for this component.");
            return new BaseHierarchicalConfiguration();
        }
    }

    @ProvidesIntoSet
    InitializationOperation initMailetContext(ConfigurationProvider configurationProvider, JamesMailetContext mailetContext) {
        return InitilizationOperationBuilder
                .forClass(JamesMailetContext.class)
                .init(() -> mailetContext.configure(getMailetContextConfiguration(configurationProvider)));
    }

    @VisibleForTesting
    HierarchicalConfiguration<ImmutableNode> getMailetContextConfiguration(ConfigurationProvider configurationProvider) throws ConfigurationException {
        HierarchicalConfiguration<ImmutableNode> mailetContainerConfiguration = configurationProvider.getConfiguration("mailetcontainer");
        try {
            return mailetContainerConfiguration.configurationAt("context");
        } catch (ConfigurationRuntimeException e) {
            LOGGER.warn("Could not locate configuration for Mailet context. Assuming empty configuration for this component.");
            return new BaseHierarchicalConfiguration();
        }
    }

    @Singleton
    public static class MailetModuleInitializationOperation implements InitializationOperation {
        private final ConfigurationProvider configurationProvider;
        private final CompositeProcessorImpl compositeProcessorImpl;
        private final DefaultProcessorsConfigurationSupplier defaultProcessorsConfigurationSupplier;
        private final Set<ProcessorsCheck> processorsCheckSet;
        private final JamesMailSpooler jamesMailSpooler;
        private final JamesMailSpooler.Configuration spoolerConfiguration;

        @Inject
        public MailetModuleInitializationOperation(ConfigurationProvider configurationProvider,
                                                   CompositeProcessorImpl compositeProcessorImpl,
                                                   Set<ProcessorsCheck> processorsCheckSet,
                                                   DefaultProcessorsConfigurationSupplier defaultProcessorsConfigurationSupplier,
                                                   JamesMailSpooler jamesMailSpooler,
                                                   JamesMailSpooler.Configuration spoolerConfiguration) {
            this.configurationProvider = configurationProvider;
            this.compositeProcessorImpl = compositeProcessorImpl;
            this.processorsCheckSet = processorsCheckSet;
            this.defaultProcessorsConfigurationSupplier = defaultProcessorsConfigurationSupplier;
            this.jamesMailSpooler = jamesMailSpooler;
            this.spoolerConfiguration = spoolerConfiguration;
        }

        @Override
        public void initModule() throws Exception {
            configureProcessors();
            if (MAILET_CONTAINER_CHECK_ENABLED) {
                checkProcessors();
            }
        }

        private void configureProcessors() throws Exception {
            compositeProcessorImpl.configure(getProcessorConfiguration());
            compositeProcessorImpl.init();

            jamesMailSpooler.configure(spoolerConfiguration);
            jamesMailSpooler.init();
        }

        @VisibleForTesting
        HierarchicalConfiguration<ImmutableNode> getProcessorConfiguration() throws ConfigurationException {
            HierarchicalConfiguration<ImmutableNode> mailetContainerConfiguration = configurationProvider.getConfiguration("mailetcontainer");
            try {
                return mailetContainerConfiguration.configurationAt("processors");
            } catch (ConfigurationRuntimeException e) {
                LOGGER.warn("Could not load configuration for Processors. Fallback to default.");
                return defaultProcessorsConfigurationSupplier.getDefaultConfiguration();
            }
        }

        private void checkProcessors() throws ConfigurationException {
            ImmutableListMultimap<String, MatcherMailetPair> processors = Arrays.stream(compositeProcessorImpl.getProcessorStates())
                    .flatMap(state -> {
                        MailProcessor processor = compositeProcessorImpl.getProcessor(state);
                        if (processor instanceof MailetProcessorImpl) {
                            MailetProcessorImpl camelProcessor = (MailetProcessorImpl) processor;
                            return camelProcessor.getPairs().stream()
                                    .map(pair -> Pair.of(state, pair));
                        } else {
                            throw new RuntimeException("Can not perform checks as transport processor is not an instance of " + MailProcessor.class);
                        }
                    })
                    .collect(ImmutableListMultimap.toImmutableListMultimap(
                            Pair::getKey,
                            Pair::getValue));
            for (ProcessorsCheck check : processorsCheckSet) {
                check.check(processors);
            }
        }

        @Override
        public Class<? extends Startable> forClass() {
            return CompositeProcessorImpl.class;
        }
    }

    @FunctionalInterface
    public interface ProcessorsCheck {
        static ProcessorsCheck noCheck() {
            return new ProcessorsCheck() {
                @Override
                public void check(Multimap<String, MatcherMailetPair> processors) {

                }
            };
        }

        void check(Multimap<String, MatcherMailetPair> processors) throws ConfigurationException;

        class Or implements ProcessorsCheck {
            public static ProcessorsCheck of(ProcessorsCheck... checks) {
                return new Or(ImmutableSet.copyOf(checks));
            }

            private final Set<ProcessorsCheck> checks;

            public Or(Set<ProcessorsCheck> checks) {
                Preconditions.checkArgument(!checks.isEmpty());
                this.checks = checks;
            }

            @Override
            public void check(Multimap<String, MatcherMailetPair> processors) throws ConfigurationException {
                ImmutableList<ConfigurationException> failures = checks.stream().flatMap(check -> {
                    try {
                        check.check(processors);
                        return Stream.empty();
                    } catch (ConfigurationException e) {
                        return Stream.of(e);
                    }
                }).collect(ImmutableList.toImmutableList());

                if (failures.size() == checks.size()) {
                    throw failures.get(0);
                }
            }
        }

        class Impl implements ProcessorsCheck {
            private final String processorName;
            private final Class<? extends Matcher> matcherClass;
            private final Class<? extends Mailet> mailetClass;
            private final Optional<Predicate<? super MatcherMailetPair>> additionalFilter;
            private final Optional<String> additionalErrorMessage;

            public Impl(String processorName, Class<? extends Matcher> matcherClass, Class<? extends Mailet> mailetClass) {
                this(processorName, matcherClass, mailetClass, Optional.empty(), Optional.empty());
            }

            public Impl(String processorName, Class<? extends Matcher> matcherClass, Class<? extends GenericMailet> mailetClass, Predicate<? super MatcherMailetPair> additionalFilter, String additionalErrorMessage) {
                this(processorName, matcherClass, mailetClass, Optional.of(additionalFilter), Optional.of(additionalErrorMessage));
            }

            private Impl(String processorName, Class<? extends Matcher> matcherClass, Class<? extends Mailet> mailetClass, Optional<Predicate<? super MatcherMailetPair>> additionalFilter, Optional<String> additionalErrorMessage) {
                this.processorName = processorName;
                this.matcherClass = matcherClass;
                this.mailetClass = mailetClass;
                this.additionalFilter = additionalFilter;
                this.additionalErrorMessage = additionalErrorMessage;
            }

            @Override
            public void check(Multimap<String, MatcherMailetPair> processors) throws ConfigurationException {
                Collection<MatcherMailetPair> pairs = processors.get(processorName);
                if (pairs == null) {
                    throw new ConfigurationException(processorName + " is missing");
                }
                Preconditions.checkNotNull(pairs);
                pairs.stream()
                        .filter(pair -> pair.getMailet().getClass().equals(mailetClass))
                        .filter(pair -> pair.getMatcher().getClass().equals(matcherClass))
                        .filter(additionalFilter.orElse(any -> true))
                        .findAny()
                        .orElseThrow(() -> new ConfigurationException("Missing " + mailetClass.getName() + " in mailets configuration (mailetcontainer -> processors -> " + processorName + "). " +
                                additionalErrorMessage.orElse("")));
            }
        }
    }

    public interface DefaultProcessorsConfigurationSupplier {
        HierarchicalConfiguration<ImmutableNode> getDefaultConfiguration();
    }

}
