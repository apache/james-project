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
import org.junit.jupiter.api.BeforeEach;
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
    private Port port;

    @BeforeEach
    void setUp(GuiceJamesServer server) {
        port = server.getProbe(WebAdminGuiceProbe.class).getWebAdminPort();
        dataProbe = server.getProbe(DataProbeImpl.class);
    }

    @Test
    void userListShouldBeEmptyWhenNoUsers() {
        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "user", "list");

        assertThat(exitCode).isEqualTo(0);
        assertThat(outputStreamCaptor.toString()).isEqualTo("");
    }

    @Test
    void userListShouldShowTwoAddedUser() throws Exception {
        dataProbe.fluent().addDomain("linagora.com")
            .addUser("hqtran@linagora.com", "123456")
            .addUser("testing@linagora.com", "123456");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "user", "list");

        assertThat(exitCode).isEqualTo(0);
        assertThat(outputStreamCaptor.toString().trim().toCharArray()).containsOnly("hqtran@linagora.com".concat("\n").concat("testing@linagora.com").toCharArray());
    }

    @Test
    void userCreateWithoutForceShouldAddValidUserSucceed() throws Exception {
        dataProbe.fluent().addDomain("linagora.com");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "user", "create", "hqtran@linagora.com", "--password", "123456");

        assertThat(exitCode).isEqualTo(0);
        assertThat(outputStreamCaptor.toString().trim()).isEqualTo("The user was created successfully");
        assertThat(dataProbe.listUsers()).containsOnly("hqtran@linagora.com");
    }

    @Test
    void userCreateShouldFailWithInvalidUsername() throws Exception {
        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "user", "create", "hqtran@linagora.com", "--password", "123456");

        int exitCode1 = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "user", "create", "--force", "hqtran@linagora.com", "--password", "123456");

        assertThat(exitCode).isEqualTo(1);
        assertThat(exitCode1).isEqualTo(1);
        assertThat(errorStreamCaptor.toString().trim()).isEqualTo("The user name or the payload is invalid\nThe user name or the payload is invalid");
        assertThat(dataProbe.listUsers()).isEmpty();
    }

    @Test
    void userCreateWithoutForceShouldNotAllowUpdateAUserPassword() throws Exception {
        dataProbe.fluent().addDomain("linagora.com");

        WebAdminCli.executeFluent(new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()),
            "--url", "http://127.0.0.1:" + port.getValue(), "user", "create", "hqtran@linagora.com", "--password", "123456");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "user", "create", "hqtran@linagora.com", "--password", "123457");

        assertThat(exitCode).isEqualTo(1);
        assertThat(errorStreamCaptor.toString().trim()).isEqualTo("The user already exists");
    }

    @Test
    void userCreateWithForceShouldAllowUpdateAUserPassword() throws Exception {
        dataProbe.fluent().addDomain("linagora.com");

        WebAdminCli.executeFluent(new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()),
            "--url", "http://127.0.0.1:" + port.getValue(), "user", "create", "hqtran@linagora.com", "--password", "123456");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "user", "create", "--force", "hqtran@linagora.com", "--password", "123457");

        assertThat(exitCode).isEqualTo(0);
        assertThat(outputStreamCaptor.toString().trim()).isEqualTo("The user's password was successfully updated");
    }

    @Test
    void userDeleteWithAddedUserShouldSucceed() throws Exception {
        dataProbe.fluent().addDomain("linagora.com")
            .addUser("hqtran@linagora.com", "123456");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "user", "delete", "hqtran@linagora.com");

        assertThat(exitCode).isEqualTo(0);
        assertThat(dataProbe.listUsers()).isEmpty();
    }

    @Test
    void userDeleteWithNonExistingUserShouldSucceed() throws Exception {
        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "user", "delete", "hqtran@linagora.com");

        assertThat(exitCode).isEqualTo(0);
        assertThat(dataProbe.listUsers()).doesNotContain("hqtran@linagora.com");
    }

    @Test
    void userExistCommandWithNonExistingUserShouldFail() {
        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "user", "exist", "hqtran@linagora.com");

        assertThat(exitCode).isEqualTo(0);
        assertThat(outputStreamCaptor.toString().trim()).isEqualTo("hqtran@linagora.com does not exist");
    }

    @Test
    void userExistCommandWithInvalidUserNameShouldFail() {
        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "user", "exist", "hqtran@@linagora.com");

        assertThat(exitCode).isEqualTo(1);
        assertThat(outputStreamCaptor.toString().trim()).isEqualTo("The user name is invalid.\n" +
            "A user has two attributes: username and password. A valid user should satisfy these criteria:\n" +
            "-  username and password cannot be null or empty\n" +
            "-  username should not be longer than 255 characters\n" +
            "-  username can not contain '/'\n" +
            "-  username can not contain multiple domain delimiter('@')\n" +
            "-  A username can have only a local part when virtualHosting is disabled. E.g.'myUser'\n" +
            "-  When virtualHosting is enabled, a username should have a domain part, and the domain part " +
            "should be concatenated after a domain delimiter('@'). E.g. 'myuser@james.org'");
    }

    @Test
    void userExistCommandWithAddedUserShouldSucceed() throws Exception {
        dataProbe.fluent().addDomain("linagora.com")
            .addUser("hqtran@linagora.com", "123456");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "user", "exist", "hqtran@linagora.com");

        assertThat(exitCode).isEqualTo(0);
        assertThat(outputStreamCaptor.toString().trim()).isEqualTo("hqtran@linagora.com exists");
    }

}