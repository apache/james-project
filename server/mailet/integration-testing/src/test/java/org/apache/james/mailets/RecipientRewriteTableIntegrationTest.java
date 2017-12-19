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

import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import com.jayway.awaitility.core.ConditionFactory;

public class RecipientRewriteTableIntegrationTest {
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final int IMAP_PORT = 1143;
    private static final int SMTP_PORT = 1025;
    private static final String PASSWORD = "secret";

    private static final String JAMES_APACHE_ORG = "james.org";
    private static final String JAMES_ANOTHER_DOMAIN = "james.com";

    private static final String FROM = "fromUser@" + JAMES_APACHE_ORG;
    private static final String RECIPIENT = "touser@" + JAMES_APACHE_ORG;

    private static final String ANY_AT_JAMES = "any@" + JAMES_APACHE_ORG;
    private static final String OTHER_AT_JAMES = "other@" + JAMES_APACHE_ORG;

    private static final String ANY_AT_ANOTHER_DOMAIN = "any@" + JAMES_ANOTHER_DOMAIN;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private TemporaryJamesServer jamesServer;
    private ConditionFactory calmlyAwait;
    private DataProbe dataProbe;


    @Before
    public void setup() throws Exception {
        MailetContainer mailetContainer = MailetContainer.builder()
            .postmaster("postmaster@" + JAMES_APACHE_ORG)
            .addProcessor(CommonProcessors.root())
            .addProcessor(CommonProcessors.error())
            .addProcessor(CommonProcessors.transport())
            .addProcessor(CommonProcessors.spam())
            .addProcessor(CommonProcessors.localAddressError())
            .addProcessor(CommonProcessors.relayDenied())
            .addProcessor(CommonProcessors.bounces())
            .addProcessor(CommonProcessors.sieveManagerCheck())
            .build();

        jamesServer = TemporaryJamesServer.builder().build(temporaryFolder, mailetContainer);
        Duration slowPacedPollInterval = Duration.FIVE_HUNDRED_MILLISECONDS;
        calmlyAwait = Awaitility.with().pollInterval(slowPacedPollInterval).and().with().pollDelay(slowPacedPollInterval).await();
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);

        dataProbe.addDomain(JAMES_APACHE_ORG);
        dataProbe.addDomain(JAMES_ANOTHER_DOMAIN);
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void rrtServiceShouldDeliverEmailToMappingRecipients() throws Exception {
        dataProbe.addUser(FROM, PASSWORD);

        createUserInbox(ANY_AT_JAMES);
        createUserInbox(OTHER_AT_JAMES);

        dataProbe.addAddressMapping("touser", JAMES_APACHE_ORG, ANY_AT_JAMES);
        dataProbe.addAddressMapping("touser", JAMES_APACHE_ORG, OTHER_AT_JAMES);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(FROM, RECIPIENT);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(messageSender::messageHasBeenSent);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() -> imapMessageReader.userReceivedMessage(ANY_AT_JAMES, PASSWORD));
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() -> imapMessageReader.userReceivedMessage(OTHER_AT_JAMES, PASSWORD));
        }
    }

    @Test
    public void rrtServiceShouldNotDeliverEmailToRecipientWhenHaveMappingRecipients() throws Exception {
        dataProbe.addUser(FROM, PASSWORD);

        createUserInbox(RECIPIENT);
        createUserInbox(ANY_AT_JAMES);
        createUserInbox(OTHER_AT_JAMES);

        dataProbe.addAddressMapping("touser", JAMES_APACHE_ORG, ANY_AT_JAMES);
        dataProbe.addAddressMapping("touser", JAMES_APACHE_ORG, OTHER_AT_JAMES);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(FROM, RECIPIENT);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(messageSender::messageHasBeenSent);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() -> imapMessageReader.userDoesNotReceiveMessage(RECIPIENT, PASSWORD));
        }
    }

    @Test
    public void rrtServiceShouldDeliverEmailToRecipientOnLocalWhenMappingContainsNonDomain() throws Exception {
        String nonDomainUser = "nondomain";
        String localUser = nonDomainUser + "@" + dataProbe.getDefaultDomain();

        dataProbe.addUser(FROM, PASSWORD);

        createUserInbox(localUser);
        createUserInbox(OTHER_AT_JAMES);

        dataProbe.addAddressMapping("touser", JAMES_APACHE_ORG, nonDomainUser);
        dataProbe.addAddressMapping("touser", JAMES_APACHE_ORG, OTHER_AT_JAMES);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(FROM, RECIPIENT);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(messageSender::messageHasBeenSent);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() -> imapMessageReader.userReceivedMessage(OTHER_AT_JAMES, PASSWORD));
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() -> imapMessageReader.userReceivedMessage(localUser, PASSWORD));
        }
    }

    @Test
    public void messageShouldRedirectToTheSameUserWhenDomainMapping() throws Exception {
        dataProbe.addDomainAliasMapping(JAMES_APACHE_ORG, JAMES_ANOTHER_DOMAIN);

        createUserInbox(ANY_AT_JAMES);
        createUserInbox(ANY_AT_ANOTHER_DOMAIN);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(FROM, ANY_AT_JAMES);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(messageSender::messageHasBeenSent);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() -> imapMessageReader.userReceivedMessage(ANY_AT_ANOTHER_DOMAIN, PASSWORD));
        }
    }

    @Test
    public void messageShouldNotSendToRecipientWhenDomainMapping() throws Exception {
        dataProbe.addDomainAliasMapping(JAMES_APACHE_ORG, JAMES_ANOTHER_DOMAIN);

        createUserInbox(ANY_AT_JAMES);
        createUserInbox(ANY_AT_ANOTHER_DOMAIN);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_APACHE_ORG);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(FROM, ANY_AT_JAMES);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(messageSender::messageHasBeenSent);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() -> imapMessageReader.userDoesNotReceiveMessage(ANY_AT_JAMES, PASSWORD));
        }
    }

    private void createUserInbox(String username) throws Exception {
        dataProbe.addUser(username, PASSWORD);
        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "INBOX");
    }

}
