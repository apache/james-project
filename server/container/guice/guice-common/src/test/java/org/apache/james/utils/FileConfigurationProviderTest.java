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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

public class FileConfigurationProviderTest {

    private static final String CONFIG_KEY_1 = "test2";
    private static final String CONFIG_KEY_2 = "property";
    private static final String CONFIG_KEY_4 = "james";
    private static final String CONFIG_KEY_5 = "internal";
    private static final String CONFIG_KEY_ENV = "env";
    private static final String CONFIG_KEY_ENV_WITH_COMMA = "envWithComma";
    private static final String CONFIG_KEY_NOT_ENV = "notEnv";
    private static final String VALUE_1 = "0";
    private static final String VALUE_2 = "awesome";
    private static final String VALUE_3 = "james";
    private static final String VALUE_NOT_ENV = "${env:MY_NOT_IN_ENV_VAR}";
    private static final String ENVIRONMENT_SET_VALUE = "testvalue";
    private static final String ENVIRONMENT_WITH_COMMA = "testvalue,testvalue2,testvalue3";
    private static final String FAKE_CONFIG_KEY = "fake";
    private static final String ROOT_CONFIG_KEY = "test";
    private static final String CONFIG_SEPARATOR = ".";

    private FileConfigurationProvider configurationProvider;

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Before
    public void setUp() {
        environmentVariables.set("MY_ENV_VAR", ENVIRONMENT_SET_VALUE);
        environmentVariables.set("MY_ENV_VAR_WITH_COMMA", ENVIRONMENT_WITH_COMMA);
        environmentVariables.clear("MY_NOT_IN_ENV_VAR");
        Configuration configuration = Configuration.builder()
            .workingDirectory("../")
            .configurationFromClasspath()
            .build();
        FileSystemImpl fileSystem = new FileSystemImpl(configuration.directories());
        configurationProvider = new FileConfigurationProvider(fileSystem, configuration);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyArgumentShouldThrow() throws Exception {
        configurationProvider.getConfiguration("");
    }

    @Test(expected = NullPointerException.class)
    public void nullArgumentShouldThrow() throws Exception {
        configurationProvider.getConfiguration(null);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void configSeparatorArgumentShouldThrow() throws Exception {
        configurationProvider.getConfiguration(CONFIG_SEPARATOR);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void emptyFileNameShouldThrow() throws Exception {
        configurationProvider.getConfiguration(CONFIG_SEPARATOR + ROOT_CONFIG_KEY);
    }

    @Test
    public void getConfigurationShouldLoadCorrespondingXMLFile() throws Exception {
        HierarchicalConfiguration<ImmutableNode> hierarchicalConfiguration = configurationProvider.getConfiguration(ROOT_CONFIG_KEY);
        assertThat(hierarchicalConfiguration.getKeys())
            .toIterable()
            .containsOnly(CONFIG_KEY_1,
                String.join(CONFIG_SEPARATOR, CONFIG_KEY_4, CONFIG_KEY_2),
                String.join(CONFIG_SEPARATOR, CONFIG_KEY_4, CONFIG_KEY_5, CONFIG_KEY_2),
                CONFIG_KEY_ENV, CONFIG_KEY_ENV_WITH_COMMA, CONFIG_KEY_NOT_ENV);
        assertThat(hierarchicalConfiguration.getProperty(CONFIG_KEY_1)).isEqualTo(VALUE_1);
    }

    @Test
    public void getConfigurationShouldLoadCorrespondingXMLFilePart() throws Exception {
        HierarchicalConfiguration<ImmutableNode> hierarchicalConfiguration = configurationProvider.getConfiguration(
                String.join(CONFIG_SEPARATOR, ROOT_CONFIG_KEY, CONFIG_KEY_4));
        assertThat(hierarchicalConfiguration.getKeys())
            .toIterable()
            .containsOnly(CONFIG_KEY_2,
                String.join(CONFIG_SEPARATOR, CONFIG_KEY_5, CONFIG_KEY_2));
        assertThat(hierarchicalConfiguration.getProperty(CONFIG_KEY_2)).isEqualTo(VALUE_2);
    }

    @Test
    public void getConfigurationShouldLoadCorrespondingXMLFileWhenAPathIsProvidedPart() throws Exception {
        HierarchicalConfiguration<ImmutableNode> hierarchicalConfiguration = configurationProvider.getConfiguration(
                String.join(CONFIG_SEPARATOR, ROOT_CONFIG_KEY, CONFIG_KEY_4, CONFIG_KEY_5));
        assertThat(hierarchicalConfiguration.getKeys())
            .toIterable()
            .containsOnly(CONFIG_KEY_2);
        assertThat(hierarchicalConfiguration.getProperty(CONFIG_KEY_2)).isEqualTo(VALUE_3);
    }

    @Test
    public void multiplesSeparatorsShouldBeTolerated() throws Exception {
        HierarchicalConfiguration<ImmutableNode> hierarchicalConfiguration = configurationProvider.getConfiguration(
                ROOT_CONFIG_KEY + CONFIG_SEPARATOR + CONFIG_SEPARATOR + CONFIG_KEY_4);
        assertThat(hierarchicalConfiguration.getKeys())
            .toIterable()
            .containsOnly(CONFIG_KEY_2,
                String.join(CONFIG_SEPARATOR, CONFIG_KEY_5, CONFIG_KEY_2));
        assertThat(hierarchicalConfiguration.getProperty(CONFIG_KEY_2)).isEqualTo(VALUE_2);
    }

    @Test
    public void getConfigurationShouldReturnDefaultOnNonExistingXMLFile() throws Exception {
        assertThat(configurationProvider.getConfiguration(FAKE_CONFIG_KEY)).isEqualTo(FileConfigurationProvider.EMPTY_CONFIGURATION);
    }

    @Test(expected = ConfigurationRuntimeException.class)
    public void getConfigurationShouldThrowOnNonExistingXMLFilePart() throws Exception {
        configurationProvider.getConfiguration(String.join(CONFIG_SEPARATOR, ROOT_CONFIG_KEY, FAKE_CONFIG_KEY));
    }

    @Test
    public void getConfigurationShouldNotReplaceEnvironmentVariableWhenNotSet() throws Exception {
        HierarchicalConfiguration<ImmutableNode> hierarchicalConfiguration = configurationProvider.getConfiguration(ROOT_CONFIG_KEY);
        assertThat(hierarchicalConfiguration.getString(CONFIG_KEY_NOT_ENV)).isEqualTo(VALUE_NOT_ENV);
    }

    @Test
    public void getConfigurationShouldReplaceEnvironmentVariableWhenSet() throws Exception {
        HierarchicalConfiguration<ImmutableNode> hierarchicalConfiguration = configurationProvider.getConfiguration(ROOT_CONFIG_KEY);
        assertThat(hierarchicalConfiguration.getString(CONFIG_KEY_ENV)).isEqualTo(ENVIRONMENT_SET_VALUE);
    }

    @Test
    public void getConfigurationShouldReplaceEnvironmentVariableWithoutSplittingThemWhenSet() throws Exception {
        HierarchicalConfiguration<ImmutableNode> hierarchicalConfiguration = configurationProvider.getConfiguration(ROOT_CONFIG_KEY);
        assertThat(hierarchicalConfiguration.getString(CONFIG_KEY_ENV_WITH_COMMA)).isEqualTo(ENVIRONMENT_WITH_COMMA);
    }
}
