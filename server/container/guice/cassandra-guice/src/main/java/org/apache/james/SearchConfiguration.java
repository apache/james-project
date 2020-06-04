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

import static org.apache.james.modules.mailbox.ElasticSearchMailboxModule.ELASTICSEARCH_CONFIGURATION_NAME;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchConfiguration {

    public enum Implementation {
        Scanning,
        ElasticSearch
    }

    public static SearchConfiguration parse(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration(ELASTICSEARCH_CONFIGURATION_NAME);
            return SearchConfiguration.from(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find {} configuration file, enabling elasticsearch by default", ELASTICSEARCH_CONFIGURATION_NAME);
            return elasticSearch();
        }
    }

    static SearchConfiguration from(Configuration configuration) {
        if (configuration.getBoolean("enabled", true)) {
            return elasticSearch();
        }
        return scanning();
    }

    public static SearchConfiguration scanning() {
        return new SearchConfiguration(Implementation.Scanning);
    }

    public static SearchConfiguration elasticSearch() {
        return new SearchConfiguration(Implementation.ElasticSearch);
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
