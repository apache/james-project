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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.mailetcontainer.api.MailetLoader;
import org.apache.james.mailetcontainer.api.MatcherLoader;
import org.apache.james.mailetcontainer.api.jmx.MailSpoolerMBean;
import org.apache.james.mailetcontainer.impl.JamesMailSpooler;
import org.apache.james.mailetcontainer.impl.JamesMailetContext;
import org.apache.james.mailetcontainer.impl.MatcherMailetPair;
import org.apache.james.mailetcontainer.impl.camel.CamelCompositeProcessor;
import org.apache.james.mailetcontainer.impl.camel.CamelMailetProcessor;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.transport.mailets.RemoveMimeHeader;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.ConfigurationPerformer;
import org.apache.james.utils.GuiceMailetLoader;
import org.apache.james.utils.GuiceMatcherLoader;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.MailetConfigurationOverride;
import org.apache.james.utils.SpoolerProbe;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetContext;
import org.apache.mailet.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class CamelMailetContainerModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(CamelMailetContainerModule.class);

    public static final TransportProcessorCheck.Impl BCC_Check = new TransportProcessorCheck.Impl(
        All.class,
        RemoveMimeHeader.class,
        pair -> pair.getMailet().getMailetConfig().getInitParameter("name").equals("bcc"),
        "Should be configured to remove Bcc header");

    @Override
    protected void configure() {
        bind(CamelCompositeProcessor.class).in(Scopes.SINGLETON);
        bind(MailProcessor.class).to(CamelCompositeProcessor.class);

        bind(JamesMailSpooler.class).in(Scopes.SINGLETON);
        bind(MailSpoolerMBean.class).to(JamesMailSpooler.class);

        bind(JamesMailetContext.class).in(Scopes.SINGLETON);
        bind(MailetContext.class).to(JamesMailetContext.class);

        bind(MailetLoader.class).to(GuiceMailetLoader.class);
        bind(MatcherLoader.class).to(GuiceMatcherLoader.class);

        Multibinder.newSetBinder(binder(), MailetConfigurationOverride.class);
        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(SpoolerProbe.class);
        Multibinder<ConfigurationPerformer> configurationPerformers = Multibinder.newSetBinder(binder(), ConfigurationPerformer.class);
        configurationPerformers.addBinding().to(MailetModuleConfigurationPerformer.class);
        configurationPerformers.addBinding().to(SpoolerStarter.class);
        configurationPerformers.addBinding().to(MailetContextConfigurationPerformer.class);

        Multibinder<CamelMailetContainerModule.TransportProcessorCheck> transportProcessorChecks = Multibinder.newSetBinder(binder(), CamelMailetContainerModule.TransportProcessorCheck.class);
        transportProcessorChecks.addBinding().toInstance(BCC_Check);
    }

    @Singleton
    @Provides
    public DefaultCamelContext provideCamelContext() {
        DefaultCamelContext camelContext = new DefaultCamelContext();
        camelContext.disableJMX();
        camelContext.setRegistry(new SimpleRegistry());
        return camelContext;
    }

    @Singleton
    public static class SpoolerStarter implements ConfigurationPerformer {
        private final CamelCompositeProcessor camelCompositeProcessor;
        private final JamesMailSpooler jamesMailSpooler;
        private final ConfigurationProvider configurationProvider;

        @Inject
        public SpoolerStarter(CamelCompositeProcessor camelCompositeProcessor, JamesMailSpooler jamesMailSpooler, ConfigurationProvider configurationProvider) {
            this.camelCompositeProcessor = camelCompositeProcessor;
            this.jamesMailSpooler = jamesMailSpooler;
            this.configurationProvider = configurationProvider;
        }

        @Override
        public void initModule() {
            jamesMailSpooler.setMailProcessor(camelCompositeProcessor);
            jamesMailSpooler.configure(getJamesSpoolerConfiguration());
            jamesMailSpooler.init();
        }

        private HierarchicalConfiguration getJamesSpoolerConfiguration() {
            try {
                return configurationProvider.getConfiguration("mailetcontainer")
                    .configurationAt("spooler");
            } catch (Exception e) {
                LOGGER.warn("Could not locate configuration for James Spooler. Assuming empty configuration for this component.");
                return new HierarchicalConfiguration();
            }
        }

        @Override
        public List<Class<? extends Startable>> forClasses() {
            return ImmutableList.of(JamesMailSpooler.class);
        }
    }

    @Singleton
    public static class MailetContextConfigurationPerformer implements ConfigurationPerformer {
        private final ConfigurationProvider configurationProvider;
        private final JamesMailetContext mailetContext;

        @Inject
        public MailetContextConfigurationPerformer(ConfigurationProvider configurationProvider, JamesMailetContext mailetContext) {
            this.configurationProvider = configurationProvider;
            this.mailetContext = mailetContext;
        }

        @Override
        public void initModule() {
            try {
                mailetContext.configure(getMailetContextConfiguration());
            } catch (ConfigurationException e) {
                throw new RuntimeException(e);
            }
        }

        private HierarchicalConfiguration getMailetContextConfiguration() {
            try {
                return configurationProvider.getConfiguration("mailetcontainer")
                    .configurationAt("context");
            } catch (Exception e) {
                LOGGER.warn("Could not locate configuration for Mailet context. Assuming empty configuration for this component.");
                return new HierarchicalConfiguration();
            }
        }

        @Override
        public List<Class<? extends Startable>> forClasses() {
            return ImmutableList.of(JamesMailetContext.class);
        }
    }

    @Singleton
    public static class MailetModuleConfigurationPerformer implements ConfigurationPerformer {

        private final ConfigurationProvider configurationProvider;
        private final CamelCompositeProcessor camelCompositeProcessor;
        private final DefaultProcessorsConfigurationSupplier defaultProcessorsConfigurationSupplier;
        private final Set<TransportProcessorCheck> transportProcessorCheckSet;
        private final DefaultCamelContext camelContext;

        @Inject
        public MailetModuleConfigurationPerformer(ConfigurationProvider configurationProvider,
                                                  CamelCompositeProcessor camelCompositeProcessor,
                                                  Set<TransportProcessorCheck> transportProcessorCheckSet,
                                                  DefaultProcessorsConfigurationSupplier defaultProcessorsConfigurationSupplier, DefaultCamelContext camelContext) {
            this.configurationProvider = configurationProvider;
            this.camelCompositeProcessor = camelCompositeProcessor;
            this.transportProcessorCheckSet = transportProcessorCheckSet;
            this.defaultProcessorsConfigurationSupplier = defaultProcessorsConfigurationSupplier;
            this.camelContext = camelContext;
        }

        @Override
        public void initModule() {
            try {
                configureProcessors(camelContext);
                checkProcessors();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void configureProcessors(DefaultCamelContext camelContext) throws Exception {
            camelCompositeProcessor.setCamelContext(camelContext);
            camelCompositeProcessor.configure(getProcessorConfiguration());
            camelCompositeProcessor.init();
        }

        private HierarchicalConfiguration getProcessorConfiguration() {
            try {
                return configurationProvider.getConfiguration("mailetcontainer")
                    .configurationAt("processors");
            } catch (Exception e) {
                LOGGER.warn("Could not load configuration for Processors. Fallback to default.");
                return defaultProcessorsConfigurationSupplier.getDefaultConfiguration();
            }
        }

        private void checkProcessors() throws ConfigurationException {
            MailProcessor mailProcessor = Optional.ofNullable(camelCompositeProcessor.getProcessor("transport"))
                .orElseThrow(() -> new RuntimeException("JMAP needs a transport processor"));
            if (mailProcessor instanceof CamelMailetProcessor) {
                List<MatcherMailetPair> matcherMailetPairs = ((CamelMailetProcessor) mailProcessor).getPairs();
                for (TransportProcessorCheck check : transportProcessorCheckSet) {
                    check.check(matcherMailetPairs);
                }
            } else {
                throw new RuntimeException("Can not perform checks as transport processor is not an instance of " + MailProcessor.class);
            }
        }

        @Override
        public List<Class<? extends Startable>> forClasses() {
            return ImmutableList.of(CamelCompositeProcessor.class);
        }
    }

    @FunctionalInterface
    public interface TransportProcessorCheck {
        void check(List<MatcherMailetPair> pairs) throws ConfigurationException;

        class Impl implements TransportProcessorCheck {
            private final Class<? extends Matcher> matcherClass;
            private final Class<? extends Mailet> mailetClass;
            private final Optional<Predicate<? super MatcherMailetPair>> additionalFilter;
            private final Optional<String> additionalErrorMessage;

            public Impl(Class<? extends Matcher> matcherClass, Class<? extends Mailet> mailetClass) {
                this(matcherClass, mailetClass, Optional.empty(), Optional.empty());
            }

            public Impl(Class<? extends Matcher> matcherClass, Class<? extends Mailet> mailetClass, Predicate<? super MatcherMailetPair> additionalFilter, String additionalErrorMessage) {
                this(matcherClass, mailetClass, Optional.of(additionalFilter), Optional.of(additionalErrorMessage));
            }

            private Impl(Class<? extends Matcher> matcherClass, Class<? extends Mailet> mailetClass, Optional<Predicate<? super MatcherMailetPair>> additionalFilter, Optional<String> additionalErrorMessage) {
                this.matcherClass = matcherClass;
                this.mailetClass = mailetClass;
                this.additionalFilter = additionalFilter;
                this.additionalErrorMessage = additionalErrorMessage;
            }

            @Override
            public void check(List<MatcherMailetPair> pairs) throws ConfigurationException {
                Preconditions.checkNotNull(pairs);
                pairs.stream()
                    .filter(pair -> pair.getMailet().getClass().equals(mailetClass))
                    .filter(pair -> pair.getMatcher().getClass().equals(matcherClass))
                    .filter(additionalFilter.orElse(any -> true))
                    .findAny()
                    .orElseThrow(() -> new ConfigurationException("Missing " + mailetClass.getName() + " in mailets configuration (mailetcontainer -> processors -> transport). " +
                        additionalErrorMessage.orElse("")));
            }
        }
    }

    public interface DefaultProcessorsConfigurationSupplier {
        HierarchicalConfiguration getDefaultConfiguration();
    }

}
