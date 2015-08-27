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

import com.google.common.collect.Lists;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.junit.Before;
import org.junit.Test;

public class ClassPathConfigurationProviderTest {

    private static final String CONFIG_KEY_1 = "test2";
    private static final String CONFIG_KEY_2 = "property";
    private static final String CONFIG_KEY_3 = "test2";
    private static final String CONFIG_KEY_4 = "james";
    private static final String CONFIG_KEY_5 = "internal";
    private static final String VALUE_1 = "0";
    private static final String VALUE_2 = "awesome";
    private static final String FAKE_CONFIG_KEY = "fake";
    private static final String ROOT_CONFIG_KEY = "test";
    private static final String CONFIG_SEPARATOR = ".";

    private ClassPathConfigurationProvider configurationProvider;

    @Before
    public void setUp() {
        configurationProvider = new ClassPathConfigurationProvider();
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyFileNameShouldThrow() throws Exception {
        configurationProvider.getConfiguration(CONFIG_SEPARATOR + ROOT_CONFIG_KEY);
    }

    @Test
    public void getConfigurationShouldLoadCorrespondingXMLFile() throws Exception {
        HierarchicalConfiguration hierarchicalConfiguration = configurationProvider.getConfiguration(ROOT_CONFIG_KEY);
        assertThat(Lists.newArrayList(hierarchicalConfiguration.getKeys())).containsOnly(CONFIG_KEY_1,
            CONFIG_KEY_4 + CONFIG_SEPARATOR + CONFIG_KEY_2,
            CONFIG_KEY_4 + CONFIG_SEPARATOR + CONFIG_KEY_5 + CONFIG_SEPARATOR + CONFIG_KEY_2);
        assertThat(hierarchicalConfiguration.getProperty(CONFIG_KEY_3)).isEqualTo(VALUE_1);
    }

    @Test
    public void getConfigurationShouldLoadCorrespondingXMLFilePart() throws Exception {
        HierarchicalConfiguration hierarchicalConfiguration = configurationProvider.getConfiguration(ROOT_CONFIG_KEY + CONFIG_SEPARATOR + CONFIG_KEY_4);
        assertThat(Lists.newArrayList(hierarchicalConfiguration.getKeys())).containsOnly(CONFIG_KEY_2,
            "internal" + CONFIG_SEPARATOR + CONFIG_KEY_2);
        assertThat(hierarchicalConfiguration.getProperty(CONFIG_KEY_2)).isEqualTo(VALUE_2);
    }

    @Test
    public void getConfigurationShouldLoadCorrespondingXMLFileWhenAPathIsProvidedPart() throws Exception {
        HierarchicalConfiguration hierarchicalConfiguration = configurationProvider.getConfiguration(ROOT_CONFIG_KEY + CONFIG_SEPARATOR + CONFIG_KEY_4 + CONFIG_SEPARATOR + "internal");
        assertThat(Lists.newArrayList(hierarchicalConfiguration.getKeys())).containsOnly(CONFIG_KEY_2);
        assertThat(hierarchicalConfiguration.getProperty(CONFIG_KEY_2)).isEqualTo("james");
    }

    @Test
    public void multiplesSeparatorsShouldBeTolerated() throws Exception {
        HierarchicalConfiguration hierarchicalConfiguration = configurationProvider.getConfiguration(ROOT_CONFIG_KEY + CONFIG_SEPARATOR + CONFIG_SEPARATOR + CONFIG_KEY_4);
        assertThat(Lists.newArrayList(hierarchicalConfiguration.getKeys())).containsOnly(CONFIG_KEY_2,
            "internal" + CONFIG_SEPARATOR + CONFIG_KEY_2);
        assertThat(hierarchicalConfiguration.getProperty(CONFIG_KEY_2)).isEqualTo(VALUE_2);
    }

    @Test(expected = ConfigurationException.class)
    public void getConfigurationShouldThrowOnNonExistingXMLFile() throws Exception {
        assertThat(configurationProvider.getConfiguration(FAKE_CONFIG_KEY)).isNotNull();
    }

    @Test(expected = IllegalArgumentException.class)
    public void getConfigurationShouldThrowOnNonExistingXMLFilePart() throws Exception {
        configurationProvider.getConfiguration(ROOT_CONFIG_KEY + CONFIG_SEPARATOR + FAKE_CONFIG_KEY);
    }

}
