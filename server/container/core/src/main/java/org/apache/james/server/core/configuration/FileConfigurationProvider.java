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

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.io.FileHandler;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.util.LoggingLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

public class FileConfigurationProvider implements ConfigurationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileConfigurationProvider.class);
    private static final String CONFIGURATION_FILE_SUFFIX = ".xml";

    public static final HierarchicalConfiguration<ImmutableNode> EMPTY_CONFIGURATION = new XMLConfiguration();

    public static XMLConfiguration getConfig(InputStream configStream) throws ConfigurationException {
        FileBasedConfigurationBuilder<XMLConfiguration> builder = new FileBasedConfigurationBuilder<>(XMLConfiguration.class)
            .configure(new Parameters()
                .xml()
                .setListDelimiterHandler(new DisabledListDelimiterHandler()));
        XMLConfiguration xmlConfiguration = builder.getConfiguration();
        FileHandler fileHandler = new FileHandler(xmlConfiguration);
        fileHandler.load(configStream);

        return xmlConfiguration;
    }
    
    private final FileSystem fileSystem;
    private final Configuration.ConfigurationPath configurationPrefix;

    public FileConfigurationProvider(FileSystem fileSystem, Configuration configuration) {
        this.fileSystem = fileSystem;
        this.configurationPrefix = configuration.configurationPath();
    }
    
    @Override
    public HierarchicalConfiguration<ImmutableNode> getConfiguration(String component, LoggingLevel loggingLevelOnError) throws ConfigurationException {
        Preconditions.checkNotNull(component);
        List<String> configPathParts = Splitter.on(".").splitToList(component);
        Preconditions.checkArgument(!configPathParts.isEmpty());

        Optional<InputStream> inputStream = retrieveConfigInputStream(configPathParts.get(0), loggingLevelOnError);
        if (inputStream.isPresent()) {
            return selectConfigurationPart(configPathParts,
                getConfig(inputStream.get()));
        }
        return EMPTY_CONFIGURATION;
    }

    private HierarchicalConfiguration<ImmutableNode> selectConfigurationPart(List<String> configPathParts, HierarchicalConfiguration<ImmutableNode> config) {
        return selectHierarchicalConfigPart(config, Iterables.skip(configPathParts, 1));
    }

    private Optional<InputStream> retrieveConfigInputStream(String configurationFileWithoutExtension, LoggingLevel loggingLevelOnError) throws ConfigurationException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(configurationFileWithoutExtension), "The configuration file name should not be empty or null");
        try {
            return Optional.of(
                fileSystem.getResource(configurationPrefix.asString() + configurationFileWithoutExtension + CONFIGURATION_FILE_SUFFIX));
        } catch (IOException e) {
            loggingLevelOnError.format(LOGGER, "Unable to locate configuration file {}" + CONFIGURATION_FILE_SUFFIX + ", assuming empty configuration", configurationFileWithoutExtension);
            return Optional.empty();
        }
    }

    private HierarchicalConfiguration<ImmutableNode> selectHierarchicalConfigPart(HierarchicalConfiguration<ImmutableNode> config, Iterable<String> configsPathParts) {
        HierarchicalConfiguration<ImmutableNode> currentConfig = config;
        for (String nextPathPart : configsPathParts) {
            currentConfig = currentConfig.configurationAt(nextPathPart);
        }
        return currentConfig;
    }

}
