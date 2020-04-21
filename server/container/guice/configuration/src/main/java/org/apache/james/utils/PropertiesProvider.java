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

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.filesystem.api.FileSystem;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class PropertiesProvider {

    private static final char COMMA = ',';
    private static final String COMMA_STRING = ",";

    private final FileSystem fileSystem;
    private final String configurationPrefix;

    @Inject
    public PropertiesProvider(FileSystem fileSystem, org.apache.james.server.core.configuration.Configuration configuration) {
        this.fileSystem = fileSystem;
        this.configurationPrefix = configuration.configurationPath();
    }

    public Configuration getConfigurations(String... filenames) throws FileNotFoundException, ConfigurationException {
        File file = Arrays.stream(filenames)
            .map(this::getConfigurationFile)
            .flatMap(Optional::stream)
            .findFirst()
            .orElseThrow(() -> new FileNotFoundException(Joiner.on(",").join(filenames) + " not found"));

        return getConfiguration(file);
    }

    public Configuration getConfiguration(String fileName) throws FileNotFoundException, ConfigurationException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(fileName));

        File file = getConfigurationFile(fileName)
            .orElseThrow(() -> new FileNotFoundException(fileName));

        return getConfiguration(file);
    }

    private Configuration getConfiguration(File propertiesFile) throws ConfigurationException {
        FileBasedConfigurationBuilder<FileBasedConfiguration> builder = new FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration.class)
            .configure(new Parameters()
                .fileBased()
                .setListDelimiterHandler(new DefaultListDelimiterHandler(COMMA))
                .setFile(propertiesFile));

        return new DelegatedPropertiesConfiguration(COMMA_STRING, builder.getConfiguration());
    }

    private Optional<File> getConfigurationFile(String fileName) {
        try {
            return Optional.of(fileSystem.getFile(configurationPrefix + fileName + ".properties"))
                .filter(File::exists);
        } catch (FileNotFoundException e) {
            return Optional.empty();
        }
    }
}