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
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.server.core.configuration.Configuration.ConfigurationPath;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class PropertiesProvider {
    public static class MissingConfigurationFile extends RuntimeException {
        public MissingConfigurationFile(String message) {
            super(message);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("org.apache.james.CONFIGURATION");
    private static final char COMMA = ',';

    private static boolean failOnMissingConfiguration() {
        return Boolean.valueOf(System.getProperty("james.fail.on.missing.configuration", "false"));
    }

    @VisibleForTesting
    public static PropertiesProvider forTesting() {
        org.apache.james.server.core.configuration.Configuration configuration = org.apache.james.server.core.configuration.Configuration.builder()
            .workingDirectory("../")
            .configurationFromClasspath()
            .build();
        FileSystemImpl fileSystem = new FileSystemImpl(configuration.directories());

        return new PropertiesProvider(fileSystem, configuration.configurationPath());
    }

    private final FileSystem fileSystem;
    private final ConfigurationPath configurationPrefix;
    private final LoadingCache<String, Optional<File>> fileCacheLoader;

    public PropertiesProvider(FileSystem fileSystem, ConfigurationPath configurationPrefix) {
        this.fileSystem = fileSystem;
        this.configurationPrefix = configurationPrefix;

        this.fileCacheLoader = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .build(new CacheLoader<>() {
                @Override
                public Optional<File> load(final String fileName) {
                    return getConfigurationFileFromFileSystem(fileName);
                }
            });
    }

    public Configuration getConfigurations(String... filenames) throws FileNotFoundException, ConfigurationException {
        File file = Arrays.stream(filenames)
            .map(this::getConfigurationFile)
            .flatMap(Optional::stream)
            .findFirst()
            .orElseThrow(() -> fail(Joiner.on(",").join(filenames) + " not found"));

        return getConfiguration(file);
    }

    public Configuration getConfiguration(String fileName) throws FileNotFoundException, ConfigurationException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(fileName));

        File file = getConfigurationFile(fileName)
            .orElseThrow(() -> fail(fileName));

        return getConfiguration(file);
    }

    public FileNotFoundException fail(String message) {
        if (failOnMissingConfiguration()) {
            throw new MissingConfigurationFile(message);
        }
        return new FileNotFoundException(message);
    }

    private Configuration getConfiguration(File propertiesFile) throws ConfigurationException {
        FileBasedConfigurationBuilder<FileBasedConfiguration> builder = new FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration.class)
            .configure(new Parameters()
                .fileBased()
                .setListDelimiterHandler(new DefaultListDelimiterHandler(COMMA))
                .setFile(propertiesFile));

        return new DelegatedPropertiesConfiguration(COMMA, builder.getConfiguration());
    }

    private Optional<File> getConfigurationFile(String fileName) {
        return Throwing.supplier(() -> fileCacheLoader.get(fileName)).get();
    }

    private Optional<File> getConfigurationFileFromFileSystem(String fileName) {
        try {
            File file = fileSystem.getFile(configurationPrefix.asString() + fileName + ".properties");
            LOGGER.info("Load configuration file {}", file.getAbsolutePath());
            return Optional.of(file)
                .filter(File::exists);
        } catch (FileNotFoundException e) {
            return Optional.empty();
        }
    }
}