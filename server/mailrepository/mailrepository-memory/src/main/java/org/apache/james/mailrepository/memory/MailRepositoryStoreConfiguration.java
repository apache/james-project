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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.mailrepository.api.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class MailRepositoryStoreConfiguration {
    public static class Item {
        private final List<Protocol> protocols;
        private final String classFqdn;
        private final HierarchicalConfiguration<ImmutableNode> configuration;

        public Item(List<Protocol> protocols, String classFqdn, HierarchicalConfiguration<ImmutableNode> configuration) {
            Preconditions.checkNotNull(protocols);
            Preconditions.checkNotNull(classFqdn);
            Preconditions.checkNotNull(configuration);

            this.protocols = protocols;
            this.classFqdn = classFqdn;
            this.configuration = configuration;
        }

        public List<Protocol> getProtocols() {
            return protocols;
        }

        public String getClassFqdn() {
            return classFqdn;
        }

        public HierarchicalConfiguration<ImmutableNode> getConfiguration() {
            return configuration;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MailRepositoryStoreConfiguration.class);

    public static MailRepositoryStoreConfiguration forItems(Item... items) {
        return forItems(ImmutableList.copyOf(items));
    }

    public static MailRepositoryStoreConfiguration forItems(List<Item> items) {
        Optional<Protocol> defaultProtocol = computeDefaultProtocol(items);

        return new MailRepositoryStoreConfiguration(items, defaultProtocol);
    }

    public static MailRepositoryStoreConfiguration parse(HierarchicalConfiguration<ImmutableNode> configuration) {
        ImmutableList<Item> items = retrieveRegisteredClassConfiguration(configuration)
            .stream()
            .map(MailRepositoryStoreConfiguration::readItem)
            .collect(Guavate.toImmutableList());

        Optional<Protocol> defaultProtocol =
            Optional.ofNullable(configuration.getString("defaultProtocol", null)).map(Protocol::new)
            .or(() -> computeDefaultProtocol(items));

        return new MailRepositoryStoreConfiguration(items, defaultProtocol);
    }

    private static List<HierarchicalConfiguration<ImmutableNode>> retrieveRegisteredClassConfiguration(HierarchicalConfiguration<ImmutableNode> configuration) {
        try {
            return configuration.configurationsAt("mailrepositories.mailrepository");
        } catch (Exception e) {
            LOGGER.warn("Could not process configuration. Skipping Mail Repository initialization.", e);
            return ImmutableList.of();
        }
    }

    static Optional<Protocol> computeDefaultProtocol(List<Item> items) {
        return items.stream()
            .flatMap(item -> item.getProtocols().stream())
            .findFirst();
    }

    private static Item readItem(HierarchicalConfiguration<ImmutableNode> configuration) {
        String className = configuration.getString("[@class]");
        List<Protocol> protocolStream = Arrays.stream(configuration.getStringArray("protocols.protocol")).map(Protocol::new).collect(Guavate.toImmutableList());
        HierarchicalConfiguration<ImmutableNode> extraConfig = extraConfig(configuration);

        return new Item(protocolStream, className, extraConfig);
    }

    private static HierarchicalConfiguration<ImmutableNode> extraConfig(HierarchicalConfiguration<ImmutableNode> configuration) {
        if (configuration.getKeys("config").hasNext()) {
            return configuration.configurationAt("config");
        }
        return new BaseHierarchicalConfiguration();
    }


    private final List<Item> items;
    private final Optional<Protocol> defaultProtocol;

    public MailRepositoryStoreConfiguration(List<Item> items, Optional<Protocol> defaultProtocol) {
        this.items = items;
        this.defaultProtocol = defaultProtocol;
    }

    public List<Item> getItems() {
        return items;
    }

    public Optional<Protocol> getDefaultProtocol() {
        return defaultProtocol;
    }
}
