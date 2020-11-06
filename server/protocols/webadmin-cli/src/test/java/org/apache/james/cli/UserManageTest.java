/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.cli;

import static org.apache.james.MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.integration.WebadminIntegrationTestModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class UserManageTest {

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
        .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
            .combineWith(IN_MEMORY_SERVER_AGGREGATE_MODULE)
            .overrideWith(new WebadminIntegrationTestModule())
            .overrideWith(new TestJMAPServerModule()))
        .build();

    private final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errorStreamCaptor = new ByteArrayOutputStream();
    private DataProbeImpl dataProbe;

    @Test
    void userListShouldBeEmptyWhenNoUsers(GuiceJamesServer server) {
        Port port = server.getProbe(WebAdminGuiceProbe.class).getWebAdminPort();

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "user", "list");

        assertThat(exitCode).isEqualTo(0);
        assertThat(outputStreamCaptor.toString()).isEqualTo("");
    }

    @Test
    void userListShouldShowTwoAddedUser(GuiceJamesServer server) throws Exception {
        Port port = server.getProbe(WebAdminGuiceProbe.class).getWebAdminPort();
        dataProbe = server.getProbe(DataProbeImpl.class);
        dataProbe.fluent().addDomain("linagora.com")
            .addUser("hqtran@linagora.com", "123456")
            .addUser("testing@linagora.com", "123456");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "user", "list");

        assertThat(exitCode).isEqualTo(0);
        assertThat(outputStreamCaptor.toString().trim().toCharArray()).containsOnly("hqtran@linagora.com".concat("\n").concat("testing@linagora.com").toCharArray());
    }
}