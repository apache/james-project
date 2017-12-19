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
import static org.apache.james.mailets.configuration.AwaitUtils.calmlyAwait;

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

public class CommonMailetConfigurationTest {

    private static final String DEFAULT_DOMAIN = "james.org";
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final int IMAP_PORT = 1143;
    private static final int SMTP_PORT = 1025;
    private static final String PASSWORD = "secret";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private TemporaryJamesServer jamesServer;

    @Before
    public void setup() throws Exception {
        jamesServer = TemporaryJamesServer.builder().build(temporaryFolder);
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void startingJamesWithCommonMailetConfigurationShouldWork() throws Exception {
    }

    @Test
    public void simpleMailShouldBeSent() throws Exception {
        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        String from = "user@" + DEFAULT_DOMAIN;
        dataProbe.addUser(from, PASSWORD);
        String recipient = "user2@" + DEFAULT_DOMAIN;
        dataProbe.addUser(recipient, PASSWORD);
        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, recipient, "INBOX");

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, DEFAULT_DOMAIN);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(from, recipient)
                .awaitSent(calmlyAwait.atMost(ONE_MINUTE));
            calmlyAwait.atMost(ONE_MINUTE).until(() -> imapMessageReader.userReceivedMessage(recipient, PASSWORD));
        }
    }
}
