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

package org.apache.james;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.utils.PropertiesProvider.MissingConfigurationFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DefaultMemoryJamesServerTest {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> binder.bind(ConfigurationProvider.class).toInstance((s, l) -> new BaseHierarchicalConfiguration())))
        .disableAutoStart()
        .build();

    @Test
    void memoryJamesServerShouldStartWithNoConfigurationFile(GuiceJamesServer server) throws Exception {
        server.start();
        assertThat(server.isStarted()).isTrue();
    }

    @Test
    void shouldFailOnMissingConfigurationFilesWhenRequested(GuiceJamesServer server) {
        System.setProperty("james.fail.on.missing.configuration", "true");
        try {
            assertThatThrownBy(server::start).hasCauseInstanceOf(MissingConfigurationFile.class);
        } finally {
            System.setProperty("james.fail.on.missing.configuration", "false");
        }
    }
}
