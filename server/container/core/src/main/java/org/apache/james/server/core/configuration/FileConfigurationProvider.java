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

package org.apache.james.server.core.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.james.filesystem.api.FileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

public class FileConfigurationProvider implements ConfigurationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileConfigurationProvider.class);
    private static final String CONFIGURATION_FILE_SUFFIX = ".xml";
    private static final char SEMICOLON = ';';

    public static final HierarchicalConfiguration EMTY_CONFIGURATION = new HierarchicalConfiguration();

    public static XMLConfiguration getConfig(InputStream configStream) throws ConfigurationException {
        XMLConfiguration config = new XMLConfiguration();
        config.setListDelimiter(SEMICOLON);
        config.setDelimiterParsingDisabled(true);
        config.setAttributeSplittingDisabled(true);
        config.load(configStream);
        return config;
    }
    
    private final FileSystem fileSystem;
    private final String configurationPrefix;

    public FileConfigurationProvider(FileSystem fileSystem, Configuration configuration) {
        this.fileSystem = fileSystem;
        this.configurationPrefix = configuration.configurationPath();
    }
    
    @Override
    public HierarchicalConfiguration getConfiguration(String component) throws ConfigurationException {
        Preconditions.checkNotNull(component);
        List<String> configPathParts = Splitter.on(".").splitToList(component);
        Preconditions.checkArgument(!configPathParts.isEmpty());

        Optional<InputStream> inputStream = retrieveConfigInputStream(configPathParts.get(0));
        if (inputStream.isPresent()) {
            return selectConfigurationPart(configPathParts,
                getConfig(inputStream.get()));
        }
        return EMTY_CONFIGURATION;
    }

    private HierarchicalConfiguration selectConfigurationPart(List<String> configPathParts, HierarchicalConfiguration config) {
        return selectHierarchicalConfigPart(config, Iterables.skip(configPathParts, 1));
    }

    private Optional<InputStream> retrieveConfigInputStream(String configurationFileWithoutExtension) throws ConfigurationException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(configurationFileWithoutExtension), "The configuration file name should not be empty or null");
        try {
            return Optional.of(
                fileSystem.getResource(configurationPrefix + configurationFileWithoutExtension + CONFIGURATION_FILE_SUFFIX));
        } catch (IOException e) {
            LOGGER.warn("Unable to locate configuration file {}" + CONFIGURATION_FILE_SUFFIX + ", assuming empty configuration", configurationFileWithoutExtension);
            return Optional.empty();
        }
    }

    private HierarchicalConfiguration selectHierarchicalConfigPart(HierarchicalConfiguration config, Iterable<String> configsPathParts) {
        HierarchicalConfiguration currentConfig = config;
        for (String nextPathPart : configsPathParts) {
            currentConfig = currentConfig.configurationAt(nextPathPart);
        }
        return currentConfig;
    }

}
