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

package org.apache.james.webadmin.integration.vault;

import static io.restassured.config.ParamConfig.UpdateStrategy.REPLACE;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.awaitility.Durations.FIVE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.PostgresJamesConfiguration;
import org.apache.james.PostgresJamesServerMain;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.vault.VaultConfiguration;
import org.apache.james.webadmin.WebAdminUtils;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.config.ParamConfig;
import io.restassured.specification.RequestSpecification;

class PostgresDeletedMessageVaultIntegrationTest {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<PostgresJamesConfiguration>(tmpDir ->
        PostgresJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.scanning())
            .usersRepository(DEFAULT)
            .eventBusImpl(PostgresJamesConfiguration.EventBusImpl.IN_MEMORY)
            .deletedMessageVaultConfiguration(VaultConfiguration.ENABLED_DEFAULT)
            .build())
        .server(PostgresJamesServerMain::createServer)
        .extension(PostgresExtension.empty())
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    private static final ConditionFactory AWAIT = Awaitility.await()
        .atMost(ONE_MINUTE)
        .with()
        .pollInterval(FIVE_HUNDRED_MILLISECONDS);
    private static final String DOMAIN = "james.local";
    private static final String USER = "toto@" + DOMAIN;
    private static final String PASSWORD = "123456";
    private static final String JAMES_SERVER_HOST = "127.0.0.1";

    private TestIMAPClient testIMAPClient;
    private SMTPMessageSender smtpMessageSender;
    private RequestSpecification webAdminApi;

    @BeforeEach
    void setUp(GuiceJamesServer jamesServer) throws Exception {
        this.testIMAPClient = new TestIMAPClient();
        this.smtpMessageSender = new SMTPMessageSender(DOMAIN);
        this.webAdminApi = WebAdminUtils.spec(jamesServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort())
            .config(WebAdminUtils.defaultConfig()
                .paramConfig(new ParamConfig(REPLACE, REPLACE, REPLACE)));

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN)
            .addUser(USER, PASSWORD);
    }

    @Test
    void restoreDeletedMessageShouldSucceed(GuiceJamesServer jamesServer) throws Exception {
        // Create a message
        int imapPort = jamesServer.getProbe(ImapGuiceProbe.class).getImapPort();
        smtpMessageSender.connect(JAMES_SERVER_HOST, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(USER, PASSWORD)
            .sendMessageWithHeaders(USER, USER, "Subject: thisIsASubject\r\n\r\nBody");
        testIMAPClient.connect(JAMES_SERVER_HOST, imapPort)
            .login(USER, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessageCount(AWAIT, 1);

        // Delete the message
        testIMAPClient.setFlagsForAllMessagesInMailbox("\\Deleted");
        testIMAPClient.expunge();
        testIMAPClient.awaitNoMessage(AWAIT);

        // Restore the message using the Deleted message vault webadmin endpoint
        String restoreBySubjectQuery = "{" +
            "  \"combinator\": \"and\"," +
            "  \"limit\": 1," +
            "  \"criteria\": [" +
            "    {" +
            "      \"fieldName\": \"subject\"," +
            "      \"operator\": \"equals\"," +
            "      \"value\": \"thisIsASubject\"" +
            "    }" +
            "  ]" +
            "}";
        DeletedMessagesVaultRequests.restoreMessagesForUserWithQuery(webAdminApi, USER, restoreBySubjectQuery);

        // await the message to be restored
        testIMAPClient.select(DefaultMailboxes.RESTORED_MESSAGES)
            .awaitMessageCount(AWAIT, 1);
    }

}
