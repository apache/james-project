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

import static com.jayway.restassured.RestAssured.given;
import static org.apache.james.mailets.configuration.CommonProcessors.ERROR_REPOSITORY;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.IMAP_PORT;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.SMTP_PORT;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import javax.mail.internet.MimeMessage;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.jmap.mailet.VacationMailet;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.FakeSmtp;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.GroupsRoutes;
import org.apache.mailet.base.test.FakeMail;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jayway.restassured.specification.RequestSpecification;

public class GroupMappingTest {
    private static final String DOMAIN1 = "domain1.com";
    private static final String DOMAIN2 = "domain2.com";

    private static final String SENDER = "fromUser@" + DOMAIN1;
    private static final String GROUP_ON_DOMAIN1 = "group@" + DOMAIN1;
    private static final String GROUP_ON_DOMAIN2 = "group@" + DOMAIN2;

    private static final String USER_DOMAIN1 = "user@" + DOMAIN1;
    private static final String USER_DOMAIN2 = "user@" + DOMAIN2;
    private static final String MESSAGE_CONTENT = "any text";

    private TemporaryJamesServer jamesServer;
    private MimeMessage message;
    private DataProbe dataProbe;
    private RequestSpecification webAdminApi;

    @Rule
    public final FakeSmtp fakeSmtp = new FakeSmtp();
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
                    .mailet(RecipientRewriteTable.class))
                .addMailet(MailetConfiguration.builder()
                    .matcher(RecipientIsLocal.class)
                    .mailet(VacationMailet.class))
                .addMailetsFrom(CommonProcessors.deliverOnlyTransport())
                .addMailet(MailetConfiguration.remoteDeliveryBuilder()
                    .matcher(All.class)
                    .addProperty("gateway", fakeSmtp.getContainer().getContainerIp())));

        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder);

        fakeSmtp.awaitStarted(awaitAtMostOneMinute);

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN1);
        dataProbe.addDomain(DOMAIN2);

        dataProbe.addUser(SENDER, PASSWORD);

        dataProbe.addUser(USER_DOMAIN1, PASSWORD);
        dataProbe.addUser(USER_DOMAIN2, PASSWORD);

        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, USER_DOMAIN1, "INBOX");
        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, USER_DOMAIN2, "INBOX");

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
    public void messageShouldRedirectToUserWhenBelongingToGroup() throws Exception {
        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN1 + "/" + USER_DOMAIN1);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(GROUP_ON_DOMAIN1));

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(USER_DOMAIN1, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    public void messageShouldRedirectToUserDoesNotHaveSameDomainWhenBelongingToGroup() throws Exception {
        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN1 + "/" + USER_DOMAIN2);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(GROUP_ON_DOMAIN1));

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(USER_DOMAIN2, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    public void messageShouldRedirectToAllUsersBelongingToGroup() throws Exception {
        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN1 + "/" + USER_DOMAIN1);

        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN1 + "/" + USER_DOMAIN2);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(GROUP_ON_DOMAIN1));

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(USER_DOMAIN1, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(USER_DOMAIN2, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void messageShouldRedirectWhenGroupBelongingToAnotherGroup() throws Exception {
        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN2 + "/" + USER_DOMAIN2);

        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN1 + "/" + GROUP_ON_DOMAIN2);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(GROUP_ON_DOMAIN1));

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(USER_DOMAIN2, PASSWORD)
            .select(IMAPMessageReader.INBOX);
        awaitAtMostOneMinute.until(imapMessageReader::hasAMessage);
        assertThat(imapMessageReader.readFirstMessage()).contains(MESSAGE_CONTENT);
    }

    @Test
    public void messageShouldNotBeDuplicatedWhenUserBelongingToTwoGroups() throws Exception {
        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN1 + "/" + USER_DOMAIN1);

        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN2 + "/" + USER_DOMAIN1);

        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN1 + "/" + GROUP_ON_DOMAIN2);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(GROUP_ON_DOMAIN1));

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(USER_DOMAIN1, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void messageShouldNotBeDuplicatedWhenRecipientIsAlsoPartOfGroup() throws Exception {
        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN1 + "/" + USER_DOMAIN1);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(SENDER)
                .recipients(GROUP_ON_DOMAIN1, USER_DOMAIN1));

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(USER_DOMAIN1, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void groupMappingShouldSupportTreeStructure() throws Exception {
        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN1 + "/" + USER_DOMAIN1);

        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN2 + "/" + USER_DOMAIN2);

        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN1 + "/" + GROUP_ON_DOMAIN2);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(GROUP_ON_DOMAIN1));

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(USER_DOMAIN2, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(USER_DOMAIN1, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void messageShouldBeStoredInRepositoryWhenGroupLoopMapping() throws Exception {
        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN1 + "/" + USER_DOMAIN1);

        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN2 + "/" + USER_DOMAIN2);

        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN1 + "/" + GROUP_ON_DOMAIN2);

        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN2 + "/" + GROUP_ON_DOMAIN1);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(GROUP_ON_DOMAIN1));

        awaitAtMostOneMinute.until(
            () -> jamesServer.getProbe(MailRepositoryProbeImpl.class)
                .getRepositoryMailCount(ERROR_REPOSITORY) == 1);
    }

    @Test
    public void messageShouldBeWellDeliveredToRecipientNotPartOfTheLoop() throws Exception {
        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN1 + "/" + GROUP_ON_DOMAIN2);

        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN2 + "/" + GROUP_ON_DOMAIN1);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(SENDER)
                .recipients(GROUP_ON_DOMAIN1, USER_DOMAIN2));

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(USER_DOMAIN2, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void messageShouldRedirectToUserWhenDomainMapping() throws Exception {
        dataProbe.addDomainAliasMapping(DOMAIN1, DOMAIN2);

        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN1 + "/" + USER_DOMAIN1);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(GROUP_ON_DOMAIN1));

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(USER_DOMAIN2, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void messageShouldNotSendToUserBelongingToGroupWhenDomainMapping() throws Exception {
        dataProbe.addDomainAliasMapping(DOMAIN1, DOMAIN2);

        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN1 + "/" + USER_DOMAIN1);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(GROUP_ON_DOMAIN1));

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(USER_DOMAIN1, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitNoMessage(awaitAtMostOneMinute);
    }

    @Test
    public void messageShouldRedirectToGroupWhenDomainMapping() throws Exception {
        dataProbe.addDomainAliasMapping(DOMAIN1, DOMAIN2);

        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN2 + "/" + USER_DOMAIN2);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(SENDER)
                .recipient((GROUP_ON_DOMAIN1)));

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(USER_DOMAIN2, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void messageShouldRedirectToGroupContainingSlash() throws Exception {
        String groupWithSlash = "a/a@" + DOMAIN1;
        String groupWithEncodedSlash = "a%2Fa@" + DOMAIN1;
        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + groupWithEncodedSlash + "/" + USER_DOMAIN1);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(groupWithSlash));

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(USER_DOMAIN1, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void messageShouldRedirectToUserContainingSlash() throws Exception {
        String userWithSlash = "a/a@" + DOMAIN1;
        dataProbe.addUser(userWithSlash, PASSWORD);
        String userWithEncodedSlash = "a%2Fa@" + DOMAIN1;
        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN1 + "/" + userWithEncodedSlash);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(GROUP_ON_DOMAIN1));

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(userWithSlash, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void messageShouldRedirectToUserWhenEncodingAt() throws Exception {
        String userWithEncodedAt = "user%40" + DOMAIN1;
        String groupWithEncodedAt = "group%40" + DOMAIN1;
        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + groupWithEncodedAt + "/" + userWithEncodedAt);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(GROUP_ON_DOMAIN1));

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(USER_DOMAIN1, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    public void sendMessageShouldSendAMessageToAnExternalGroupMember() throws Exception {
        String externalMail = "ray@yopmail.com";
        webAdminApi.put(GroupsRoutes.ROOT_PATH + "/" + GROUP_ON_DOMAIN1 + "/" + externalMail);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FakeMail.builder()
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(GROUP_ON_DOMAIN1));

        fakeSmtp.isReceived(response -> response
            .body("[0].from", equalTo(SENDER))
            .body("[0].to[0]", equalTo(externalMail))
            .body("[0].text", equalTo(MESSAGE_CONTENT)));
    }
}
