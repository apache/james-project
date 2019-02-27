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

import org.apache.commons.configuration.HierarchicalConfiguration;
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
        private final HierarchicalConfiguration configuration;

        public Item(List<Protocol> protocols, String classFqdn, HierarchicalConfiguration configuration) {
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

        public HierarchicalConfiguration getConfiguration() {
            return configuration;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MailRepositoryStoreConfiguration.class);

    public static MailRepositoryStoreConfiguration parse(HierarchicalConfiguration configuration) {
        return new MailRepositoryStoreConfiguration(
            retrieveRegisteredClassConfiguration(configuration)
            .stream()
            .map(MailRepositoryStoreConfiguration::readItem)
            .collect(Guavate.toImmutableList()));
    }

    private static List<HierarchicalConfiguration> retrieveRegisteredClassConfiguration(HierarchicalConfiguration configuration) {
        try {
            return configuration.configurationsAt("mailrepositories.mailrepository");
        } catch (Exception e) {
            LOGGER.warn("Could not process configuration. Skipping Mail Repository initialization.", e);
            return ImmutableList.of();
        }
    }

    private static Item readItem(HierarchicalConfiguration configuration) {
        String className = configuration.getString("[@class]");
        List<Protocol> protocolStream = Arrays.stream(configuration.getStringArray("protocols.protocol")).map(Protocol::new).collect(Guavate.toImmutableList());
        HierarchicalConfiguration extraConfig = extraConfig(configuration);

        return new Item(protocolStream, className, extraConfig);
    }

    private static HierarchicalConfiguration extraConfig(HierarchicalConfiguration configuration) {
        if (configuration.getKeys("config").hasNext()) {
            return configuration.configurationAt("config");
        }
        return new HierarchicalConfiguration();
    }


    private final List<Item> items;

    public MailRepositoryStoreConfiguration(List<Item> items) {
        this.items = items;
    }

    public List<Item> getItems() {
        return items;
    }
}
