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


import static org.apache.james.mailets.SPFIntegrationTests.POSTMASTER;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.FROM;
import static org.apache.james.mailets.configuration.Constants.FROM2;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.SubAddressing;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;


class SubAddressingTest {
    private static final String TARGETED_MAILBOX = "any";

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    void setup(File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(TemporaryJamesServer.defaultMailetContainerConfiguration()
                .postmaster(POSTMASTER)
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(SubAddressing.class))
                    .addMailetsFrom(CommonProcessors.transport())))
            .build(temporaryFolder);
        jamesServer.start();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        dataProbe.addUser(FROM, PASSWORD);
        dataProbe.addUser(FROM2, PASSWORD);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(RECIPIENT, PASSWORD);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
        testIMAPClient.close();
        messageSender.close();
    }

    @Test
    void subAddressedEmailShouldBeDeliveredInINBOXWhenSpecifiedFolderDoesNotExist(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder);

        // do not create mailbox

        sendSubAddressedMail();
        awaitSubAddressedMail(MailboxConstants.INBOX);
    }

    @Test
    void subAddressedEmailShouldBeDeliveredInINBOXWhenNobodyHasRight(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder);

        // create mailbox
        testIMAPClient.sendCommand("CREATE " + TARGETED_MAILBOX);

        // do not give posting rights
        testIMAPClient.sendCommand("SETACL " + TARGETED_MAILBOX + " " + "anyone" + " -p");

        sendSubAddressedMail();
        awaitSubAddressedMail(MailboxConstants.INBOX);
    }

    @Test
    void subAddressedEmailShouldBeDeliveredInSpecifiedFolderWhenAnyoneHasRight(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder);

        testIMAPClient.sendCommand("CREATE " + TARGETED_MAILBOX);

        // give posting rights for anyone
        testIMAPClient.sendCommand("SETACL " + TARGETED_MAILBOX + " " + "anyone" + " +p");

        sendSubAddressedMail();
        awaitSubAddressedMail(TARGETED_MAILBOX);
    }

    private void sendSubAddressedMail() throws IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidKeySpecException {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM, "user2+" + TARGETED_MAILBOX + "@" + DEFAULT_DOMAIN);
    }

    private void awaitSubAddressedMail(String expectedMailbox) throws IOException {
        testIMAPClient
            .connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(expectedMailbox)
            .awaitMessage(awaitAtMostOneMinute);
    }
}