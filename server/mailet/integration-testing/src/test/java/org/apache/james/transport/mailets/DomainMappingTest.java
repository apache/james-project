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

package org.apache.james.transport.mailets;

import static org.apache.james.mailets.configuration.CommonProcessors.RRT_ERROR_REPOSITORY;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import javax.mail.internet.MimeMessage;

import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.restassured.specification.RequestSpecification;

class DomainMappingTest {
    private static final String DOMAIN1 = "domain1.com";
    private static final String DOMAIN2 = "domain2.com";

    private static final String SENDER_LOCAL_PART = "fromuser";
    private static final String SENDER = SENDER_LOCAL_PART + "@" + DOMAIN1;
    private static final String USER_DOMAIN1 = "user@" + DOMAIN1;
    private static final String USER_DOMAIN2 = "user@" + DOMAIN2;
    private static final String BOB_DOMAIN1 = "bob@" + DOMAIN1;
    private static final String BOB_DOMAIN2 = "bob@" + DOMAIN2;
    private static final String MESSAGE_CONTENT = "any text";

    private TemporaryJamesServer jamesServer;
    private MimeMessage message;
    private RequestSpecification webAdminApi;

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    @BeforeEach
    void setup(@TempDir File temporaryFolder) throws Exception {
        MailetContainer.Builder mailetContainer = TemporaryJamesServer.simpleMailetContainerConfiguration()
            .putProcessor(CommonProcessors.rrtErrorEnabledTransport())
            .putProcessor(CommonProcessors.rrtError());

        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder);
        jamesServer.start();

        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN1)
            .addDomain(DOMAIN2)
            .addUser(SENDER, PASSWORD)
            .addUser(USER_DOMAIN1, PASSWORD);

        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxPath.forUser(Username.of(USER_DOMAIN1), MailboxConstants.INBOX));
        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxPath.forUser(Username.of(USER_DOMAIN2), MailboxConstants.INBOX));

        webAdminApi = WebAdminUtils.spec(jamesServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());

        message = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("test")
            .setText(MESSAGE_CONTENT)
            .build();
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void messageShouldRedirectToUserWhenBelongingToGroup() throws Exception {
        webAdminApi.body(DOMAIN1).put("/domainMappings/" + DOMAIN2);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(USER_DOMAIN2));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(USER_DOMAIN1, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    void messageShouldRedirectToUserOfTheDestinationDomainWhenSentToTheAliasDomain() throws Exception {
        webAdminApi.put("/domains/" + DOMAIN1 + "/aliases/" + DOMAIN2);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(USER_DOMAIN2));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(USER_DOMAIN1, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    void mailShouldGoToRRTErrorMailRepositoryUponDomainLoop() throws Exception {
        webAdminApi.put("/domains/" + DOMAIN1 + "/aliases/" + DOMAIN2);
        webAdminApi.put("/domains/" + DOMAIN2 + "/aliases/" + DOMAIN1);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(USER_DOMAIN2));

        awaitAtMostOneMinute.until(
            () -> jamesServer.getProbe(MailRepositoryProbeImpl.class)
                .getRepositoryMailCount(RRT_ERROR_REPOSITORY) == 1);
    }

    @Test
    void mailShouldGoToRRTErrorMailRepositoryUponLoopCombiningDomainAndAlias() throws Exception {
        jamesServer.getProbe(DataProbeImpl.class).addUser(BOB_DOMAIN2, PASSWORD);

        webAdminApi.put("/address/aliases/" + BOB_DOMAIN2 + "/sources/" + BOB_DOMAIN1);
        webAdminApi.put("/domains/" + DOMAIN1 + "/aliases/" + DOMAIN2);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(BOB_DOMAIN1));

        awaitAtMostOneMinute.until(
            () -> jamesServer.getProbe(MailRepositoryProbeImpl.class)
                .getRepositoryMailCount(RRT_ERROR_REPOSITORY) == 1);
    }

    @Test
    void domainAliasShouldBeIgnoredWhenUserAlias() throws Exception {
        jamesServer.getProbe(DataProbeImpl.class).addUser(BOB_DOMAIN1, PASSWORD);

        webAdminApi.put("/address/aliases/" + BOB_DOMAIN1 + "/sources/" + USER_DOMAIN2);
        webAdminApi.put("/domains/" + DOMAIN1 + "/aliases/" + DOMAIN2);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(USER_DOMAIN2));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_DOMAIN1, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    void domainAliasShouldBeChainedIfApplicableAfterUserAliasRewrite() throws Exception {
        jamesServer.getProbe(DataProbeImpl.class).addUser(BOB_DOMAIN1, PASSWORD);

        webAdminApi.put("/address/aliases/" + BOB_DOMAIN2 + "/sources/" + USER_DOMAIN2);
        webAdminApi.put("/domains/" + DOMAIN1 + "/aliases/" + DOMAIN2);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(USER_DOMAIN2));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_DOMAIN1, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).contains(MESSAGE_CONTENT);
    }
}
