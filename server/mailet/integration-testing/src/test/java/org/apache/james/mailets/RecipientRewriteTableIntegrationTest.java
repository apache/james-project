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

package org.apache.james.mailets;

import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.ForwardRoutes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.restassured.specification.RequestSpecification;

public class RecipientRewriteTableIntegrationTest {
    private static final String JAMES_ANOTHER_DOMAIN = "james.com";

    private static final String FROM_LOCAL_PART = "fromUser";
    private static final String FROM = FROM_LOCAL_PART + "@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT_LOCAL_PART = "touser";
    private static final String RECIPIENT = RECIPIENT_LOCAL_PART + "@" + DEFAULT_DOMAIN;
    private static final String ANY_LOCAL_PART = "any";
    private static final String ANY_AT_JAMES = ANY_LOCAL_PART + "@" + DEFAULT_DOMAIN;
    private static final String OTHER_AT_JAMES = "other@" + DEFAULT_DOMAIN;
    private static final String ANY_AT_ANOTHER_DOMAIN = ANY_LOCAL_PART + "@" + JAMES_ANOTHER_DOMAIN;
    private static final String GROUP_LOCAL_PART = "group";
    private static final String GROUP = GROUP_LOCAL_PART + "@" + DEFAULT_DOMAIN;

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;
    private DataProbe dataProbe;
    private RequestSpecification webAdminApi;

    @BeforeEach
    void setup(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder().build(temporaryFolder);
        jamesServer.start();

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addDomain(JAMES_ANOTHER_DOMAIN);

        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(ANY_AT_JAMES, PASSWORD);
        dataProbe.addUser(OTHER_AT_JAMES, PASSWORD);
        dataProbe.addUser(FROM, PASSWORD);

        webAdminApi = WebAdminUtils.spec(jamesServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void rrtServiceShouldNotImpactRecipientsNotMatchingAnyRRT() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).isNotNull();
    }

    @Test
    void rrtServiceShouldDeliverEmailToMappingRecipients() throws Exception {
        dataProbe.addAddressMapping(RECIPIENT_LOCAL_PART, DEFAULT_DOMAIN, ANY_AT_JAMES);
        dataProbe.addAddressMapping(RECIPIENT_LOCAL_PART, DEFAULT_DOMAIN, OTHER_AT_JAMES);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(ANY_AT_JAMES, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).isNotNull();
        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(OTHER_AT_JAMES, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).isNotNull();
    }

    @Test
    void rrtServiceShouldNotDeliverEmailToRecipientWhenHaveMappingRecipients() throws Exception {
        dataProbe.addAddressMapping(RECIPIENT_LOCAL_PART, DEFAULT_DOMAIN, ANY_AT_JAMES);
        dataProbe.addAddressMapping(RECIPIENT_LOCAL_PART, DEFAULT_DOMAIN, OTHER_AT_JAMES);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitNoMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).isNotNull();
    }

    @Test
    void rrtServiceShouldDeliverEmailToRecipientOnLocalWhenMappingContainsNonDomain() throws Exception {
        String nonDomainUser = "nondomain";
        String localUser = nonDomainUser + "@" + dataProbe.getDefaultDomain();
        dataProbe.addUser(localUser, PASSWORD);

        dataProbe.addAddressMapping(RECIPIENT_LOCAL_PART, DEFAULT_DOMAIN, nonDomainUser);
        dataProbe.addAddressMapping(RECIPIENT_LOCAL_PART, DEFAULT_DOMAIN, OTHER_AT_JAMES);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(localUser, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).isNotNull();
        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(OTHER_AT_JAMES, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).isNotNull();
    }

    @Test
    void messageShouldRedirectToTheSameUserWhenDomainMapping() throws Exception {
        dataProbe.addDomainAliasMapping(DEFAULT_DOMAIN, JAMES_ANOTHER_DOMAIN);
        dataProbe.addUser(ANY_AT_ANOTHER_DOMAIN, PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ANY_AT_JAMES);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(ANY_AT_ANOTHER_DOMAIN, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).isNotNull();
    }

    @Test
    void messageShouldNotSendToRecipientWhenDomainMapping() throws Exception {
        dataProbe.addDomainAliasMapping(DEFAULT_DOMAIN, JAMES_ANOTHER_DOMAIN);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, ANY_AT_JAMES);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(ANY_AT_JAMES, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitNoMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).isNotNull();
    }

    @Test
    void rrtServiceShouldDeliverEmailToForwardRecipients() throws Exception {
        webAdminApi.put(ForwardRoutes.ROOT_PATH + "/" + RECIPIENT + "/targets/" + ANY_AT_JAMES);
        webAdminApi.put(ForwardRoutes.ROOT_PATH + "/" + RECIPIENT + "/targets/" + OTHER_AT_JAMES);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(ANY_AT_JAMES, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(OTHER_AT_JAMES, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).isNotNull();
    }

    @Test
    void rrtServiceShouldFollowForwardWhenSendingToAGroup() throws Exception {
        dataProbe.addAddressMapping(GROUP_LOCAL_PART, DEFAULT_DOMAIN, ANY_AT_JAMES);

        webAdminApi.put(ForwardRoutes.ROOT_PATH + "/" + ANY_AT_JAMES + "/targets/" + OTHER_AT_JAMES);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, GROUP);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(OTHER_AT_JAMES, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).isNotNull();
    }

    @Test
    void domainAliasMappingShouldNotCreateNonExistedUserWhenReRouting() throws Exception {
        dataProbe.addDomain("domain1.com");
        dataProbe.addDomain("domain2.com");

        // domain2 as the source, domain1 as the destination
        dataProbe.addDomainAliasMapping("domain2.com", "domain1.com");

        dataProbe.addUser("user@domain1.com", PASSWORD);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, "user@domain2.com");

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login("user@domain1.com", PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        // assert user  non existence
        assertThat(dataProbe.listUsers())
            .doesNotContain("user@domain2.com");

        // assert alias address has no mailbox
        assertThat(jamesServer.getProbe(MailboxProbeImpl.class)
            .listUserMailboxes("user@doamin2.com"))
            .isEmpty();
    }
}
