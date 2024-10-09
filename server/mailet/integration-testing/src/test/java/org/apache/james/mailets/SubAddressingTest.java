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
import static org.apache.james.mailets.configuration.Constants.RECIPIENT2;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.ACLProbeImpl;
import org.apache.james.modules.MailboxProbeImpl;
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
    private static final String TARGETED_MAILBOX_LOWER = TARGETED_MAILBOX;
    private static final String TARGETED_MAILBOX_UPPER = "ANY";
    private static final String TARGETED_MAILBOX_REQUIRING_ENCODING = "Dossier d'été";
    private static final String TARGETED_MAILBOX_ENCODED = "Dossier%20d%27%C3%A9t%C3%A9";

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
        dataProbe.addUser(RECIPIENT2, PASSWORD);
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

        sendSubAddressedMail(TARGETED_MAILBOX);
        awaitSubAddressedMail(MailboxConstants.INBOX);
    }


    @Test
    void subAddressedEmailShouldBeDeliveredInINBOXWhenSpecifiedFolderExistsForAnotherUser(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder);

        // create mailbox for recipient 1
        testIMAPClient.sendCommand("CREATE " + TARGETED_MAILBOX);
        testIMAPClient.sendCommand("SETACL " + TARGETED_MAILBOX + " " + "anyone" + " p");

        // send to recipient 2
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
                .authenticate(FROM, PASSWORD)
                .sendMessage(FROM, "recipient2+" + TARGETED_MAILBOX + "@" + DEFAULT_DOMAIN);

        testIMAPClient
                .connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(RECIPIENT2, PASSWORD)
                .select(MailboxConstants.INBOX)
                .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    void subAddressedEmailShouldBeDeliveredInSpecifiedFolderWhenItExistsInUpperCase(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder);

        // create mailbox
        testIMAPClient.sendCommand("CREATE " + TARGETED_MAILBOX_UPPER);
        testIMAPClient.sendCommand("SETACL " + TARGETED_MAILBOX_UPPER + " " + "anyone" + " p");

        sendSubAddressedMail(TARGETED_MAILBOX_LOWER);
        awaitSubAddressedMail(TARGETED_MAILBOX_UPPER);
    }

    @Test
    void subAddressedEmailShouldBeDeliveredInSpecifiedFolderWhenItExistsInLowerCase(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder);

        // create mailbox
        testIMAPClient.sendCommand("CREATE " + TARGETED_MAILBOX_LOWER);
        testIMAPClient.sendCommand("SETACL " + TARGETED_MAILBOX_LOWER + " " + "anyone" + " p");

        sendSubAddressedMail(TARGETED_MAILBOX_UPPER);
        awaitSubAddressedMail(TARGETED_MAILBOX_LOWER);
    }

    @Test
    void subAddressedEmailShouldBeDeliveredInSpecifiedFolderWithCorrectLowerCaseWhenSeveralCasesExist(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder);

        // create mailboxes
        testIMAPClient.sendCommand("CREATE " + TARGETED_MAILBOX_LOWER);
        testIMAPClient.sendCommand("SETACL " + TARGETED_MAILBOX_LOWER + " " + "anyone" + " p");
        testIMAPClient.sendCommand("CREATE " + TARGETED_MAILBOX_UPPER);
        testIMAPClient.sendCommand("SETACL " + TARGETED_MAILBOX_UPPER + " " + "anyone" + " p");

        sendSubAddressedMail(TARGETED_MAILBOX_LOWER);
        awaitSubAddressedMail(TARGETED_MAILBOX_LOWER);
    }

    @Test
    void subAddressedEmailShouldBeDeliveredInSpecifiedFolderWithCorrectUpperCaseWhenSeveralCasesExist(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder);

        // create mailboxes
        testIMAPClient.sendCommand("CREATE " + TARGETED_MAILBOX_LOWER);
        testIMAPClient.sendCommand("SETACL " + TARGETED_MAILBOX_LOWER + " " + "anyone" + " p");
        testIMAPClient.sendCommand("CREATE " + TARGETED_MAILBOX_UPPER);
        testIMAPClient.sendCommand("SETACL " + TARGETED_MAILBOX_UPPER + " " + "anyone" + " p");

        sendSubAddressedMail(TARGETED_MAILBOX_UPPER);
        awaitSubAddressedMail(TARGETED_MAILBOX_UPPER);
    }

    @Test
    void subAddressedEmailShouldBeDeliveredInINBOXWhenSpecifiedFolderExistsWithCorrectCaseButNoRightAndOtherCaseButRight(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder);

        // create mailbox with incorrect case and give posting right
        testIMAPClient.sendCommand("CREATE " + TARGETED_MAILBOX_LOWER);
        testIMAPClient.sendCommand("SETACL " + TARGETED_MAILBOX_LOWER + " " + "anyone" + " p");

        // create mailbox with correct case but don't give posting right
        testIMAPClient.sendCommand("CREATE " + TARGETED_MAILBOX_UPPER);

        sendSubAddressedMail(TARGETED_MAILBOX_UPPER);
        awaitSubAddressedMail(MailboxConstants.INBOX);
    }

    @Test
    void subAddressedEmailShouldBeDeliveredInSpecifiedFolderWhenRequiringEncoding(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder);

        MailboxId targetMailboxId = jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxPath.forUser(Username.of(RECIPIENT), TARGETED_MAILBOX_REQUIRING_ENCODING));

        //give posting rights
        jamesServer.getProbe(ACLProbeImpl.class).executeCommand(
                targetMailboxId,
                Username.of(RECIPIENT),
                MailboxACL.command()
                        .key(MailboxACL.ANYONE_KEY)
                        .rights(MailboxACL.Right.Post)
                        .asAddition());

        sendSubAddressedMail(TARGETED_MAILBOX_ENCODED);

        int loadLimit = 1;
        awaitAtMostOneMinute.until(() -> jamesServer.getProbe(MailboxProbeImpl.class)
                .searchMessage(MultimailboxesSearchQuery
                        .from(SearchQuery.builder().build())
                        .inMailboxes(targetMailboxId)
                        .build(),
                    RECIPIENT,
                    loadLimit)
                .size() == 1);
    }

    @Test
    void subAddressedEmailShouldBeDeliveredInINBOXWhenNobodyHasRight(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder);

        // create mailbox
        testIMAPClient.sendCommand("CREATE " + TARGETED_MAILBOX);

        // do not give posting rights
        testIMAPClient.sendCommand("SETACL " + TARGETED_MAILBOX + " " + "anyone" + " -p");

        sendSubAddressedMail(TARGETED_MAILBOX);
        awaitSubAddressedMail(MailboxConstants.INBOX);
    }

    @Test
    void subAddressedEmailShouldBeDeliveredInSpecifiedFolderWhenAnyoneHasRight(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder);

        testIMAPClient.sendCommand("CREATE " + TARGETED_MAILBOX);

        // give posting rights for anyone
        testIMAPClient.sendCommand("SETACL " + TARGETED_MAILBOX + " " + "anyone" + " +p");

        sendSubAddressedMail(TARGETED_MAILBOX);
        awaitSubAddressedMail(TARGETED_MAILBOX);
    }

    private void sendSubAddressedMail(String targetMailbox) throws IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidKeySpecException {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(FROM,"recipient+" + targetMailbox + "@" + DEFAULT_DOMAIN);
    }

    private void awaitSubAddressedMail(String expectedMailbox) throws IOException {
        testIMAPClient
            .connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(expectedMailbox)
            .awaitMessage(awaitAtMostOneMinute);
    }
}