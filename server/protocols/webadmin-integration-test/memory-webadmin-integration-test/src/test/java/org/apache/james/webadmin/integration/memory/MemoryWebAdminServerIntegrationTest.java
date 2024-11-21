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

import static io.restassured.RestAssured.when;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.hamcrest.Matchers.is;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesConfiguration;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.webadmin.integration.WebAdminServerIntegrationTest;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MemoryWebAdminServerIntegrationTest extends WebAdminServerIntegrationTest {
    private static final String DOMAIN = "domain";
    private static final String USERNAME = "bob@" + DOMAIN;
    private static final String PASSWORD = "password";

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(MemoryJamesServerMain::createServer)
        .build();

    @RegisterExtension
    TestIMAPClient testIMAPClient = new TestIMAPClient();

    @Test
    void shouldDescribeConnectedImapChannels(GuiceJamesServer server) throws Exception {
        int imapPort = server.getProbe(ImapGuiceProbe.class).getImapPort();

        server.getProbe(DataProbeImpl.class).addUser(USERNAME, PASSWORD);

        testIMAPClient.connect(LOCALHOST_IP, imapPort);
        testIMAPClient.sendCommand("ID (\"name\" \"Thunderbird\" \"version\" \"102.7.1\")");
        testIMAPClient.login(USERNAME, PASSWORD)
            .select("INBOX");

        when()
            .get("/servers/channels/" + USERNAME)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("[0].protocol", is("IMAP"))
            .body("[0].endpoint", is("imapserver"))
            .body("[0].username", is("bob@domain"))
            .body("[0].isEncrypted", is(false))
            .body("[0].isEncrypted", is(false))
            .body("[0].protocolSpecificInformation.loggedInUser", is("bob@domain"))
            .body("[0].protocolSpecificInformation.userAgent", is("{name=Thunderbird, version=102.7.1}"))
            .body("[0].protocolSpecificInformation.requestCount", is("3"));
    }
}