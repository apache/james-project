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

package org.apache.james.mailrepository.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.configuration.CombinedConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryProvider;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.MailRepositoryUrlStore;
import org.apache.james.mailrepository.api.Protocol;
import org.apache.james.repository.api.Initializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

public class MemoryMailRepositoryStore implements MailRepositoryStore, Configurable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryMailRepositoryStore.class);

    private final MailRepositoryUrlStore urlStore;
    private final Set<MailRepositoryProvider> mailRepositories;
    private final ConcurrentMap<MailRepositoryUrl, MailRepository> destinationToRepositoryAssociations;
    private final Map<Protocol, MailRepositoryProvider> protocolToRepositoryProvider;
    private final Map<Protocol, HierarchicalConfiguration> perProtocolMailRepositoryDefaultConfiguration;
    private MailRepositoryStoreConfiguration configuration;

    @Inject
    public MemoryMailRepositoryStore(MailRepositoryUrlStore urlStore, Set<MailRepositoryProvider> mailRepositories) {
        this.urlStore = urlStore;
        this.mailRepositories = mailRepositories;
        this.destinationToRepositoryAssociations = new ConcurrentHashMap<>();
        this.protocolToRepositoryProvider = new HashMap<>();
        this.perProtocolMailRepositoryDefaultConfiguration = new HashMap<>();
    }

    @Override
    public Stream<MailRepositoryUrl> getUrls() {
        return urlStore.listDistinct();
    }

    @Override
    public void configure(HierarchicalConfiguration configuration) {
        this.configuration = MailRepositoryStoreConfiguration.parse(configuration);
    }

    public void configure(MailRepositoryStoreConfiguration configuration) {
        this.configuration = configuration;
    }

    public void init() throws Exception {
        LOGGER.info("JamesMailStore init... {}", this);

        for (MailRepositoryStoreConfiguration.Item item : configuration.getItems()) {
            initEntry(item);
        }
    }


    @Override
    public Optional<MailRepository> get(MailRepositoryUrl url) {
        if (urlStore.contains(url)) {
            return Optional.of(select(url));
        }
        return Optional.empty();
    }

    @Override
    public Stream<MailRepository> getByPath(MailRepositoryPath path) {
        return urlStore.listDistinct()
            .filter(url -> url.getPath().equals(path))
            .map(this::select);
    }

    @Override
    public MailRepository select(MailRepositoryUrl mailRepositoryUrl) {
        return Optional.ofNullable(destinationToRepositoryAssociations.get(mailRepositoryUrl))
            .orElseGet(Throwing.supplier(
                () -> createNewMailRepository(mailRepositoryUrl))
                .sneakyThrow());
    }

    private MailRepository createNewMailRepository(MailRepositoryUrl mailRepositoryUrl) throws MailRepositoryStoreException {
        MailRepository newMailRepository = retrieveMailRepository(mailRepositoryUrl);
        urlStore.add(mailRepositoryUrl);
        newMailRepository = initializeNewRepository(newMailRepository, createRepositoryCombinedConfig(mailRepositoryUrl));
        MailRepository previousRepository = destinationToRepositoryAssociations.putIfAbsent(mailRepositoryUrl, newMailRepository);
        return Optional.ofNullable(previousRepository)
            .orElse(newMailRepository);
    }

    private void initEntry(MailRepositoryStoreConfiguration.Item item) throws ConfigurationException {
        String className = item.getClassFqdn();

        MailRepositoryProvider usedMailRepository = mailRepositories.stream()
            .filter(mailRepositoryProvider -> mailRepositoryProvider.canonicalName().equals(className))
            .findAny()
            .orElseThrow(() -> new ConfigurationException("MailRepository " + className + " has not been registered"));

        for (Protocol protocol : item.getProtocols()) {
            protocolToRepositoryProvider.put(protocol, usedMailRepository);
            perProtocolMailRepositoryDefaultConfiguration.put(protocol, item.getConfiguration());
        }
    }

    private CombinedConfiguration createRepositoryCombinedConfig(MailRepositoryUrl mailRepositoryUrl) {
        CombinedConfiguration config = new CombinedConfiguration();
        HierarchicalConfiguration defaultProtocolConfig = perProtocolMailRepositoryDefaultConfiguration.get(mailRepositoryUrl.getProtocol());
        if (defaultProtocolConfig != null) {
            config.addConfiguration(defaultProtocolConfig);
        }
        DefaultConfigurationBuilder builder = new DefaultConfigurationBuilder();
        builder.addProperty("[@destinationURL]", mailRepositoryUrl.asString());
        config.addConfiguration(builder);
        return config;
    }

    private MailRepository initializeNewRepository(MailRepository mailRepository, CombinedConfiguration config) throws MailRepositoryStoreException {
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

    private MailRepository retrieveMailRepository(MailRepositoryUrl mailRepositoryUrl) throws MailRepositoryStoreException {
        Protocol protocol = mailRepositoryUrl.getProtocol();
        return Optional.ofNullable(protocolToRepositoryProvider.get(protocol))
            .orElseThrow(() -> new MailRepositoryStoreException("No Mail Repository associated with " + protocol.getValue()))
            .provide(mailRepositoryUrl);
    }

}
