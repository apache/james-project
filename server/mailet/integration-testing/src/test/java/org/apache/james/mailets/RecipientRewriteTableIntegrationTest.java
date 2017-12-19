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

import static com.jayway.awaitility.Duration.ONE_MINUTE;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.IMAP_PORT;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.SMTP_PORT;
import static org.apache.james.mailets.configuration.Constants.calmlyAwait;

import org.apache.james.mailbox.model.MailboxConstants;
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

public class RecipientRewriteTableIntegrationTest {
    private static final String JAMES_ANOTHER_DOMAIN = "james.com";

    private static final String FROM = "fromUser@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT = "touser@" + DEFAULT_DOMAIN;

    private static final String ANY_AT_JAMES = "any@" + DEFAULT_DOMAIN;
    private static final String OTHER_AT_JAMES = "other@" + DEFAULT_DOMAIN;

    private static final String ANY_AT_ANOTHER_DOMAIN = "any@" + JAMES_ANOTHER_DOMAIN;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();

    private TemporaryJamesServer jamesServer;
    private DataProbe dataProbe;


    @Before
    public void setup() throws Exception {
        jamesServer = TemporaryJamesServer.builder().build(temporaryFolder);
        dataProbe = jamesServer.getProbe(DataProbeImpl.class);

        dataProbe.addDomain(DEFAULT_DOMAIN);
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

        dataProbe.addAddressMapping("touser", DEFAULT_DOMAIN, ANY_AT_JAMES);
        dataProbe.addAddressMapping("touser", DEFAULT_DOMAIN, OTHER_AT_JAMES);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, DEFAULT_DOMAIN)) {
            messageSender.sendMessage(FROM, RECIPIENT)
                .awaitSent(calmlyAwait.atMost(ONE_MINUTE));

            imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
                .login(ANY_AT_JAMES, PASSWORD)
                .select(IMAPMessageReader.INBOX)
                .awaitMessage(calmlyAwait.atMost(ONE_MINUTE));
            imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
                .login(OTHER_AT_JAMES, PASSWORD)
                .select(IMAPMessageReader.INBOX)
                .awaitMessage(calmlyAwait.atMost(ONE_MINUTE));
        }
    }

    @Test
    public void rrtServiceShouldNotDeliverEmailToRecipientWhenHaveMappingRecipients() throws Exception {
        dataProbe.addUser(FROM, PASSWORD);

        createUserInbox(RECIPIENT);
        createUserInbox(ANY_AT_JAMES);
        createUserInbox(OTHER_AT_JAMES);

        dataProbe.addAddressMapping("touser", DEFAULT_DOMAIN, ANY_AT_JAMES);
        dataProbe.addAddressMapping("touser", DEFAULT_DOMAIN, OTHER_AT_JAMES);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, DEFAULT_DOMAIN)) {
            messageSender.sendMessage(FROM, RECIPIENT)
                .awaitSent(calmlyAwait.atMost(ONE_MINUTE));

            imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
                .login(RECIPIENT, PASSWORD)
                .select(IMAPMessageReader.INBOX);
            calmlyAwait.atMost(ONE_MINUTE).until(imapMessageReader::userDoesNotReceiveMessage);
        }
    }

    @Test
    public void rrtServiceShouldDeliverEmailToRecipientOnLocalWhenMappingContainsNonDomain() throws Exception {
        String nonDomainUser = "nondomain";
        String localUser = nonDomainUser + "@" + dataProbe.getDefaultDomain();

        dataProbe.addUser(FROM, PASSWORD);

        createUserInbox(localUser);
        createUserInbox(OTHER_AT_JAMES);

        dataProbe.addAddressMapping("touser", DEFAULT_DOMAIN, nonDomainUser);
        dataProbe.addAddressMapping("touser", DEFAULT_DOMAIN, OTHER_AT_JAMES);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, DEFAULT_DOMAIN)) {
            messageSender.sendMessage(FROM, RECIPIENT)
                .awaitSent(calmlyAwait.atMost(ONE_MINUTE));

            imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
                .login(localUser, PASSWORD)
                .select(IMAPMessageReader.INBOX)
                .awaitMessage(calmlyAwait.atMost(ONE_MINUTE));
            imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
                .login(OTHER_AT_JAMES, PASSWORD)
                .select(IMAPMessageReader.INBOX)
                .awaitMessage(calmlyAwait.atMost(ONE_MINUTE));
        }
    }

    @Test
    public void messageShouldRedirectToTheSameUserWhenDomainMapping() throws Exception {
        dataProbe.addDomainAliasMapping(DEFAULT_DOMAIN, JAMES_ANOTHER_DOMAIN);

        createUserInbox(ANY_AT_JAMES);
        createUserInbox(ANY_AT_ANOTHER_DOMAIN);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, DEFAULT_DOMAIN)) {
            messageSender.sendMessage(FROM, ANY_AT_JAMES)
                .awaitSent(calmlyAwait.atMost(ONE_MINUTE));

            imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
                .login(ANY_AT_ANOTHER_DOMAIN, PASSWORD)
                .select(IMAPMessageReader.INBOX)
                .awaitMessage(calmlyAwait.atMost(ONE_MINUTE));
        }
    }

    @Test
    public void messageShouldNotSendToRecipientWhenDomainMapping() throws Exception {
        dataProbe.addDomainAliasMapping(DEFAULT_DOMAIN, JAMES_ANOTHER_DOMAIN);

        createUserInbox(ANY_AT_JAMES);
        createUserInbox(ANY_AT_ANOTHER_DOMAIN);

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, DEFAULT_DOMAIN)) {
            messageSender.sendMessage(FROM, ANY_AT_JAMES)
                .awaitSent(calmlyAwait.atMost(ONE_MINUTE));

            imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
                .login(ANY_AT_JAMES, PASSWORD)
                .select(IMAPMessageReader.INBOX);
            calmlyAwait.atMost(ONE_MINUTE).until(imapMessageReader::userDoesNotReceiveMessage);
        }
    }

    private void createUserInbox(String username) throws Exception {
        dataProbe.addUser(username, PASSWORD);
        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, username, "INBOX");
    }

}
