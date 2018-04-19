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
import static org.apache.james.mailets.configuration.Constants.IMAP_PORT;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.SMTP_PORT;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

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

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;
    private DataProbe dataProbe;

    @Before
    public void setup() throws Exception {
        jamesServer = TemporaryJamesServer.builder().build(temporaryFolder);

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addDomain(JAMES_ANOTHER_DOMAIN);

        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(ANY_AT_JAMES, PASSWORD);
        dataProbe.addUser(OTHER_AT_JAMES, PASSWORD);
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void rrtServiceShouldNotImpactRecipientsNotMatchingAnyRRT() throws Exception {
        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FROM, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(RECIPIENT, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).isNotNull();
    }

    @Test
    public void rrtServiceShouldDeliverEmailToMappingRecipients() throws Exception {
        dataProbe.addAddressMapping(RECIPIENT_LOCAL_PART, DEFAULT_DOMAIN, ANY_AT_JAMES);
        dataProbe.addAddressMapping(RECIPIENT_LOCAL_PART, DEFAULT_DOMAIN, OTHER_AT_JAMES);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FROM, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(ANY_AT_JAMES, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).isNotNull();
        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(OTHER_AT_JAMES, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).isNotNull();
    }

    @Test
    public void rrtServiceShouldNotDeliverEmailToRecipientWhenHaveMappingRecipients() throws Exception {
        dataProbe.addAddressMapping(RECIPIENT_LOCAL_PART, DEFAULT_DOMAIN, ANY_AT_JAMES);
        dataProbe.addAddressMapping(RECIPIENT_LOCAL_PART, DEFAULT_DOMAIN, OTHER_AT_JAMES);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FROM, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(RECIPIENT, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitNoMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).isNotNull();
    }

    @Test
    public void rrtServiceShouldDeliverEmailToRecipientOnLocalWhenMappingContainsNonDomain() throws Exception {
        String nonDomainUser = "nondomain";
        String localUser = nonDomainUser + "@" + dataProbe.getDefaultDomain();
        dataProbe.addUser(localUser, PASSWORD);

        dataProbe.addAddressMapping(RECIPIENT_LOCAL_PART, DEFAULT_DOMAIN, nonDomainUser);
        dataProbe.addAddressMapping(RECIPIENT_LOCAL_PART, DEFAULT_DOMAIN, OTHER_AT_JAMES);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FROM, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(localUser, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).isNotNull();
        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(OTHER_AT_JAMES, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).isNotNull();
    }

    @Test
    public void messageShouldRedirectToTheSameUserWhenDomainMapping() throws Exception {
        dataProbe.addDomainAliasMapping(DEFAULT_DOMAIN, JAMES_ANOTHER_DOMAIN);
        dataProbe.addUser(ANY_AT_ANOTHER_DOMAIN, PASSWORD);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FROM, ANY_AT_JAMES);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(ANY_AT_ANOTHER_DOMAIN, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).isNotNull();
    }

    @Test
    public void messageShouldNotSendToRecipientWhenDomainMapping() throws Exception {
        dataProbe.addDomainAliasMapping(DEFAULT_DOMAIN, JAMES_ANOTHER_DOMAIN);
        dataProbe.addUser(ANY_AT_ANOTHER_DOMAIN, PASSWORD);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FROM, ANY_AT_JAMES);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(ANY_AT_JAMES, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitNoMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).isNotNull();
    }

    @Test
    public void rrtServiceShouldDeliverEmailToForwardRecipients() throws Exception {
        dataProbe.addForwardMapping(RECIPIENT_LOCAL_PART, DEFAULT_DOMAIN, ANY_AT_JAMES);
        dataProbe.addForwardMapping(RECIPIENT_LOCAL_PART, DEFAULT_DOMAIN, OTHER_AT_JAMES);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FROM, RECIPIENT);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(ANY_AT_JAMES, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(OTHER_AT_JAMES, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).isNotNull();
    }

    @Test
    public void rrtServiceShouldFollowForwardWhenSendingToAGroup() throws Exception {
        dataProbe.addAddressMapping(GROUP_LOCAL_PART, DEFAULT_DOMAIN, ANY_AT_JAMES);

        dataProbe.addForwardMapping(ANY_LOCAL_PART, DEFAULT_DOMAIN, OTHER_AT_JAMES);

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FROM, GROUP);

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(OTHER_AT_JAMES, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(imapMessageReader.readFirstMessage()).isNotNull();
    }
}
