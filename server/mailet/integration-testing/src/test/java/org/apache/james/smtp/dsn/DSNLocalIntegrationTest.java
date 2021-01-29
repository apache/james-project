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

package org.apache.james.smtp.dsn;

import static org.apache.james.MemoryJamesServerMain.SMTP_AND_IMAP_MODULE;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.awaitility.Durations.FIVE_SECONDS;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import org.apache.commons.net.smtp.AuthenticatingSMTPClient;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.smtpserver.dsn.DSNEhloHook;
import org.apache.james.smtpserver.dsn.DSNMailParameterHook;
import org.apache.james.smtpserver.dsn.DSNMessageHook;
import org.apache.james.smtpserver.dsn.DSNRcptParameterHook;
import org.apache.james.transport.mailets.DSNBounce;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.transport.mailets.RecipientRewriteTable;
import org.apache.james.transport.mailets.ToProcessor;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.DSNFailureRequested;
import org.apache.james.transport.matchers.DSNSuccessRequested;
import org.apache.james.transport.matchers.RecipientIs;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DSNLocalIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DSNLocalIntegrationTest.class);

    private static final String FROM = "from@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT = "touser@" + DEFAULT_DOMAIN;
    private static final String FAILING_RECIPIENT = "failing@" + DEFAULT_DOMAIN;
    public static final ConditionFactory AWAIT_NO_MESSAGE = Awaitility.with().pollDelay(Duration.ofSeconds(2)).timeout(FIVE_SECONDS);

    private InMemoryDNSService inMemoryDNSService;

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @BeforeEach
    void setUp(@TempDir File temporaryFolder) throws Exception {
        inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(DEFAULT_DOMAIN, LOCALHOST_IP);

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.simpleRoot())
                .putProcessor(CommonProcessors.error())
                .putProcessor(localDelivery())
                .putProcessor(ProcessorConfiguration.bounces()
                    .enableJmx(false)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(DSNFailureRequested.class)
                        .mailet(DSNBounce.class)
                        .addProperty("defaultStatus", "5.0.0")
                        .addProperty("action", "failed")
                        .addProperty("prefix", "[FAILURE]")
                        .addProperty("messageString", "Your message failed to be delivered")
                    )))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .addHook(DSNEhloHook.class.getName())
                .addHook(DSNMailParameterHook.class.getName())
                .addHook(DSNRcptParameterHook.class.getName())
                .addHook(DSNMessageHook.class.getName()))
            .build(temporaryFolder);
        jamesServer.start();

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(FAILING_RECIPIENT, PASSWORD)
            .addUser(RECIPIENT, PASSWORD);

    }

    private ProcessorConfiguration.Builder localDelivery() {
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(RecipientRewriteTable.class))
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIs.class).matcherCondition(FAILING_RECIPIENT)
                .mailet(ToProcessor.class)
                .addProperty("processor", ProcessorConfiguration.STATE_BOUNCES))
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(LocalDelivery.class)
                .addProperty("consume", "false"))
            .addMailet(MailetConfiguration.builder()
                .matcher(DSNSuccessRequested.class)
                .mailet(DSNBounce.class)
                .addProperty("defaultStatus", "2.0.0")
                .addProperty("action", "delivered")
                .addProperty("prefix", "[SUCCESS]")
                .addProperty("messageString", "Your message was successfully delivered")
            );
    }

    @Test
    void givenAMailWithNoNotifyWhenItSucceedsThenNoDsnIsSentBack() throws IOException {
        AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient("TLS", "UTF-8");

        try {
            smtpClient.connect("localhost", jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
            smtpClient.ehlo(DEFAULT_DOMAIN);
            smtpClient.mail("<" + FROM + ">");
            smtpClient.rcpt("<" + RECIPIENT + ">");
            smtpClient.sendShortMessageData("A short message...");
        } finally {
            smtpClient.disconnect();
        }

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitNoMessage(AWAIT_NO_MESSAGE);
    }

    @Test
    void givenAMailWithNotifyNeverWhenItSucceedThenNoDsnIsSentBack() throws IOException {
        AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient("TLS", "UTF-8");

        try {
            smtpClient.connect("localhost", jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
            smtpClient.ehlo(DEFAULT_DOMAIN);
            smtpClient.mail("<" + FROM + ">");
            smtpClient.rcpt("<" + RECIPIENT + "> NOTIFY=NEVER");
            smtpClient.sendShortMessageData("A short message...");
        } finally {
            smtpClient.disconnect();
        }

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitNoMessage(AWAIT_NO_MESSAGE);
    }

    @Test
    void givenAMailWithNotifySuccessWhenItSucceedThenADsnSuccessIsSentBack() throws IOException {
        AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient("TLS", "UTF-8");

        try {
            smtpClient.connect("localhost", jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
            smtpClient.ehlo(DEFAULT_DOMAIN);
            smtpClient.mail("<" + FROM + ">");
            smtpClient.rcpt("<" + RECIPIENT + "> NOTIFY=SUCCESS");
            smtpClient.sendShortMessageData("A short message...");
        } finally {
            smtpClient.disconnect();
        }

        String dsnMessage = testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessageCount(awaitAtMostOneMinute, 1)
            .readFirstMessage();

        Assertions.assertThat(dsnMessage).contains("Subject: [SUCCESS]");
        Assertions.assertThat(dsnMessage).contains("Status: 2.0.0");
        Assertions.assertThat(dsnMessage).contains("Your message was successfully delivered\n" +
            "Delivered recipient(s):\n" +
            "touser@james.org");
    }

    @Test
    void givenAMailWithNotifyFailureWhenItSucceedThenNoDsnIsSentBack() throws IOException {
        AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient("TLS", "UTF-8");

        try {
            smtpClient.connect("localhost", jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
            smtpClient.ehlo(DEFAULT_DOMAIN);
            smtpClient.mail("<" + FROM + ">");
            smtpClient.rcpt("<" + RECIPIENT + "> NOTIFY=FAILURE");
            smtpClient.sendShortMessageData("A short message...");
        } finally {
            smtpClient.disconnect();
        }

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitNoMessage(AWAIT_NO_MESSAGE);
    }

    @Test
    void givenAMailWithNoNotifyWhenItFailsThenADSNBounceIsSentBack() throws IOException {
        AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient("TLS", "UTF-8");

        try {
            smtpClient.connect("localhost", jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
            smtpClient.ehlo(DEFAULT_DOMAIN);
            smtpClient.mail("<" + FROM + ">");
            smtpClient.rcpt("<" + FAILING_RECIPIENT + ">");
            smtpClient.sendShortMessageData("A short message...");
        } finally {
            smtpClient.disconnect();
        }

        String dsnMessage = testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessageCount(awaitAtMostOneMinute, 1)
            .readFirstMessage();

        Assertions.assertThat(dsnMessage).contains("Subject: [FAILURE]");
        Assertions.assertThat(dsnMessage).contains("Status: 5.0.0");
        Assertions.assertThat(dsnMessage).contains("Your message failed to be delivered\n" +
            "Failed recipient(s):\n" +
            "failing@james.org");
    }

    @Test
    void givenAMailWithNotifyNeverWhenItFailsThenNoEmailIsSentBack() throws IOException, InterruptedException {
        AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient("TLS", "UTF-8");

        try {
            smtpClient.connect("localhost", jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
            smtpClient.ehlo(DEFAULT_DOMAIN);
            smtpClient.mail("<" + FROM + ">");
            smtpClient.rcpt("<" + FAILING_RECIPIENT + "> NOTIFY=NEVER");
            smtpClient.sendShortMessageData("A short message...");
        } finally {
            smtpClient.disconnect();
        }

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitNoMessage(AWAIT_NO_MESSAGE);
    }

    @Test
    void givenAMailWithNotifySuccessWhenItFailsThenNoBounceIsSentBack() throws IOException {
        AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient("TLS", "UTF-8");

        try {
            smtpClient.connect("localhost", jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
            smtpClient.ehlo(DEFAULT_DOMAIN);
            smtpClient.mail("<" + FROM + ">");
            smtpClient.rcpt("<" + FAILING_RECIPIENT + "> NOTIFY=SUCCESS");
            smtpClient.sendShortMessageData("A short message...");
        } finally {
            smtpClient.disconnect();
        }

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitNoMessage(AWAIT_NO_MESSAGE);
    }

    @Test
    void givenAMailWithNotifyFailureWhenItFailsThenADsnBounceIsSentBack() throws IOException {
        AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient("TLS", "UTF-8");

        try {
            smtpClient.connect("localhost", jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
            smtpClient.ehlo(DEFAULT_DOMAIN);
            smtpClient.mail("<" + FROM + ">");
            smtpClient.rcpt("<" + FAILING_RECIPIENT + "> NOTIFY=FAILURE");
            smtpClient.sendShortMessageData("A short message...");
        } finally {
            smtpClient.disconnect();
        }

       String dsnMessage = testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessageCount(awaitAtMostOneMinute, 1)
           .readFirstMessage();

        Assertions.assertThat(dsnMessage).contains("Subject: [FAILURE]");
        Assertions.assertThat(dsnMessage).contains("Status: 5.0.0");
        Assertions.assertThat(dsnMessage).contains("Your message failed to be delivered\n" +
            "Failed recipient(s):\n" +
            "failing@james.org");
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }
}
