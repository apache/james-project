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

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesConfiguration;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.modules.ACLProbeImpl;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.specification.RequestSpecification;

class MemoryUserDeletionIntegrationTest {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule()))
        .build();

    private RequestSpecification webAdminApi;

    @BeforeEach
    void setUp(GuiceJamesServer jmapServer) throws Exception {
        DataProbe dataProbe = jmapServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(ALICE.asString(), ALICE_PASSWORD);
        dataProbe.addUser(BOB.asString(), BOB_PASSWORD);

        webAdminApi = WebAdminUtils.spec(jmapServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());
    }

    @Test
    void shouldDeleteMailboxes() {
        webAdminApi.put("/users/" + ALICE.asString() + "/mailboxes/test");

        String taskId = webAdminApi
            .queryParam("action", "deleteData")
            .post("/users/" + ALICE.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await");

        webAdminApi.get("/users/" + ALICE.asString() + "/mailboxes")
            .then()
            .body(".", hasSize(0));
    }

    @Test
    void shouldDeleteACLs(GuiceJamesServer server) throws Exception {
        server.getProbe(MailboxProbeImpl.class).createMailbox(MailboxPath.inbox(BOB));
        server.getProbe(ACLProbeImpl.class).addRights(MailboxPath.inbox(BOB), ALICE.asString(), MailboxACL.FULL_RIGHTS);

        String taskId = webAdminApi
            .queryParam("action", "deleteData")
            .post("/users/" + ALICE.asString())
            .jsonPath()
            .get("taskId");

        webAdminApi.get("/tasks/" + taskId + "/await");

        // Bob Inbox should no longer accept Alice access
        MailboxACL acls = server.getProbe(ACLProbeImpl.class).retrieveRights(MailboxPath.inbox(BOB));
        assertThat(acls.getEntries())
            .hasSize(1)
            .containsEntry(MailboxACL.EntryKey.deserialize("owner"), MailboxACL.FULL_RIGHTS);
    }
}
