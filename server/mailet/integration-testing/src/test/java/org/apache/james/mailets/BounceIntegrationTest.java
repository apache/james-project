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
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Arrays;

import jakarta.mail.internet.MimeMessage;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.transport.mailets.Bounce;
import org.apache.james.transport.mailets.DSNBounce;
import org.apache.james.transport.mailets.Forward;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.transport.mailets.NotifyPostmaster;
import org.apache.james.transport.mailets.NotifySender;
import org.apache.james.transport.mailets.Redirect;
import org.apache.james.transport.mailets.Resend;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIs;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.mailet.Mailet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class BounceIntegrationTest {
    public static final String POSTMASTER = "postmaster@" + DEFAULT_DOMAIN;
    public static final String POSTMASTER_PASSWORD = "postmasterSecret";
    public static final String SENDER = "bounce.receiver@" + DEFAULT_DOMAIN;
    public static final String SENDER_PASSWORD = "senderSecret";
    public static final String OTHER = "other@" + DEFAULT_DOMAIN;
    public static final String OTHER_PASSWORD = "otherSecret";

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void dsnBounceMailetShouldDeliverBounce(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder, DSNBounce.class);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, SENDER_PASSWORD)
            .sendMessage(SENDER, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(SENDER, SENDER_PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    void dsnBounceMailetShouldDeliverBounceToTheMailFromAddress(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder, DSNBounce.class);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, SENDER_PASSWORD)
            .sendMessageWithHeaders(SENDER, RECIPIENT,
                "From: " + OTHER + "\r\n" +
                    "To: " + RECIPIENT + "\r\n" +
                    "Subject: " + "Hello\r\n" +
                    "\r\n" +
                    "Please bounce me to the return address\r\n"
            );

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(SENDER, SENDER_PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    void dsnBounceMailetBouncedMailShouldBeAdressedToTheSenderInEnvelopeAndHeader(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder, DSNBounce.class);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, SENDER_PASSWORD)
            .sendMessageWithHeaders(SENDER, RECIPIENT,
                "From: " + OTHER + "\r\n" +
                    "To: " + RECIPIENT + "\r\n" +
                    "Subject: " + "Hello\r\n" +
                    "\r\n" +
                    "Please bounce me to the return address\r\n"
            );

        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromString(testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(SENDER, SENDER_PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute)
            .readFirstMessage());
        assertThat(mimeMessage.getHeader("To")[0]).isEqualTo(SENDER);
    }

    private void setup(File tempDir, Class<? extends Mailet> mailet, Pair<String, String>... additionalProperties) throws Exception {
        MailetConfiguration.Builder mailetConfiguration = MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(mailet)
                .addProperty("passThrough", "false");

        Arrays.stream(additionalProperties)
                    .forEach(property -> mailetConfiguration.addProperty(property.getKey(), property.getValue()));

        jamesServer = TemporaryJamesServer.builder()
                .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
                .withMailetContainer(
                        generateMailetContainerConfiguration(mailetConfiguration))
                .build(tempDir);
        jamesServer.start();

        jamesServer.getProbe(DataProbeImpl.class)
                .fluent()
                .addDomain(DEFAULT_DOMAIN)
                .addUser(RECIPIENT, PASSWORD)
                .addUser("any@" + DEFAULT_DOMAIN, PASSWORD)
                .addUser(SENDER, SENDER_PASSWORD)
                .addUser(OTHER, OTHER_PASSWORD)
                .addUser(POSTMASTER, POSTMASTER_PASSWORD);
    }

    @Test
    void bounceMailetShouldDeliverBounce(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder, Bounce.class);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, SENDER_PASSWORD)
            .sendMessage(SENDER, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(SENDER, SENDER_PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    void forwardMailetShouldDeliverBounce(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder, Forward.class, Pair.of("forwardTo", SENDER));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate("any@" + DEFAULT_DOMAIN, PASSWORD)
            .sendMessage("any@" + DEFAULT_DOMAIN, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(SENDER, SENDER_PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    void redirectMailetShouldDeliverBounce(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder, Redirect.class, Pair.of("recipients", SENDER));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate("any@" + DEFAULT_DOMAIN, PASSWORD)
            .sendMessage("any@" + DEFAULT_DOMAIN, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(SENDER, SENDER_PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    void resendMailetShouldDeliverBounce(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder, Resend.class, Pair.of("recipients", SENDER));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate("any@" + DEFAULT_DOMAIN, PASSWORD)
            .sendMessage("any@" + DEFAULT_DOMAIN, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(SENDER, SENDER_PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    void notifySenderMailetShouldDeliverBounce(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder, NotifySender.class);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, SENDER_PASSWORD)
            .sendMessage(SENDER, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(SENDER, SENDER_PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    @Test
    void notifyPostmasterMailetShouldDeliverBounce(@TempDir File temporaryFolder) throws Exception {
        setup(temporaryFolder, NotifyPostmaster.class);

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate("any@" + DEFAULT_DOMAIN, PASSWORD)
            .sendMessage("any@" + DEFAULT_DOMAIN, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(POSTMASTER, POSTMASTER_PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    private MailetContainer.Builder generateMailetContainerConfiguration(MailetConfiguration.Builder redirectionMailetConfiguration) {
        return TemporaryJamesServer.defaultMailetContainerConfiguration()
            .postmaster(POSTMASTER)
            .putProcessor(transport())
            .putProcessor(bounces(redirectionMailetConfiguration));
    }

    private ProcessorConfiguration.Builder transport() {
        // This processor delivers emails to SENDER and POSTMASTER
        // Other recipients will be bouncing
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIs.class)
                .matcherCondition(SENDER)
                .mailet(LocalDelivery.class))
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIs.class)
                .matcherCondition(POSTMASTER)
                .mailet(LocalDelivery.class))
            .addMailet(MailetConfiguration.TO_BOUNCE);
    }

    public static ProcessorConfiguration.Builder bounces(MailetConfiguration.Builder redirectionMailetConfiguration) {
        return ProcessorConfiguration.bounces()
            .addMailet(redirectionMailetConfiguration);
    }
}
