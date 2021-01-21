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
import static org.apache.james.mock.smtp.server.ConfigurationClient.BehaviorsParamsBuilder.ResponseStep.doesNotAcceptAnyMail;
import static org.apache.james.mock.smtp.server.ConfigurationClient.BehaviorsParamsBuilder.ResponseStep.serviceNotAvailable;
import static org.apache.james.mock.smtp.server.model.Condition.MATCH_ALL;
import static org.apache.james.mock.smtp.server.model.SMTPCommand.RCPT_TO;
import static org.apache.james.util.docker.Images.MOCK_SMTP_SERVER;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.commons.net.smtp.AuthenticatingSMTPClient;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.mock.smtp.server.ConfigurationClient;
import org.apache.james.mock.smtp.server.model.SMTPExtension;
import org.apache.james.mock.smtp.server.model.SMTPExtensions;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.smtpserver.dsn.DSNEhloHook;
import org.apache.james.smtpserver.dsn.DSNMailParameterHook;
import org.apache.james.smtpserver.dsn.DSNMessageHook;
import org.apache.james.smtpserver.dsn.DSNRcptParameterHook;
import org.apache.james.transport.mailets.DSNBounce;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.transport.mailets.RecipientRewriteTable;
import org.apache.james.transport.mailets.RemoteDelivery;
import org.apache.james.transport.mailets.ToProcessor;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.DSNDelayRequested;
import org.apache.james.transport.matchers.DSNFailureRequested;
import org.apache.james.transport.matchers.IsRemoteDeliveryPermanentError;
import org.apache.james.transport.matchers.IsRemoteDeliveryTemporaryError;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.james.util.Host;
import org.apache.james.util.docker.DockerContainer;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DSNRemoteIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DSNRemoteIntegrationTest.class);

    private static final String ANOTHER_DOMAIN = "other.com";
    private static final String FROM = "from@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT = "touser@" + ANOTHER_DOMAIN;
    private static final ConditionFactory AWAIT_NO_MESSAGE = Awaitility.with().pollDelay(new Duration(2, TimeUnit.SECONDS)).timeout(Duration.FIVE_SECONDS);

    private InMemoryDNSService inMemoryDNSService;
    private ConfigurationClient mockSMTPConfiguration;

    @RegisterExtension
    public static DockerContainer mockSmtp = DockerContainer.fromName(MOCK_SMTP_SERVER)
        .withLogConsumer(outputFrame -> LOGGER.debug("MockSMTP 1: " + outputFrame.getUtf8String()));

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @BeforeEach
    void setUp(@TempDir File temporaryFolder) throws Exception {
        inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(DEFAULT_DOMAIN, LOCALHOST_IP)
            .registerMxRecord(ANOTHER_DOMAIN, mockSmtp.getContainerIp());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.simpleRoot())
                .putProcessor(CommonProcessors.error())
                .putProcessor(directResolutionTransport())
                .putProcessor(ProcessorConfiguration.builder().state("relay-bounces")
                    .enableJmx(false)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(IsRemoteDeliveryPermanentError.class)
                        .mailet(ToProcessor.class)
                        .addProperty("processor", "remote-delivery-permanent-error"))
                    .addMailet(MailetConfiguration.builder()
                        .matcher(IsRemoteDeliveryTemporaryError.class)
                        .mailet(ToProcessor.class)
                        .addProperty("processor", "remote-delivery-temporary-error")))
                .putProcessor(ProcessorConfiguration.builder().state("remote-delivery-permanent-error")
                    .enableJmx(false)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(DSNFailureRequested.class)
                        .mailet(DSNBounce.class)
                        .addProperty("defaultStatus", "5.0.0")
                        .addProperty("action", "failed")
                        .addProperty("prefix", "[FAILURE]")
                        .addProperty("messageString", "Your message failed to be delivered")))
                .putProcessor(ProcessorConfiguration.builder().state("remote-delivery-temporary-error")
                    .enableJmx(false)
                    .addMailet(MailetConfiguration.builder()
                        .matcher(DSNDelayRequested.class)
                        .mailet(DSNBounce.class)
                        .addProperty("defaultStatus", "4.0.0")
                        .addProperty("action", "delayed")
                        .addProperty("prefix", "[DELAY]")
                        .addProperty("messageString", "Your message is delayed")))
                .putProcessor(CommonProcessors.bounces()))
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
            .addUser(FROM, PASSWORD);
        mockSMTPConfiguration = configurationClient(mockSmtp);
        mockSMTPConfiguration.setSMTPExtensions(SMTPExtensions.of(SMTPExtension.of("dsn")));

        assertThat(mockSMTPConfiguration.version()).isEqualTo("0.2");
    }

    private ProcessorConfiguration.Builder directResolutionTransport() {
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(RecipientRewriteTable.class))
            .addMailet(MailetConfiguration.builder()
                .mailet(LocalDelivery.class)
                .matcher(RecipientIsLocal.class))
            .addMailet(MailetConfiguration.builder()
                .mailet(RemoteDelivery.class)
                .matcher(All.class)
                .addProperty("sendpartial", "true")
                .addProperty("maxRetries", "2")
                .addProperty("delayTime", "2 * 200 ms")
                .addProperty("bounceProcessor", "relay-bounces"));
    }

    @Test
    void givenAMailWithNoNotifyWhenItSucceedsThenNoEmailIsSentBack() throws Exception {
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
    void givenAMailWithNotifyNeverWhenItSucceedThenNoDsnIsSentBack() throws Exception {
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

    @Disabled("JAMES-3431 DSN relayed notifications cannot be generated as RemoteDelivery lacks a 'success' callback")
    @Test
    void givenAMailWithNotifySuccessWhenItSucceedThenADsnSuccessIsSentBack() throws Exception {
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
            "touser@other.com");
    }

    @Test
    void givenAMailWithNotifyFailureWhenItSucceedThenNoEmailIsSentBack() throws Exception {
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
    void givenAMailWithNoNotifyWhenItFailsThenADSNBounceIsSentBack() throws Exception {
        AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient("TLS", "UTF-8");
        mockSMTPConfiguration.addNewBehavior()
            .expect(RCPT_TO)
            .matching(MATCH_ALL)
            .thenRespond(doesNotAcceptAnyMail("mock response"))
            .anyTimes()
            .post();

        try {
            smtpClient.connect("localhost", jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
            smtpClient.ehlo(DEFAULT_DOMAIN);
            smtpClient.mail("<" + FROM + ">");
            smtpClient.rcpt("<" + RECIPIENT + ">");
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
        Assertions.assertThat(dsnMessage).contains("Status: 521 mock response");
        Assertions.assertThat(dsnMessage).contains("Your message failed to be delivered\n" +
            "Failed recipient(s):\n" +
            "touser@other.com");
    }

    @Test
    void givenAMailWithNotifyNeverWhenItFailsThenNoEmailIsSentBack() throws Exception {
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
    void givenAMailWithNotifyFailureWhenItFailsThenADsnBounceIsSentBack() throws Exception {
        AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient("TLS", "UTF-8");
        mockSMTPConfiguration.addNewBehavior()
            .expect(RCPT_TO)
            .matching(MATCH_ALL)
            .thenRespond(doesNotAcceptAnyMail("mock response"))
            .anyTimes()
            .post();

        try {
            smtpClient.connect("localhost", jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
            smtpClient.ehlo(DEFAULT_DOMAIN);
            smtpClient.mail("<" + FROM + ">");
            smtpClient.rcpt("<" + RECIPIENT + "> NOTIFY=FAILURE");
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
        Assertions.assertThat(dsnMessage).contains("Status: 521 mock response");
        Assertions.assertThat(dsnMessage).contains("Your message failed to be delivered\n" +
            "Failed recipient(s):\n" +
            "touser@other.com");
    }

    @Test
    void givenAMailWithNotifyDelayWhenItIsDelayedThenADsnDelayedIsSentBack() throws Exception {
        AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient("TLS", "UTF-8");
        mockSMTPConfiguration.addNewBehavior()
            .expect(RCPT_TO)
            .matching(MATCH_ALL)
            .thenRespond(serviceNotAvailable("mock_response"))
            .anyTimes()
            .post();

        try {
            smtpClient.connect("localhost", jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
            smtpClient.ehlo(DEFAULT_DOMAIN);
            smtpClient.mail("<" + FROM + ">");
            smtpClient.rcpt("<" + RECIPIENT + "> NOTIFY=DELAY");
            smtpClient.sendShortMessageData("A short message...");
        } finally {
            smtpClient.disconnect();
        }

        String dsnMessage = testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessageCount(awaitAtMostOneMinute, 1)
            .readFirstMessage();

        Assertions.assertThat(dsnMessage).contains("Subject: [DELAY]");
        Assertions.assertThat(dsnMessage).contains("Too many retries failure. Bouncing after 2 retries.");
        Assertions.assertThat(dsnMessage).contains("Your message is delayed\n" +
            "Delayed recipient(s):\n" +
            "touser@other.com");
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
        mockSMTPConfiguration.cleanServer();
    }

    private ConfigurationClient configurationClient(DockerContainer mockSmtp) {
        return ConfigurationClient.from(
            Host.from(mockSmtp.getHostIp(),
                mockSmtp.getMappedPort(8000)));
    }
}
