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

package org.apache.james.modules.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.junit.jupiter.api.Test;

class WebAdminServerModuleTest {
    @Test
    void shouldReturnEmptyWhenNoField() throws Exception {
        FileBasedConfiguration configuration = getConfiguration("webadmin-none.properties");

        assertThat(new WebAdminServerModule().additionalRoutes(configuration))
            .isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenEmptyField() throws Exception {
        FileBasedConfiguration configuration = getConfiguration("webadmin-empty.properties");

        assertThat(new WebAdminServerModule().additionalRoutes(configuration))
            .isEmpty();
    }

    @Test
    void shouldReturnOneRoutes() throws Exception {
        FileBasedConfiguration configuration = getConfiguration("webadmin-one.properties");

        assertThat(new WebAdminServerModule().additionalRoutes(configuration))
            .containsOnly("org.apache.custom.webadmin.CustomRoute");
    }

    @Test
    void shouldReturnSeveralRoutes() throws Exception {
        FileBasedConfiguration configuration = getConfiguration("webadmin-two.properties");

        assertThat(new WebAdminServerModule().additionalRoutes(configuration))
            .containsOnly("org.apache.custom.webadmin.CustomRoute", "org.apache.custom.webadmin.AnotherCustomRoute");
    }

    private FileBasedConfiguration getConfiguration(String name) throws org.apache.commons.configuration2.ex.ConfigurationException {
        FileBasedConfigurationBuilder<FileBasedConfiguration> builder = new FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration.class)
            .configure(new Parameters()
                .fileBased()
                .setURL(ClassLoader.getSystemResource(name)));
        return builder.getConfiguration();
    }
}