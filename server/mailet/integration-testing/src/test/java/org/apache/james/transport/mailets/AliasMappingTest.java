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

import static io.restassured.RestAssured.given;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.internet.MimeMessage;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.jmap.mailet.VacationMailet;
import org.apache.james.jmap.mailet.filter.JMAPFiltering;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.IsSenderInRRTLoop;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.AliasRoutes;
import org.apache.mailet.base.test.FakeMail;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.restassured.specification.RequestSpecification;

public class AliasMappingTest {
    private static final String DOMAIN = "domain.tld";

    private static final String BOB_USER = "bob";
    private static final String ALICE_USER = "alice";
    private static final String CEDRIC_USER = "cedric";

    private static final String BOB_ADDRESS = BOB_USER + "@" + DOMAIN;
    private static final String ALICE_ADDRESS = ALICE_USER + "@" + DOMAIN;
    private static final String CEDRIC_ADDRESS = CEDRIC_USER + "@" + DOMAIN;

    private static final String GROUP = "group";
    private static final String GROUP_ADDRESS = GROUP + "@" + DOMAIN;

    private static final String BOB_ALIAS = BOB_USER + "-alias@" + DOMAIN;
    private static final String BOB_ALIAS_2 = BOB_USER + "-alias2@" + DOMAIN;
    private static final String GROUP_ALIAS = GROUP + "-alias@" + DOMAIN;

    private static final String MESSAGE_CONTENT = "any text";
    private static final String RRT_ERROR = "rrt-error";
    private static final MailRepositoryUrl RRT_ERROR_REPOSITORY = MailRepositoryUrl.from("file://var/mail/rrt-error/");

    private TemporaryJamesServer jamesServer;
    private MimeMessage message;
    private DataProbe dataProbe;
    private RequestSpecification webAdminApi;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    @Before
    public void setup() throws Exception {
        MailetContainer.Builder mailetContainer = TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
            .putProcessor(ProcessorConfiguration.transport()
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(RecipientRewriteTable.class)
                    .addProperty("errorProcessor", RRT_ERROR))
                .addMailet(MailetConfiguration.builder()
                    .matcher(RecipientIsLocal.class)
                    .mailet(VacationMailet.class))
                .addMailet(MailetConfiguration.builder()
                    .matcher(RecipientIsLocal.class)
                    .mailet(JMAPFiltering.class))
                .addMailetsFrom(CommonProcessors.deliverOnlyTransport()))
            .putProcessor(ProcessorConfiguration.builder()
                .state(RRT_ERROR)
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ToRepository.class)
                    .addProperty("passThrough", "true")
                    .addProperty("repositoryPath", RRT_ERROR_REPOSITORY.asString()))
                .addMailet(MailetConfiguration.builder()
                    .matcher(IsSenderInRRTLoop.class)
                    .mailet(Null.class))
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(Bounce.class)));

        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder);

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);

        dataProbe.addUser(BOB_ADDRESS, PASSWORD);
        dataProbe.addUser(ALICE_ADDRESS, PASSWORD);
        dataProbe.addUser(CEDRIC_ADDRESS, PASSWORD);

        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, BOB_ADDRESS, "INBOX");
        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, ALICE_ADDRESS, "INBOX");
        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, CEDRIC_ADDRESS, "INBOX");

        WebAdminGuiceProbe webAdminGuiceProbe = jamesServer.getProbe(WebAdminGuiceProbe.class);
        webAdminGuiceProbe.await();
        webAdminApi = given()
            .spec(WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort()).build());

        message = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("test")
            .setText(MESSAGE_CONTENT)
            .build();
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void messageShouldRedirectToUserWhenSentToHisAlias() throws Exception {
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ADDRESS + "/sources/" + BOB_ALIAS);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(BOB_ALIAS));

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    public void messageShouldRedirectToForwardOfUserWhenSentToHisAlias() throws Exception {
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ADDRESS + "/sources/" + BOB_ALIAS);
        dataProbe.addForwardMapping(BOB_USER, DOMAIN, CEDRIC_ADDRESS);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(BOB_ALIAS));

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(CEDRIC_ADDRESS, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    public void messageShouldRedirectToUserWhenForwardedToHisAlias() throws Exception {
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ADDRESS + "/sources/" + BOB_ALIAS);
        dataProbe.addForwardMapping(ALICE_USER, DOMAIN, BOB_ALIAS);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(CEDRIC_ADDRESS)
                .recipient(ALICE_ADDRESS));

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    public void messageShouldRedirectToUserWhenHisAliasIsPartOfGroup() throws Exception {
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ADDRESS + "/sources/" + BOB_ALIAS);
        dataProbe.addGroupMapping(GROUP, DOMAIN, BOB_ALIAS);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(GROUP_ADDRESS));

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    public void messageShouldRedirectToMembersWhenSentToGroupAlias() throws Exception {
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + GROUP_ADDRESS + "/sources/" + GROUP_ALIAS);
        dataProbe.addGroupMapping(GROUP, DOMAIN, BOB_ADDRESS);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(GROUP_ALIAS));

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    public void messageShouldRedirectToUserWhithAliasesCascading() throws Exception {
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ADDRESS + "/sources/" + BOB_ALIAS);
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ALIAS + "/sources/" + BOB_ALIAS_2);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(BOB_ALIAS_2));

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    public void messageShouldRedirectToUsersSharingSameAlias() throws Exception {
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ADDRESS + "/sources/" + BOB_ALIAS);
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + ALICE_ADDRESS + "/sources/" + BOB_ALIAS);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(CEDRIC_ADDRESS)
                .recipient(BOB_ALIAS));

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).contains(MESSAGE_CONTENT);

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(ALICE_ADDRESS, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).contains(MESSAGE_CONTENT);
    }


    @Test
    public void messageShouldRedirectFromAliasContainingSlash() throws Exception {
        String aliasWithSlash = "bob/alias@" + DOMAIN;
        String aliasWithEncodedSlash = "bob%2Falias@" + DOMAIN;
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + BOB_ADDRESS + "/sources/" + aliasWithEncodedSlash);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(aliasWithSlash));

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void messageShouldRedirectToUserContainingSlash() throws Exception {
        String userWithSlash = "bob/a@" + DOMAIN;
        dataProbe.addUser(userWithSlash, PASSWORD);
        String userWithEncodedSlash = "bob%2Fa@" + DOMAIN;
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + userWithEncodedSlash + "/sources/" + BOB_ALIAS);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(BOB_ALIAS));

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(userWithSlash, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void messageShouldRedirectToUserWhenEncodingAt() throws Exception {
        String userWithEncodedAt = "bob%40" + DOMAIN;
        String aliasWithEncodedAt = "bob-alias%40" + DOMAIN;
        webAdminApi.put(AliasRoutes.ROOT_PATH + "/" + userWithEncodedAt + "/sources/" + aliasWithEncodedAt);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(ALICE_ADDRESS)
                .recipient(BOB_ALIAS));

        imapMessageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB_ADDRESS, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

}
