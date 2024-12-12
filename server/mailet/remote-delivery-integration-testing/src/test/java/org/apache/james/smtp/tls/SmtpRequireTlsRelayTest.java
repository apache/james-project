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

package org.apache.james.smtp.tls;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.MemoryJamesServerMain.SMTP_AND_IMAP_MODULE;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.apache.james.mailets.configuration.Constants.calmlyAwait;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.TEN_SECONDS;

import java.io.File;
import java.io.IOException;
import java.util.Base64;

import org.apache.commons.net.smtp.SMTPSClient;
import org.apache.commons.net.smtp.SimpleSMTPHeader;
import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.mock.smtp.server.model.Mail;
import org.apache.james.mock.smtp.server.model.SMTPExtension;
import org.apache.james.mock.smtp.server.model.SMTPExtensions;
import org.apache.james.mock.smtp.server.testing.MockSmtpServerExtension;
import org.apache.james.mock.smtp.server.testing.MockSmtpServerExtension.DockerMockSmtp;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;
import org.apache.james.smtpserver.dsn.DSNEhloHook;
import org.apache.james.smtpserver.dsn.DSNMailParameterHook;
import org.apache.james.smtpserver.dsn.DSNMessageHook;
import org.apache.james.smtpserver.dsn.DSNRcptParameterHook;
import org.apache.james.smtpserver.priority.SmtpMtPriorityEhloHook;
import org.apache.james.smtpserver.priority.SmtpMtPriorityMessageHook;
import org.apache.james.smtpserver.priority.SmtpMtPriorityParameterHook;
import org.apache.james.smtpserver.tls.SmtpRequireTlsEhloHook;
import org.apache.james.smtpserver.tls.SmtpRequireTlsMessageHook;
import org.apache.james.smtpserver.tls.SmtpRequireTlsParameterHook;
import org.apache.james.transport.mailets.RemoteDelivery;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.TestIMAPClient;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class SmtpRequireTlsRelayTest {
    private static final String ANOTHER_DOMAIN = "other.com";
    private static final String FROM = "from@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT = "touser@" + ANOTHER_DOMAIN;

    private InMemoryDNSService inMemoryDNSService;

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public static MockSmtpServerExtension mockSmtpExtension = new MockSmtpServerExtension();

    private TemporaryJamesServer jamesServer;

    @BeforeEach
    void setUp(@TempDir File temporaryFolder, DockerMockSmtp mockSmtp, TestInfo testInfo) throws Exception {
        boolean usePriority = testInfo.getTags().stream().anyMatch(tag -> tag.contains("usePriority=true"));

        inMemoryDNSService = new InMemoryDNSService()
                .registerMxRecord(DEFAULT_DOMAIN, LOCALHOST_IP)
                .registerMxRecord(ANOTHER_DOMAIN, mockSmtp.getIPAddress());

        SmtpConfiguration.Builder smtpConfiguration = SmtpConfiguration.builder()
                .requireStartTls()
                .addHook(DSNEhloHook.class.getName())
                .addHook(DSNMailParameterHook.class.getName())
                .addHook(DSNRcptParameterHook.class.getName())
                .addHook(DSNMessageHook.class.getName())
                .addHook(SmtpRequireTlsEhloHook.class.getName())
                .addHook(SmtpRequireTlsParameterHook.class.getName())
                .addHook(SmtpRequireTlsMessageHook.class.getName())
                .withAutorizedAddresses("0.0.0.0/0.0.0.0");

        if (usePriority) {
            smtpConfiguration
                    .addHook(SmtpMtPriorityEhloHook.class.getName())
                    .addHook(SmtpMtPriorityParameterHook.class.getName())
                    .addHook(SmtpMtPriorityMessageHook.class.getName());
        }

        jamesServer = TemporaryJamesServer.builder()
                .withBase(SMTP_AND_IMAP_MODULE)
                .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
                .withMailetContainer(MailetContainer.builder()
                        .putProcessor(CommonProcessors.simpleRoot())
                        .putProcessor(CommonProcessors.error())
                        .putProcessor(directResolutionTransport(usePriority))
                        .putProcessor(CommonProcessors.bounces()))
                .withSmtpConfiguration(smtpConfiguration)
                .build(temporaryFolder);
        jamesServer.start();

        jamesServer.getProbe(DataProbeImpl.class)
                .fluent()
                .addDomain(DEFAULT_DOMAIN)
                .addUser(FROM, PASSWORD);

        mockSmtp.getConfigurationClient().setSMTPExtensions(SMTPExtensions.of(SMTPExtension.of("STARTTLS"), SMTPExtension.of("dsn")));
        assertThat(mockSmtp.getConfigurationClient().version()).isEqualTo("0.4");
    }

    private ProcessorConfiguration.Builder directResolutionTransport(Boolean usePriority) {
        return ProcessorConfiguration.transport()
                .addMailet(MailetConfiguration.BCC_STRIPPER)
                .addMailet(MailetConfiguration.LOCAL_DELIVERY)
                .addMailet(MailetConfiguration.builder()
                        .mailet(RemoteDelivery.class)
                        .matcher(All.class)
                        .addProperty("usePriority", usePriority.toString())
                        .addProperty("outgoingQueue", "outgoing")
                        .addProperty("delayTime", "3 * 10 ms")
                        .addProperty("maxRetries", "1")
                        .addProperty("maxDnsProblemRetries", "0")
                        .addProperty("deliveryThreads", "2")
                        .addProperty("sendpartial", "true"));
    }

    @Test
    @Tag("usePriority=false")
    void remoteDeliveryShouldRequireTls(DockerMockSmtp mockSmtp) throws Exception {

        SMTPSClient smtpClient = initSMTPSClient();
        smtpClient.mail("<" + FROM + "> REQUIRETLS");
        smtpClient.rcpt("<" + RECIPIENT + ">");
        smtpClient.sendShortMessageData("A short message...");

        calmlyAwait.atMost(TEN_SECONDS).untilAsserted(() -> assertThat(mockSmtp.getConfigurationClient().listMails())
                .hasSize(1)
                .extracting(Mail::getEnvelope)
                .containsExactly(Mail.Envelope.builder()
                        .from(new MailAddress(FROM))
                        .addMailParameter(Mail.Parameter.builder()
                                .name("REQUIRETLS")
                                .build())
                        .addRecipient(Mail.Recipient.builder()
                                .address(new MailAddress(RECIPIENT))
                                .build())
                        .build()));
    }

    @Test
    @Tag("usePriority=false")
    void remoteDeliveryShouldRequireTlsWhenTlsRequiredHeaderExists(DockerMockSmtp mockSmtp) throws Exception {

        SMTPSClient smtpClient = initSMTPSClient();
        SimpleSMTPHeader header = new SimpleSMTPHeader(FROM, RECIPIENT, "Just testing");
        header.addHeaderField("TLS-Required", "No");
        smtpClient.mail("<" + FROM + "> REQUIRETLS");
        smtpClient.rcpt("<" + RECIPIENT + ">");
        smtpClient.sendShortMessageData(header + "A short message...");

        calmlyAwait.atMost(TEN_SECONDS).untilAsserted(() -> assertThat(mockSmtp.getConfigurationClient().listMails())
                .hasSize(1)
                .extracting(Mail::getEnvelope)
                .containsExactly(Mail.Envelope.builder()
                        .from(new MailAddress(FROM))
                        .addMailParameter(Mail.Parameter.builder()
                                .name("REQUIRETLS")
                                .build())
                        .addRecipient(Mail.Recipient.builder()
                                .address(new MailAddress(RECIPIENT))
                                .build())
                        .build()));
    }

    @Test
    @Tag("usePriority=false")
    void remoteDeliveryShouldNotRequireTlsWhenTlsRequiredHeaderExistsAndFromCommandDoesNotContainRequireTls(DockerMockSmtp mockSmtp) throws Exception {

        SMTPSClient smtpClient = initSMTPSClient();
        SimpleSMTPHeader header = new SimpleSMTPHeader(FROM, RECIPIENT, "Just testing");
        header.addHeaderField("TLS-Required", "No");
        smtpClient.mail("<" + FROM + ">");
        smtpClient.rcpt("<" + RECIPIENT + ">");
        smtpClient.sendShortMessageData(header + "A short message...");

        calmlyAwait.atMost(TEN_SECONDS).untilAsserted(() -> assertThat(mockSmtp.getConfigurationClient().listMails())
                .hasSize(1)
                .extracting(Mail::getEnvelope)
                .containsExactly(Mail.Envelope.builder()
                        .from(new MailAddress(FROM))
                        .addRecipient(Mail.Recipient.builder()
                                .address(new MailAddress(RECIPIENT))
                                .build())
                        .build()));
    }

    @Test
    @Tag("usePriority=true")
    void remoteDeliveryWhenShouldRequireTlsAndDsnAndMtPriorityTogether(DockerMockSmtp mockSmtp) throws Exception {
        String expectedPriorityValue = "3";

        SMTPSClient smtpClient = initSMTPSClient();
        smtpClient.mail("<" + FROM + "> MT-PRIORITY=" + expectedPriorityValue + " REQUIRETLS RET=HDRS ENVID=gabouzomeuh");
        smtpClient.rcpt("<" + RECIPIENT + ">");
        smtpClient.sendShortMessageData("A short message...");

        calmlyAwait.atMost(TEN_SECONDS).untilAsserted(() -> assertThat(mockSmtp.getConfigurationClient().listMails())
                .hasSize(1)
                .extracting(Mail::getEnvelope)
                .containsExactly(Mail.Envelope.builder()
                        .from(new MailAddress(FROM))
                        .addMailParameter(Mail.Parameter.builder()
                                .name("MT-PRIORITY")
                                .value(expectedPriorityValue)
                                .build())
                        .addMailParameter(Mail.Parameter.builder()
                                .name("REQUIRETLS")
                                .build())
                        .addMailParameter(Mail.Parameter.builder()
                                .name("RET")
                                .value("HDRS")
                                .build())
                        .addMailParameter(Mail.Parameter.builder()
                                .name("ENVID")
                                .value("gabouzomeuh")
                                .build())
                        .addRecipient(Mail.Recipient.builder()
                                .address(new MailAddress(RECIPIENT))
                                .build())
                        .build()));
    }

    @Test
    void remoteDeliveryShouldFailsWhenServerNotAllowStartTls(DockerMockSmtp mockSmtp) throws Exception {

        mockSmtp.getConfigurationClient().clearSMTPExtensions();

        SMTPSClient smtpClient = initSMTPSClient();
        smtpClient.mail("<" + FROM + "> REQUIRETLS");
        smtpClient.rcpt("<" + RECIPIENT + ">");
        smtpClient.sendShortMessageData("A short message...");

        String dsnMessage = testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(FROM, PASSWORD)
                .select(TestIMAPClient.INBOX)
                .awaitMessageCount(awaitAtMostOneMinute, 1)
                .readFirstMessage();

        Assertions.assertThat(dsnMessage).contains("Mail delivery failed; the receiving server does not support STARTTLS");
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    private void authenticate(SMTPSClient smtpProtocol) throws IOException {
        smtpProtocol.sendCommand("AUTH PLAIN");
        smtpProtocol.sendCommand(Base64.getEncoder().encodeToString(("\0" + FROM + "\0" + PASSWORD + "\0").getBytes(UTF_8)));
        assertThat(smtpProtocol.getReplyCode())
                .as("authenticated")
                .isEqualTo(235);
    }

    private SMTPSClient initSMTPSClient() throws IOException {
        SMTPSClient smtpClient = new SMTPSClient(false, BogusSslContextFactory.getClientContext());
        smtpClient.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        smtpClient.connect("localhost", jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
        smtpClient.execTLS();
        smtpClient.sendCommand("EHLO james.org");
        authenticate(smtpClient);
        return smtpClient;
    }
}
