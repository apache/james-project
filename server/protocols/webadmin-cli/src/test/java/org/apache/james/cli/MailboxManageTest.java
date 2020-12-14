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

public class MailboxManageTest {

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
    void mailboxCreateWithExistedUsernameAndValidMailboxNameShouldSucceed() throws Exception {
        dataProbe.fluent().addDomain("linagora.com")
            .addUser("hqtran@linagora.com", "123456");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "create", "hqtran@linagora.com", "INBOX");

        WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "exist", "hqtran@linagora.com", "INBOX");

        assertThat(exitCode).isEqualTo(0);
        assertThat(outputStreamCaptor.toString().trim()).isEqualTo("The mailbox was created successfully.\n" +
            "The mailbox exists.");
    }

    @Test
    void mailboxCreateWithExistedUsernameAndInvalidMailboxNameShouldFail() throws Exception {
        dataProbe.fluent().addDomain("linagora.com")
            .addUser("hqtran@linagora.com", "123456");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "create", "hqtran@linagora.com", "#&%*INBOX");

        assertThat(exitCode).isEqualTo(1);
        assertThat(errorStreamCaptor.toString()).contains("400");
    }

    @Test
    void mailboxCreateWithNonExistingUsernameShouldFail() {
        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "create", "hqtran@linagora.com", "INBOX");

        assertThat(exitCode).isEqualTo(1);
        assertThat(errorStreamCaptor.toString()).contains("404");
    }

    @Test
    void mailboxCreateWithAlreadyExistingMailboxShouldSucceed() throws Exception {
        dataProbe.fluent().addDomain("linagora.com")
            .addUser("hqtran@linagora.com", "123456");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "create", "hqtran@linagora.com", "INBOX");

        int exitCode2 = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "create", "hqtran@linagora.com", "INBOX");

        assertThat(exitCode).isEqualTo(0);
        assertThat(exitCode2).isEqualTo(0);
        assertThat(outputStreamCaptor.toString().trim()).isEqualTo("The mailbox was created successfully.");
    }

    @Test
    void mailboxCreateSubMailboxesShouldSucceed() throws Exception {
        dataProbe.fluent().addDomain("linagora.com")
            .addUser("hqtran@linagora.com", "123456");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "create", "hqtran@linagora.com", "INBOX.1");

        int exitCode2 = WebAdminCli.executeFluent(new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "create", "hqtran@linagora.com", "INBOX.2");

        WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "list", "hqtran@linagora.com");

        assertThat(exitCode).isEqualTo(0);
        assertThat(exitCode2).isEqualTo(0);
        assertThat(outputStreamCaptor.toString()).isEqualTo("INBOX\nINBOX.1\nINBOX.2\n");
    }

    @Test
    void mailboxExistWithExistedUsernameAndExistedMailboxNameShouldSucceed() throws Exception {
        dataProbe.fluent().addDomain("linagora.com")
            .addUser("hqtran@linagora.com", "123456");

        WebAdminCli.executeFluent(new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "create", "hqtran@linagora.com", "INBOX");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "exist", "hqtran@linagora.com", "INBOX");

        assertThat(exitCode).isEqualTo(0);
        assertThat(outputStreamCaptor.toString().trim()).isEqualTo("The mailbox exists.");
    }

    @Test
    void mailboxExistWithInvalidMailboxNameShouldFail() throws Exception {
        dataProbe.fluent().addDomain("linagora.com")
            .addUser("hqtran@linagora.com", "123456");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "exist", "hqtran@linagora.com", "#INBOX");

        assertThat(exitCode).isEqualTo(1);
        assertThat(errorStreamCaptor.toString()).contains("400");
    }

    @Test
    void mailboxExistWithExistedUserAndNonExistingMailboxNameShouldFail() throws Exception {
        dataProbe.fluent().addDomain("linagora.com")
            .addUser("hqtran@linagora.com", "123456");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "exist", "hqtran@linagora.com", "INBOX");

        assertThat(exitCode).isEqualTo(0);
        assertThat(outputStreamCaptor.toString().trim()).isEqualTo("Either the user name or the mailbox does not exist.");
    }

    @Test
    void mailboxExistWithNonExistingUserAndNonExistingMailboxNameShouldFail() {
        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "exist", "hqtran@linagora.com", "INBOX");

        assertThat(exitCode).isEqualTo(0);
        assertThat(outputStreamCaptor.toString().trim()).isEqualTo("Either the user name or the mailbox does not exist.");
    }

    @Test
    void mailboxListWithTwoAddedMailboxesAndExistedUsernameShouldShowMailboxesNameExactly() throws Exception {
        dataProbe.fluent().addDomain("linagora.com")
            .addUser("hqtran@linagora.com", "123456");

        WebAdminCli.executeFluent(new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "create", "hqtran@linagora.com", "INBOX1");

        WebAdminCli.executeFluent(new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "create", "hqtran@linagora.com", "INBOX2");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "list", "hqtran@linagora.com");

        assertThat(exitCode).isEqualTo(0);
        assertThat(outputStreamCaptor.toString()).isEqualTo("INBOX1\nINBOX2\n");
    }

    @Test
    void mailboxListWithAValidUserAndNonExistingMailboxesShouldShowNothing() throws Exception {
        dataProbe.fluent().addDomain("linagora.com")
            .addUser("hqtran@linagora.com", "123456");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "list", "hqtran@linagora.com");

        assertThat(exitCode).isEqualTo(0);
        assertThat(outputStreamCaptor.toString()).isEqualTo("");
    }

    @Test
    void mailboxListWithNonExistingUsernameShouldFail() {
        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "list", "hqtran@linagora.com");

        assertThat(exitCode).isEqualTo(1);
        assertThat(outputStreamCaptor.toString()).isEmpty();
    }

    @Test
    void mailboxDeleteAParentMailboxWithTwoAddedChildrenMailboxShouldDeleteThemAll() throws Exception {
        dataProbe.fluent().addDomain("linagora.com")
            .addUser("hqtran@linagora.com", "123456");

        WebAdminCli.executeFluent(new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "create", "hqtran@linagora.com", "INBOX.1");

        WebAdminCli.executeFluent(new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "create", "hqtran@linagora.com", "INBOX.2");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "delete", "hqtran@linagora.com", "INBOX");

        WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
                "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "list", "hqtran@linagora.com");

        assertThat(exitCode).isEqualTo(0);
        // The outputStreamCaptor should capture the result of delete command and the result of list command(which is nothing)
        assertThat(outputStreamCaptor.toString()).isEqualTo("The mailbox now does not exist on the server.\n");
    }

    @Test
    void mailboxDeleteWithNonExistingUsernameShouldFail() throws Exception {
        dataProbe.fluent().addDomain("linagora.com");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
                "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "delete", "hqtran@linagora.com", "INBOX");

        assertThat(exitCode).isEqualTo(1);
        assertThat(errorStreamCaptor.toString()).contains("User does not exist");
    }

    @Test
    void mailboxDeleteWithInvalidMailboxNameShouldFail() throws Exception {
        dataProbe.fluent().addDomain("linagora.com")
                .addUser("hqtran@linagora.com", "123456");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
                "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "delete", "hqtran@linagora.com", "IN#BOX");

        assertThat(exitCode).isEqualTo(1);
        assertThat(errorStreamCaptor.toString()).contains("Attempt to delete an invalid mailbox");
    }

    @Test
    void mailboxDeleteWithInvalidUsernameShouldFail() throws Exception {
        dataProbe.fluent().addDomain("linagora.com")
                .addUser("hqtran@linagora.com", "123456");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
                "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "delete", "hqtr@an@linagora.com", "INBOX");

        assertThat(exitCode).isEqualTo(1);
        assertThat(errorStreamCaptor.toString()).contains("Attempt to delete an invalid mailbox");
    }

    @Test
    void mailboxDeleteAllWithExistingUserShouldDeleteAllMailboxes() throws Exception {
        dataProbe.fluent().addDomain("linagora.com")
                .addUser("hqtran@linagora.com", "123456");

        WebAdminCli.executeFluent(new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()),
                "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "create", "hqtran@linagora.com", "INBOX.1");

        WebAdminCli.executeFluent(new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()),
                "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "create", "hqtran@linagora.com", "DRAFT");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
                "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "deleteAll", "hqtran@linagora.com");

        WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
                "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "list", "hqtran@linagora.com");

        assertThat(exitCode).isEqualTo(0);
        // The outputStreamCaptor should capture the result of deleteAll command and the result of list command(which is nothing)
        assertThat(outputStreamCaptor.toString()).isEqualTo("The user do not have mailboxes anymore.\n");
    }

    @Test
    void mailboxDeleteAllWithNonExistingUsernameShouldFail() throws Exception {
        dataProbe.fluent().addDomain("linagora.com");

        int exitCode = WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
                "--url", "http://127.0.0.1:" + port.getValue(), "mailbox", "deleteAll", "hqtran@linagora.com");

        assertThat(exitCode).isEqualTo(1);
        assertThat(errorStreamCaptor.toString()).contains("The user name does not exist.");
    }

}