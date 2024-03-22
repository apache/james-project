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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.CombinedConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.mailrepository.api.Initializable;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryLoader;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.MailRepositoryUrlStore;
import org.apache.james.mailrepository.api.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.ThrowingFunction;

public class MemoryMailRepositoryStore implements MailRepositoryStore, Startable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryMailRepositoryStore.class);

    private final MailRepositoryUrlStore urlStore;
    private final ConcurrentMap<MailRepositoryUrl, MailRepository> destinationToRepositoryAssociations;
    private final Map<Protocol, String> protocolToClass;
    private final MailRepositoryLoader mailRepositoryLoader;
    private final Map<Protocol, HierarchicalConfiguration<ImmutableNode>> perProtocolMailRepositoryDefaultConfiguration;
    private final MailRepositoryStoreConfiguration configuration;

    @Inject
    public MemoryMailRepositoryStore(MailRepositoryUrlStore urlStore, MailRepositoryLoader mailRepositoryLoader, MailRepositoryStoreConfiguration configuration) {
        this.urlStore = urlStore;
        this.mailRepositoryLoader = mailRepositoryLoader;
        this.configuration = configuration;
        this.destinationToRepositoryAssociations = new ConcurrentHashMap<>();
        this.protocolToClass = new HashMap<>();
        this.perProtocolMailRepositoryDefaultConfiguration = new HashMap<>();
    }

    @Override
    public Optional<Protocol> defaultProtocol() {
        return configuration.getDefaultProtocol();
    }

    public void init() throws Exception {
        LOGGER.info("JamesMailStore init... {}", this);

        for (MailRepositoryStoreConfiguration.Item item : configuration.getItems()) {
            initEntry(item);
        }
    }

    private void initEntry(MailRepositoryStoreConfiguration.Item item) {
        for (Protocol protocol : item.getProtocols()) {
            protocolToClass.put(protocol, item.getClassFqdn());
            perProtocolMailRepositoryDefaultConfiguration.put(protocol, item.getConfiguration());
        }
    }

    @Override
    public Stream<MailRepositoryUrl> getUrls() {
        return urlStore.listDistinct();
    }

    @Override
    public Optional<MailRepository> get(MailRepositoryUrl url) {
        return Optional.of(url)
            .filter(urlStore::contains)
            .map(this::select);
    }

    @Override
    public Stream<MailRepository> getByPath(MailRepositoryPath path) {
        return urlStore.listDistinct()
            .filter(url -> url.getPath().equals(path))
            .map(this::select);
    }

    @Override
    public MailRepository select(MailRepositoryUrl mailRepositoryUrl) {
        return destinationToRepositoryAssociations.computeIfAbsent(mailRepositoryUrl,
            Throwing.function(this::createNewMailRepository).sneakyThrow());
    }

    private MailRepository createNewMailRepository(MailRepositoryUrl mailRepositoryUrl) throws MailRepositoryStoreException {
        MailRepository newMailRepository = retrieveMailRepository(mailRepositoryUrl);
        initializeNewRepository(newMailRepository, createRepositoryCombinedConfig(mailRepositoryUrl));
        urlStore.add(mailRepositoryUrl);

        return newMailRepository;
    }

    private CombinedConfiguration createRepositoryCombinedConfig(MailRepositoryUrl mailRepositoryUrl) {
        CombinedConfiguration config = new CombinedConfiguration();

        Optional.ofNullable(perProtocolMailRepositoryDefaultConfiguration.get(mailRepositoryUrl.getProtocol()))
            .ifPresent(config::addConfiguration);

        config.setProperty("[@destinationURL]", mailRepositoryUrl.asString());
        return config;
    }

    private void initializeNewRepository(MailRepository mailRepository, CombinedConfiguration config) throws MailRepositoryStoreException {
        try {
            if (mailRepository instanceof Configurable) {
                ((Configurable) mailRepository).configure(config);
            }
            if (mailRepository instanceof Initializable) {
                ((Initializable) mailRepository).init();
            }
        } catch (Exception e) {
            throw new UnsupportedRepositoryStoreException("Cannot init mail repository", e);
        }
    }

    private MailRepository retrieveMailRepository(MailRepositoryUrl mailRepositoryUrl) throws MailRepositoryStoreException {
        Protocol protocol = mailRepositoryUrl.getProtocol();
        Optional<String> fullyQualifiedClass = Optional.ofNullable(protocolToClass.get(protocol));

        ThrowingFunction<String, MailRepository> fqcnToMailRepository = className -> mailRepositoryLoader.load(className, mailRepositoryUrl);

        return fullyQualifiedClass
            .map(Throwing.function(fqcnToMailRepository).sneakyThrow())
            .orElseThrow(() -> new UnsupportedRepositoryStoreException("No Mail Repository associated with " + protocol.getValue()));
    }
}
