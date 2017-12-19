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
import static org.apache.james.mailets.configuration.Constants.FROM;
import static org.apache.james.mailets.configuration.Constants.IMAP_PORT;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT;
import static org.apache.james.mailets.configuration.Constants.SMTP_PORT;
import static org.apache.james.mailets.configuration.Constants.calmlyAwait;

import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.SieveProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SieveDelivery {
    private static final String TARGETED_MAILBOX = "INBOX.any";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @Before
    public void setup() throws Exception {
        jamesServer = TemporaryJamesServer.builder().build(temporaryFolder);

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);
        dataProbe.addUser(RECIPIENT, PASSWORD);

        jamesServer.getProbe(MailboxProbeImpl.class)
            .createMailbox(MailboxConstants.USER_NAMESPACE, RECIPIENT, TARGETED_MAILBOX);
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void simpleMailShouldBeSent() throws Exception {
        jamesServer.getProbe(SieveProbeImpl.class).addActiveSieveScript(RECIPIENT, "myscript.sieve",
            "require \"fileinto\";\n" +
            "\n" +
            "fileinto \"" + TARGETED_MAILBOX + "\";");

        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(FROM, RECIPIENT)
            .awaitSent(calmlyAwait.atMost(ONE_MINUTE));

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(RECIPIENT, PASSWORD)
            .select(TARGETED_MAILBOX)
            .awaitMessage(calmlyAwait.atMost(ONE_MINUTE));
    }
}
