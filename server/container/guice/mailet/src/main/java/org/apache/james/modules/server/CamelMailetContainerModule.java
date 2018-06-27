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

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.mailetcontainer.api.MailetLoader;
import org.apache.james.mailetcontainer.api.MatcherLoader;
import org.apache.james.mailetcontainer.api.jmx.MailSpoolerMBean;
import org.apache.james.mailetcontainer.impl.JamesMailSpooler;
import org.apache.james.mailetcontainer.impl.JamesMailetContext;
import org.apache.james.mailetcontainer.impl.MatcherMailetPair;
import org.apache.james.mailetcontainer.impl.camel.CamelCompositeProcessor;
import org.apache.james.mailetcontainer.impl.camel.CamelMailetProcessor;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.transport.mailets.RemoveMimeHeader;
import org.apache.james.transport.matchers.All;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.utils.ConfigurationPerformer;
import org.apache.james.utils.GuiceMailetLoader;
import org.apache.james.utils.GuiceMatcherLoader;
import org.apache.james.utils.MailetConfigurationOverride;
import org.apache.mailet.MailetContext;
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

    @Override
    protected void configure() {
        bind(CamelCompositeProcessor.class).in(Scopes.SINGLETON);
        bind(MailProcessor.class).to(CamelCompositeProcessor.class);

        bind(JamesMailSpooler.class).in(Scopes.SINGLETON);
        bind(MailSpoolerMBean.class).to(JamesMailSpooler.class);

        bind(MailetContext.class).to(JamesMailetContext.class);

        bind(MailetLoader.class).to(GuiceMailetLoader.class);
        bind(MatcherLoader.class).to(GuiceMatcherLoader.class);

        Multibinder.newSetBinder(binder(), MailetConfigurationOverride.class);

        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(MailetModuleConfigurationPerformer.class);

        Multibinder<CamelMailetContainerModule.TransportProcessorCheck> transportProcessorChecks = Multibinder.newSetBinder(binder(), CamelMailetContainerModule.TransportProcessorCheck.class);
        transportProcessorChecks.addBinding().to(BccMailetCheck.class);
    }

    @Provides
    @Singleton
    private JamesMailetContext provideMailetContext(MailQueueFactory<?> mailQueueFactory,
                                                    DNSService dns,
                                                    UsersRepository localusers,
                                                    DomainList domains) {
        JamesMailetContext jamesMailetContext = new JamesMailetContext();
        jamesMailetContext.setDNSService(dns);
        jamesMailetContext.retrieveRootMailQueue(mailQueueFactory);
        jamesMailetContext.setUsersRepository(localusers);
        jamesMailetContext.setDomainList(domains);
        return jamesMailetContext;
    }

    @Singleton
    public static class MailetModuleConfigurationPerformer implements ConfigurationPerformer {

        private final ConfigurationProvider configurationProvider;
        private final CamelCompositeProcessor camelCompositeProcessor;
        private final JamesMailSpooler jamesMailSpooler;
        private final JamesMailetContext mailetContext;
        private final MailQueueFactory<?> mailQueueFactory;
        private final DefaultProcessorsConfigurationSupplier defaultProcessorsConfigurationSupplier;
        private final Set<TransportProcessorCheck> transportProcessorCheckSet;

        @Inject
        public MailetModuleConfigurationPerformer(ConfigurationProvider configurationProvider,
                                                CamelCompositeProcessor camelCompositeProcessor,
                                                JamesMailSpooler jamesMailSpooler,
                                                JamesMailetContext mailetContext,
                                                MailQueueFactory<?> mailQueueFactory,
                                                Set<TransportProcessorCheck> transportProcessorCheckSet,
                                                DefaultProcessorsConfigurationSupplier defaultProcessorsConfigurationSupplier) {
            this.configurationProvider = configurationProvider;
            this.camelCompositeProcessor = camelCompositeProcessor;
            this.jamesMailSpooler = jamesMailSpooler;
            this.mailetContext = mailetContext;
            this.mailQueueFactory = mailQueueFactory;
            this.transportProcessorCheckSet = transportProcessorCheckSet;
            this.defaultProcessorsConfigurationSupplier = defaultProcessorsConfigurationSupplier;
        }

        @Override
        public void initModule() {
            try {
                DefaultCamelContext camelContext = new DefaultCamelContext();
                camelContext.disableJMX();
                camelContext.setRegistry(new SimpleRegistry());
                configureProcessors(camelContext);
                checkProcessors();
                configureJamesSpooler();
                configureMailetContext();
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

        private void configureJamesSpooler() throws ConfigurationException {
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

        private void configureMailetContext() throws ConfigurationException {
            mailetContext.configure(getMailetContextConfiguration());
            mailetContext.retrieveRootMailQueue(mailQueueFactory);
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
        public List<Class<? extends Configurable>> forClasses() {
            return ImmutableList.of(CamelCompositeProcessor.class, JamesMailSpooler.class, JamesMailetContext.class);
        }
    }

    @FunctionalInterface
    public interface TransportProcessorCheck {
        void check(List<MatcherMailetPair> pairs) throws ConfigurationException;
    }
    
    public static class BccMailetCheck implements CamelMailetContainerModule.TransportProcessorCheck {
        @Override
        public void check(List<MatcherMailetPair> pairs) throws ConfigurationException {
            Preconditions.checkNotNull(pairs);
            pairs.stream()
                .filter(pair -> pair.getMailet().getClass().equals(RemoveMimeHeader.class))
                .filter(pair -> pair.getMatcher().getClass().equals(All.class))
                .filter(pair -> pair.getMailet().getMailetConfig().getInitParameter("name").equals("bcc"))
                .findAny()
                .orElseThrow(() -> new ConfigurationException("Missing RemoveMimeHeader in mailets configuration (mailetcontainer -> processors -> transport). Should be configured to remove Bcc header"));
        }
    }

    public interface DefaultProcessorsConfigurationSupplier {
        HierarchicalConfiguration getDefaultConfiguration();
    }

}
