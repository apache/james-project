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

package org.apache.james.webadmin.integration.postgres;

import static org.apache.james.JamesServerExtension.Lifecycle.PER_CLASS;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.PostgresJamesConfiguration;
import org.apache.james.PostgresJamesServerMain;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.webadmin.integration.WebAdminServerIntegrationImmutableTest;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresWebAdminServerIntegrationImmutableTest extends WebAdminServerIntegrationImmutableTest {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<PostgresJamesConfiguration>(tmpDir ->
        PostgresJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.scanning())
            .usersRepository(DEFAULT)
            .eventBusImpl(PostgresJamesConfiguration.EventBusImpl.IN_MEMORY)
            .build())
        .extension(PostgresExtension.empty())
        .server(PostgresJamesServerMain::createServer)
        .lifeCycle(PER_CLASS)
        .build();
}
