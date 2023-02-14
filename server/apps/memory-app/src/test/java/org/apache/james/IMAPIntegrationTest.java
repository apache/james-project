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
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class IMAPIntegrationTest {

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule()))
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(BOB.asString(), BOB_PASSWORD);
    }

    @Test
    void logoutCommandShouldWork(GuiceJamesServer guiceJamesServer) throws Exception {
        TestIMAPClient testIMAPClient = new TestIMAPClient();
        long messageCount = testIMAPClient.connect(LOCALHOST_IP, guiceJamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB.asString(), BOB_PASSWORD)
            .getMessageCount(MailboxConstants.INBOX);

        assertThat(messageCount).isEqualTo(0L);
        assertThat(testIMAPClient.sendCommand("LOGOUT"))
            .contains("OK LOGOUT completed.");
    }

}
