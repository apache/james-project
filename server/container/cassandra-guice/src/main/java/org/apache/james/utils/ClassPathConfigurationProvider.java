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

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ClassPathConfigurationProvider {

    private static final String CONFIGURATION_FILE_SUFFIX = ".xml";

    public HierarchicalConfiguration getConfiguration(String component) throws ConfigurationException {
        List<String> configPathParts = Arrays.asList(component.split("\\."));
        HierarchicalConfiguration config = getConfig(retrieveConfigInputStream(configPathParts.get(0)));
        return selectHierarchicalConfigPart(config, configPathParts.subList(1, configPathParts.size()));
    }

    private InputStream retrieveConfigInputStream(String configurationFileWithoutExtension) throws ConfigurationException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(configurationFileWithoutExtension), "The configuration file name should not be empty or null");
        return Optional.ofNullable(ClassLoader.getSystemResourceAsStream(configurationFileWithoutExtension + CONFIGURATION_FILE_SUFFIX))
            .orElseThrow(() -> new ConfigurationException("Unable to locate configuration file " + configurationFileWithoutExtension + CONFIGURATION_FILE_SUFFIX));
    }

    private XMLConfiguration getConfig(InputStream configStream) throws ConfigurationException {
        XMLConfiguration config = new XMLConfiguration();
        config.setDelimiterParsingDisabled(true);
        config.setAttributeSplittingDisabled(true);
        config.load(configStream);
        return config;
    }

    private HierarchicalConfiguration selectHierarchicalConfigPart(HierarchicalConfiguration config, List<String> configsPathParts) {
        HierarchicalConfiguration currentConfig = config;
        for (String nextPathPart : configsPathParts) {
            currentConfig = currentConfig.configurationAt(nextPathPart);
        }
        return currentConfig;
    }

}
