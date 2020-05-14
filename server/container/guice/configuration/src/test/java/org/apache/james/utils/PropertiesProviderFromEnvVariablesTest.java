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

import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

public class PropertiesProviderFromEnvVariablesTest {

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    private PropertiesProvider testee;

    @Before
    public void setUp() {
        Configuration configuration = Configuration.builder()
            .workingDirectory("../")
            .configurationFromClasspath()
            .build();
        FileSystemImpl fileSystem = new FileSystemImpl(configuration.directories());
        testee = new PropertiesProvider(fileSystem, configuration.configurationPath());
    }

    @Test
    public void getListShouldLoadEnvVariables() throws Exception {
        environmentVariables.set("PROPERTIES_PROVIDER_ENV_VARIABLES", "value1, value2, value3, value4, value5");

        assertThat(testee.getConfiguration("env")
                .getList("keyByEnv"))
            .containsExactly("value1", "value2", "value3", "value4", "value5");
    }

    @Test
    public void getArrayShouldLoadEnvVariables() throws Exception {
        environmentVariables.set("PROPERTIES_PROVIDER_ENV_VARIABLES", "value1, value2, value3, value4, value5");

        assertThat(testee.getConfiguration("env")
                .getStringArray("keyByEnv"))
            .containsExactly("value1", "value2", "value3", "value4", "value5");
    }
}
