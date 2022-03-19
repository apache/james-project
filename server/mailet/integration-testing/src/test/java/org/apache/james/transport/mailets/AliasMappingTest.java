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

import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.AliasRoutes;
import org.apache.james.webadmin.routes.ForwardRoutes;
import org.apache.james.webadmin.routes.GroupsRoutes;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.restassured.specification.RequestSpecification;

class AliasMappingTest {
    private static final String DOMAIN = "domain.tld";
    private static final String DOMAIN_2 = "domain2.tld";

    private static final String BOB_USER = "bob";
    private static final String ALICE_USER = "alice";
    private static final String CEDRIC_USER = "cedric";

    private static final String BOB_ADDRESS = BOB_USER + "@" + DOMAIN;
    private static final String UPPER_BOB_ADDRESS = BOB_USER.toUpperCase() + "@" + DOMAIN;
    private static final String ALICE_ADDRESS = ALICE_USER + "@" + DOMAIN;
    private static final String CEDRIC_ADDRESS = CEDRIC_USER + "@" + DOMAIN;

    private static final String GROUP = "group";
    private static final String GROUP_ADDRESS = GROUP + "@" + DOMAIN;

    private static final String BOB_ALIAS = BOB_USER + "-alias@" + DOMAIN;
    private static final String BOB_ALIAS_2 = BOB_USER + "-alias2@" + DOMAIN;
    private static final String UPPER_BOB_ALIAS = BOB_USER.toUpperCase() + "-ALIAS@" + DOMAIN;
    private static final String GROUP_ALIAS = GROUP + "-alias@" + DOMAIN;

    private static final String MESSAGE_CONTENT = "any text";
    private static final MailRepositoryUrl RRT_ERROR_REPOSITORY = MailRepositoryUrl.from("memory://var/mail/rrt-error/");

    private TemporaryJamesServer jamesServer;
    private MimeMessage message;
    private DataProbe dataProbe;
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

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        dataProbe.addDomain(DOMAIN_2);

