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

package org.apache.james.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.configuration.CombinedConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.repository.api.Initializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class InMemoryMailRepositoryStore implements MailRepositoryStore, Configurable {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryMailRepositoryStore.class);

    private final Set<MailRepositoryProvider> mailRepositories;
    private final ConcurrentMap<String, MailRepository> destinationToRepositoryAssociations;
    private final Map<String, MailRepositoryProvider> protocolToRepositoryProvider;
    private final Map<String, HierarchicalConfiguration> perProtocolMailRepositoryDefaultConfiguration;
    private HierarchicalConfiguration configuration;

    @Inject
    public InMemoryMailRepositoryStore(Set<MailRepositoryProvider> mailRepositories) {
        this.mailRepositories = mailRepositories;
        this.destinationToRepositoryAssociations = new ConcurrentHashMap<>();
        this.protocolToRepositoryProvider = new HashMap<>();
        this.perProtocolMailRepositoryDefaultConfiguration = new HashMap<>();
    }

    @Override
    public List<String> getUrls() {
        return ImmutableList.copyOf(destinationToRepositoryAssociations.keySet());
    }

    @Override
    public void configure(HierarchicalConfiguration configuration) throws ConfigurationException {
        this.configuration = configuration;
    }

    public void init() throws Exception {
        LOGGER.info("JamesMailStore init... {}", this);
        List<HierarchicalConfiguration> registeredClasses = retrieveRegisteredClassConfiguration();
        for (HierarchicalConfiguration registeredClass : registeredClasses) {
            readConfigurationEntry(registeredClass);
        }
    }

    private List<HierarchicalConfiguration> retrieveRegisteredClassConfiguration() {
        try {
            return configuration.configurationsAt("mailrepositories.mailrepository");
        } catch (Exception e) {
            LOGGER.warn("Could not process configuration. Skipping Mail Repository initialization.", e);
            return ImmutableList.of();
        }
    }

    @Override
    public Optional<MailRepository> get(String url) throws MailRepositoryStoreException {
        return Optional.ofNullable(destinationToRepositoryAssociations.get(url));
    }

    @Override
    public MailRepository select(String destination) throws MailRepositoryStoreException {
        MailRepository mailRepository = destinationToRepositoryAssociations.get(destination);
        if (mailRepository != null) {
            return mailRepository;
        }
        String protocol = retrieveProtocol(destination);
        mailRepository = retrieveMailRepository(protocol);
        mailRepository = initialiseNewRepository(mailRepository, createRepositoryCombinedConfig(destination, protocol));
        destinationToRepositoryAssociations.putIfAbsent(destination, mailRepository);
        return mailRepository;
    }

    private void readConfigurationEntry(HierarchicalConfiguration repositoryConfiguration) throws ConfigurationException {
        String className = repositoryConfiguration.getString("[@class]");
        MailRepositoryProvider usedMailRepository = mailRepositories.stream()
            .filter(mailRepositoryProvider -> mailRepositoryProvider.canonicalName().equals(className))
            .findAny()
            .orElseThrow(() -> new ConfigurationException("MailRepository " + className + " has not been registered"));
        for (String protocol : repositoryConfiguration.getStringArray("protocols.protocol")) {
            protocolToRepositoryProvider.put(protocol, usedMailRepository);
            registerRepositoryDefaultConfiguration(repositoryConfiguration, protocol);
        }
    }

    private void registerRepositoryDefaultConfiguration(HierarchicalConfiguration repositoryConfiguration, String protocol) {
        HierarchicalConfiguration defConf = null;
        if (repositoryConfiguration.getKeys("config").hasNext()) {
            defConf = repositoryConfiguration.configurationAt("config");
        }
        if (defConf != null) {
            perProtocolMailRepositoryDefaultConfiguration.put(protocol, defConf);
        }
    }

    private CombinedConfiguration createRepositoryCombinedConfig(String destination, String protocol) {
        final CombinedConfiguration config = new CombinedConfiguration();
        HierarchicalConfiguration defaultProtocolConfig = perProtocolMailRepositoryDefaultConfiguration.get(protocol);
        if (defaultProtocolConfig != null) {
            config.addConfiguration(defaultProtocolConfig);
        }
        DefaultConfigurationBuilder builder = new DefaultConfigurationBuilder();
        builder.addProperty("[@destinationURL]", destination);
        config.addConfiguration(builder);
        return config;
    }

    private MailRepository initialiseNewRepository(MailRepository mailRepository, CombinedConfiguration config) throws MailRepositoryStoreException {
        try {
            if (mailRepository instanceof Configurable) {
                ((Configurable) mailRepository).configure(config);
            }
            if (mailRepository instanceof Initializable) {
                ((Initializable) mailRepository).init();
            }
            return mailRepository;
        } catch (Exception e) {
            throw new MailRepositoryStoreException("Cannot init mail repository", e);
        }
    }

    private MailRepository retrieveMailRepository(String protocol) throws MailRepositoryStoreException {
        MailRepositoryProvider repositoryProvider = protocolToRepositoryProvider.get(protocol);
        if (repositoryProvider == null) {
            throw new MailRepositoryStoreException("No Mail Repository associated with " + protocol);
        }
        return repositoryProvider.get();
    }

    private String retrieveProtocol(String destination) throws MailRepositoryStoreException {
        int protocolSeparatorPosition = destination.indexOf(':');
        if (protocolSeparatorPosition == -1) {
            throw new MailRepositoryStoreException("Destination is malformed. Must be a valid URL: " + destination);
        }
        return destination.substring(0, protocolSeparatorPosition);
    }

}
