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

import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileConfigurationProviderTest {

    private static final String CONFIG_KEY_1 = "test2";
    private static final String CONFIG_KEY_2 = "property";
    private static final String CONFIG_KEY_4 = "james";
    private static final String CONFIG_KEY_5 = "internal";
    private static final String CONFIG_KEY_ENV = "env";
    private static final String CONFIG_KEY_ENV_WITH_COMMA = "envWithComma";
    private static final String CONFIG_KEY_NOT_ENV = "notEnv";
    private static final String CONFIG_KEY_ENV_WITH_FALLBACK_VALUE = "envWithFallbackValue";
    private static final String VALUE_1 = "0";
    private static final String VALUE_2 = "awesome";
    private static final String VALUE_3 = "james";
    private static final String VALUE_NOT_ENV = "${env:MY_NOT_IN_ENV_VAR}";
    private static final String FALLBACK_VALUE = "fallbackValue";
    private static final String ENVIRONMENT_SET_VALUE = "testvalue";
    private static final String ENVIRONMENT_WITH_COMMA = "testvalue,testvalue2,testvalue3";
    private static final String FAKE_CONFIG_KEY = "fake";
    private static final String ROOT_CONFIG_KEY = "test";
    private static final String CONFIG_SEPARATOR = ".";

    private FileConfigurationProvider configurationProvider;

    @BeforeEach
    void setUp() {
        Configuration configuration = Configuration.builder()
            .workingDirectory("../")
            .configurationFromClasspath()
            .build();
        FileSystemImpl fileSystem = new FileSystemImpl(configuration.directories());
        configurationProvider = new FileConfigurationProvider(fileSystem, configuration);
    }

    @Test
    void emptyArgumentShouldThrow() {
        assertThatThrownBy(() -> configurationProvider.getConfiguration(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullArgumentShouldThrow() {
        assertThatThrownBy(() -> configurationProvider.getConfiguration(null))
            .isInstanceOf(NullPointerException.class);
    }
    
    @Test
    void configSeparatorArgumentShouldThrow() {
        assertThatThrownBy(() -> configurationProvider.getConfiguration(CONFIG_SEPARATOR))
            .isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    void emptyFileNameShouldThrow() {
        assertThatThrownBy(() -> configurationProvider.getConfiguration(CONFIG_SEPARATOR + ROOT_CONFIG_KEY))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getConfigurationShouldLoadCorrespondingXMLFile() throws Exception {
        HierarchicalConfiguration<ImmutableNode> hierarchicalConfiguration = configurationProvider.getConfiguration(ROOT_CONFIG_KEY);
        assertThat(hierarchicalConfiguration.getKeys())
            .toIterable()
            .containsOnly(CONFIG_KEY_1,
                String.join(CONFIG_SEPARATOR, CONFIG_KEY_4, CONFIG_KEY_2),
                String.join(CONFIG_SEPARATOR, CONFIG_KEY_4, CONFIG_KEY_5, CONFIG_KEY_2),
                CONFIG_KEY_ENV, CONFIG_KEY_ENV_WITH_COMMA, CONFIG_KEY_NOT_ENV,
                CONFIG_KEY_ENV_WITH_FALLBACK_VALUE);
        assertThat(hierarchicalConfiguration.getProperty(CONFIG_KEY_1)).isEqualTo(VALUE_1);
    }

    @Test
    void getConfigurationShouldLoadCorrespondingXMLFilePart() throws Exception {
        HierarchicalConfiguration<ImmutableNode> hierarchicalConfiguration = configurationProvider.getConfiguration(
                String.join(CONFIG_SEPARATOR, ROOT_CONFIG_KEY, CONFIG_KEY_4));
        assertThat(hierarchicalConfiguration.getKeys())
            .toIterable()
            .containsOnly(CONFIG_KEY_2,
                String.join(CONFIG_SEPARATOR, CONFIG_KEY_5, CONFIG_KEY_2));
        assertThat(hierarchicalConfiguration.getProperty(CONFIG_KEY_2)).isEqualTo(VALUE_2);
    }

    @Test
    void getConfigurationShouldLoadCorrespondingXMLFileWhenAPathIsProvidedPart() throws Exception {
        HierarchicalConfiguration<ImmutableNode> hierarchicalConfiguration = configurationProvider.getConfiguration(
                String.join(CONFIG_SEPARATOR, ROOT_CONFIG_KEY, CONFIG_KEY_4, CONFIG_KEY_5));
        assertThat(hierarchicalConfiguration.getKeys())
            .toIterable()
            .containsOnly(CONFIG_KEY_2);
        assertThat(hierarchicalConfiguration.getProperty(CONFIG_KEY_2)).isEqualTo(VALUE_3);
    }

    @Test
    void multiplesSeparatorsShouldBeTolerated() throws Exception {
        HierarchicalConfiguration<ImmutableNode> hierarchicalConfiguration = configurationProvider.getConfiguration(
                ROOT_CONFIG_KEY + CONFIG_SEPARATOR + CONFIG_SEPARATOR + CONFIG_KEY_4);
        assertThat(hierarchicalConfiguration.getKeys())
            .toIterable()
            .containsOnly(CONFIG_KEY_2,
                String.join(CONFIG_SEPARATOR, CONFIG_KEY_5, CONFIG_KEY_2));
        assertThat(hierarchicalConfiguration.getProperty(CONFIG_KEY_2)).isEqualTo(VALUE_2);
    }

    @Test
    void getConfigurationShouldReturnDefaultOnNonExistingXMLFile() throws Exception {
        assertThat(configurationProvider.getConfiguration(FAKE_CONFIG_KEY)).isEqualTo(FileConfigurationProvider.EMPTY_CONFIGURATION);
    }

    @Test
    void getConfigurationShouldThrowOnNonExistingXMLFilePart() {
        assertThatThrownBy(() -> configurationProvider.getConfiguration(String.join(CONFIG_SEPARATOR, ROOT_CONFIG_KEY, FAKE_CONFIG_KEY)))
            .isInstanceOf(ConfigurationRuntimeException.class);
    }

    @Test
    void getConfigurationShouldNotReplaceEnvironmentVariableWhenNotSet() throws Exception {
        HierarchicalConfiguration<ImmutableNode> hierarchicalConfiguration = configurationProvider.getConfiguration(ROOT_CONFIG_KEY);
        assertThat(hierarchicalConfiguration.getString(CONFIG_KEY_NOT_ENV)).isEqualTo(VALUE_NOT_ENV);
    }

    @Test
    void getConfigurationShouldReplaceEnvironmentVariableWhenSet() throws Exception {
        withEnvironmentVariable("MY_ENV_VAR", ENVIRONMENT_SET_VALUE)
            .execute(() -> {
                HierarchicalConfiguration<ImmutableNode> hierarchicalConfiguration = configurationProvider.getConfiguration(ROOT_CONFIG_KEY);
                assertThat(hierarchicalConfiguration.getString(CONFIG_KEY_ENV)).isEqualTo(ENVIRONMENT_SET_VALUE);
            });
    }

    @Test
    void getConfigurationShouldReplaceEnvironmentVariableWithoutSplittingThemWhenSet() throws Exception {
        withEnvironmentVariable("MY_ENV_VAR_WITH_COMMA", ENVIRONMENT_WITH_COMMA)
            .execute(() -> {
                HierarchicalConfiguration<ImmutableNode> hierarchicalConfiguration = configurationProvider.getConfiguration(ROOT_CONFIG_KEY);
                assertThat(hierarchicalConfiguration.getString(CONFIG_KEY_ENV_WITH_COMMA)).isEqualTo(ENVIRONMENT_WITH_COMMA);
            });
    }

    @Test
    void getEnvironmentVariableShouldDefaultToFallbackValueIfSet() throws Exception {
        HierarchicalConfiguration<ImmutableNode> hierarchicalConfiguration = configurationProvider.getConfiguration(ROOT_CONFIG_KEY);
        assertThat(hierarchicalConfiguration.getString(CONFIG_KEY_ENV_WITH_FALLBACK_VALUE)).isEqualTo(FALLBACK_VALUE);
    }
}
