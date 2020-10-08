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

package org.apache.james.webadmin.integration.memory;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.webadmin.RandomPortSupplier;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.apache.james.webadmin.integration.AuthorizedEndpointsTest;
import org.apache.james.webadmin.integration.UnauthorizedModule;
import org.apache.james.webadmin.integration.WebadminIntegrationTestModule;
import org.junit.jupiter.api.extension.RegisterExtension;

class MemoryAuthorizedEndpointsTest extends AuthorizedEndpointsTest {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new WebadminIntegrationTestModule())
            .overrideWith(new UnauthorizedModule())
            .overrideWith(binder -> binder.bind(WebAdminConfiguration.class)
                .toInstance(WebAdminConfiguration.builder()
                    .enabled()
                    .corsDisabled()
                    .host("127.0.0.1")
                    .port(new RandomPortSupplier())
                    .additionalRoute("org.apache.james.webadmin.dropwizard.MetricsRoutes")
                    .build())))
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();
}