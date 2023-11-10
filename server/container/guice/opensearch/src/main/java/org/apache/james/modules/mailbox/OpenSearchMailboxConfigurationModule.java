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

package org.apache.james.modules.mailbox;

import java.io.FileNotFoundException;
import java.util.Set;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.opensearch.OpenSearchConfiguration;
import org.apache.james.mailbox.opensearch.OpenSearchMailboxConfiguration;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex.SearchOverride;
import org.apache.james.utils.ClassName;
import org.apache.james.utils.GuiceGenericLoader;
import org.apache.james.utils.NamingScheme;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class OpenSearchMailboxConfigurationModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchMailboxConfigurationModule.class);
    public static final String OPENSEARCH_CONFIGURATION_NAME = "opensearch";


    @Provides
    Set<SearchOverride> provideSearchOverrides(GuiceGenericLoader loader, OpenSearchConfiguration configuration) {
        return configuration.getSearchOverrides()
            .stream()
            .map(ClassName::new)
            .map(Throwing.function(loader.<SearchOverride>withNamingSheme(NamingScheme.IDENTITY)::instantiate))
            .peek(routes -> LOGGER.info("Loading Search override {}", routes.getClass().getCanonicalName()))
            .collect(ImmutableSet.toImmutableSet());
    }

    @Provides
    @Singleton
    private OpenSearchConfiguration getOpenSearchConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration(OPENSEARCH_CONFIGURATION_NAME);
            return OpenSearchConfiguration.fromProperties(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + OPENSEARCH_CONFIGURATION_NAME + " configuration file. Using {}:{} as contact point",
                OpenSearchConfiguration.LOCALHOST, OpenSearchConfiguration.DEFAULT_PORT);
            return OpenSearchConfiguration.DEFAULT_CONFIGURATION;
        }
    }

    @Provides
    @Singleton
    private OpenSearchMailboxConfiguration getOpenSearchMailboxConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration(OPENSEARCH_CONFIGURATION_NAME);
            return OpenSearchMailboxConfiguration.fromProperties(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + OPENSEARCH_CONFIGURATION_NAME + " configuration file. Providing a default OPENSearchMailboxConfiguration");
            return OpenSearchMailboxConfiguration.DEFAULT_CONFIGURATION;
        }
    }
}
