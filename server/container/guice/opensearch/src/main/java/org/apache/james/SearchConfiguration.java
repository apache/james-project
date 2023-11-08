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

package org.apache.james;

import java.io.FileNotFoundException;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public class SearchConfiguration {
    public static final String SEARCH_CONFIGURATION_NAME = "search";

    public enum Implementation {
        Scanning("scanning"),
        OpenSearch("opensearch"),
        OpenSearchDisabled("opensearch-disabled");

        static Optional<Implementation> parse(String value) {
            return Stream.of(values())
                .filter(v -> v.name.equalsIgnoreCase(value))
                .findAny();
        }

        private final String name;

        Implementation(String name) {
            this.name = name;
        }
    }

    public static SearchConfiguration parse(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration(SEARCH_CONFIGURATION_NAME);
            return SearchConfiguration.from(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.debug("Could not find {} configuration file, enabling opensearch by default", SEARCH_CONFIGURATION_NAME);
            return openSearch();
        }
    }

    static SearchConfiguration from(Configuration configuration) throws ConfigurationException {
        String searchOption = configuration.getString("implementation", Implementation.OpenSearch.name());
        return Implementation.parse(searchOption)
            .map(SearchConfiguration::new)
            .orElseThrow(() -> new ConfigurationException(String.format("'implementation' parameter in '%s.properties' should be '%s' ",
                SEARCH_CONFIGURATION_NAME, Stream.of(Implementation.values())
                    .map(v -> v.name)
                    .collect(ImmutableList.toImmutableList()))));
    }

    public static SearchConfiguration scanning() {
        return new SearchConfiguration(Implementation.Scanning);
    }

    public static SearchConfiguration openSearch() {
        return new SearchConfiguration(Implementation.OpenSearch);
    }

    public static SearchConfiguration openSearchDisabled() {
        return new SearchConfiguration(Implementation.OpenSearchDisabled);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchConfiguration.class);


    private final Implementation implementation;

    public SearchConfiguration(Implementation implementation) {
        this.implementation = implementation;
    }

    public Implementation getImplementation() {
        return implementation;
    }
}