        dataProbe.addUser(BOB_ADDRESS, PASSWORD);
        dataProbe.addUser(ALICE_ADDRESS, PASSWORD);
        dataProbe.addUser(CEDRIC_ADDRESS, PASSWORD);

        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxPath.inbox(Username.of(BOB_ADDRESS)));
        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxPath.inbox(Username.of(ALICE_ADDRESS)));
        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxPath.inbox(Username.of(CEDRIC_ADDRESS)));

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
    void messageShouldRedirectToUserWhenSentToHisAlias() throws Exception {
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ADDRESS + "/sources/" + BOB_ALIAS);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE_ADDRESS, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(BOB_ALIAS));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    void messageShouldRedirectToUserWhenSentToHisUpperCaseAlias() throws Exception {
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ADDRESS + "/sources/" + BOB_ALIAS);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE_ADDRESS, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(UPPER_BOB_ALIAS));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    void messageShouldRedirectToUserWhenLowerCaseAliasMappedToUpperCaseUser() throws Exception {
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + UPPER_BOB_ADDRESS + "/sources/" + BOB_ALIAS);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE_ADDRESS, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(BOB_ALIAS));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    void messageShouldRedirectToUserWithUpperCaseDefinedAliasWhenSentToHisLowerCaseAlias() throws Exception {
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ADDRESS + "/sources/" + UPPER_BOB_ALIAS);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE_ADDRESS, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(BOB_ALIAS));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    void messageShouldRedirectToForwardOfUserWhenSentToHisAlias() throws Exception {
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ADDRESS + "/sources/" + BOB_ALIAS);
        webAdminApi.put(ForwardRoutes.ROOT_PATH + "/" + BOB_ADDRESS + "/targets/" + CEDRIC_ADDRESS);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE_ADDRESS, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(BOB_ALIAS));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(CEDRIC_ADDRESS, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    void messageShouldRedirectToUserWhenForwardedToHisAlias() throws Exception {
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ADDRESS + "/sources/" + BOB_ALIAS);
        webAdminApi.put(ForwardRoutes.ROOT_PATH + "/" + ALICE_ADDRESS + "/targets/" + BOB_ALIAS);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(CEDRIC_ADDRESS, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(CEDRIC_ADDRESS)
                .recipient(ALICE_ADDRESS));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    void messageShouldRedirectToUserWhenHisAliasIsPartOfGroup() throws Exception {
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ADDRESS + "/sources/" + BOB_ALIAS);
        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ADDRESS + "/" + BOB_ALIAS);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE_ADDRESS, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(GROUP_ADDRESS));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    void messageShouldRedirectToMembersWhenSentToGroupAlias() throws Exception {
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + GROUP_ADDRESS + "/sources/" + GROUP_ALIAS);
        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ADDRESS + "/" + BOB_ADDRESS);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE_ADDRESS, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(GROUP_ALIAS));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    void messageShouldRedirectToUserWithAliasesCascading() throws Exception {
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ADDRESS + "/sources/" + BOB_ALIAS);
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ALIAS + "/sources/" + BOB_ALIAS_2);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE_ADDRESS, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(BOB_ALIAS_2));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    void messageShouldRedirectToUsersSharingSameAlias() throws Exception {
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ADDRESS + "/sources/" + BOB_ALIAS);
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + ALICE_ADDRESS + "/sources/" + BOB_ALIAS);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(CEDRIC_ADDRESS, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(CEDRIC_ADDRESS)
                .recipient(BOB_ALIAS));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).contains(MESSAGE_CONTENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(ALICE_ADDRESS, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    void messageShouldRedirectFromAliasContainingSlash() throws Exception {
        String aliasWithSlash = "bob/alias@" + DOMAIN;
        String aliasWithEncodedSlash = "bob%2Falias@" + DOMAIN;
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ADDRESS + "/sources/" + aliasWithEncodedSlash);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE_ADDRESS, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(aliasWithSlash));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    void messageShouldRedirectToUserContainingSlash() throws Exception {
        String userWithSlash = "bob/a@" + DOMAIN;
        dataProbe.addUser(userWithSlash, PASSWORD);
        String userWithEncodedSlash = "bob%2Fa@" + DOMAIN;
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + userWithEncodedSlash + "/sources/" + BOB_ALIAS);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE_ADDRESS, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(BOB_ALIAS));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(userWithSlash, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    void messageShouldRedirectToUserWhenEncodingAt() throws Exception {
        String userWithEncodedAt = "bob%40" + DOMAIN;
        String aliasWithEncodedAt = "bob-alias%40" + DOMAIN;
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + userWithEncodedAt + "/sources/" + aliasWithEncodedAt);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE_ADDRESS, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(BOB_ALIAS));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    void messageShouldBeStoredInRepositoryWhenAliasLoopMapping() throws Exception {
        String bobAlias2 = BOB_USER + "@" + DOMAIN_2;

        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ADDRESS + "/sources/" + bobAlias2);
        webAdminApi.put("/domains/" + DOMAIN_2 + "/aliases/" + DOMAIN);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE_ADDRESS, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(BOB_ADDRESS));

        awaitAtMostOneMinute.until(
            () -> jamesServer.getProbe(MailRepositoryProbeImpl.class)
                .getRepositoryMailCount(RRT_ERROR_REPOSITORY) == 1);
    }

    @Test
    void userShouldNotReceiveDuplicatesWhenUserAndAliasRegisteredToAGroup() throws Exception {
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ADDRESS + "/sources/" + BOB_ALIAS);
        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ADDRESS + "/" + BOB_ADDRESS);
        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ADDRESS + "/" + BOB_ALIAS);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE_ADDRESS, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(GROUP_ADDRESS));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessageCount(awaitAtMostOneMinute, 1);
    }
}
