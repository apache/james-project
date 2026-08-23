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

package org.apache.james.smtp.utf8;

import static org.apache.james.MemoryJamesServerMain.SMTP_AND_IMAP_MODULE;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.calmlyAwait;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.TEN_SECONDS;

import java.io.File;

import org.apache.commons.net.smtp.SMTPClient;
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
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.transport.mailets.RecipientRewriteTable;
import org.apache.james.transport.mailets.RemoteDelivery;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end RFC 6531: a UTF-8 envelope enters over SMTPUTF8 and leaves through
 * RemoteDelivery. Covers what {@code SmtpUtf8StrategyTest} cannot -- the strategy's
 * verdict is one thing, what James actually puts on the wire is another. Angus
 * reads {@code mail.mime.allowutf8} in the SMTPTransport constructor, so setting
 * it after the transport is built silently disables the extension, and only a
 * test at this level notices.
 */
class SmtpUtf8RelayTest {
    private static final String ANOTHER_DOMAIN = "other.com";
    private static final String FROM = "from@" + DEFAULT_DOMAIN;
    private static final String ASCII_RECIPIENT = "touser@" + ANOTHER_DOMAIN;
    private static final String UTF8_RECIPIENT = "réception@" + ANOTHER_DOMAIN;

    @RegisterExtension
    public static MockSmtpServerExtension mockSmtpExtension = new MockSmtpServerExtension();

    private TemporaryJamesServer jamesServer;

    @BeforeEach
    void setUp(@TempDir File temporaryFolder, DockerMockSmtp mockSmtp) throws Exception {
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(DEFAULT_DOMAIN, LOCALHOST_IP)
            .registerMxRecord(ANOTHER_DOMAIN, mockSmtp.getIPAddress());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(MailetContainer.builder()
                .putProcessor(CommonProcessors.simpleRoot())
                .putProcessor(CommonProcessors.error())
                .putProcessor(directResolutionTransport())
                .putProcessor(CommonProcessors.bounces()))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN);
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    private ProcessorConfiguration.Builder directResolutionTransport() {
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(RecipientRewriteTable.class))
            .addMailet(MailetConfiguration.builder()
                .mailet(RemoteDelivery.class)
                .matcher(All.class)
                .addProperty("outgoing", "outgoing")
                .addProperty("delayTime", "3 * 10 ms")
                .addProperty("maxRetries", "3")
                .addProperty("deliveryThreads", "2")
                .addProperty("sendpartial", "true"));
    }

    @Test
    void remoteDeliveryShouldAssertSmtpUtf8WhenRemoteAdvertisesIt(DockerMockSmtp mockSmtp) throws Exception {
        mockSmtp.getConfigurationClient().setSMTPExtensions(SMTPExtensions.of(SMTPExtension.of("SMTPUTF8")));

        sendWithSmtpUtf8(FROM, UTF8_RECIPIENT);

        calmlyAwait.atMost(TEN_SECONDS).untilAsserted(() -> assertThat(mockSmtp.getConfigurationClient().listMails())
            .hasSize(1)
            .extracting(Mail::getEnvelope)
            .containsExactly(Mail.Envelope.builder()
                .from(new MailAddress(FROM))
                .addMailParameter(Mail.Parameter.builder()
                    .name("SMTPUTF8")
                    .build())
                .addRecipient(Mail.Recipient.builder()
                    .address(new MailAddress(UTF8_RECIPIENT))
                    .build())
                .build()));
    }

    @Test
    void remoteDeliveryShouldNotAssertSmtpUtf8ForAnAsciiEnvelope(DockerMockSmtp mockSmtp) throws Exception {
        mockSmtp.getConfigurationClient().setSMTPExtensions(SMTPExtensions.of(SMTPExtension.of("SMTPUTF8")));

        sendWithSmtpUtf8(FROM, ASCII_RECIPIENT);

        calmlyAwait.atMost(TEN_SECONDS).untilAsserted(() -> assertThat(mockSmtp.getConfigurationClient().listMails())
            .hasSize(1)
            .extracting(Mail::getEnvelope)
            .containsExactly(Mail.Envelope.builder()
                .from(new MailAddress(FROM))
                .addRecipient(Mail.Recipient.builder()
                    .address(new MailAddress(ASCII_RECIPIENT))
                    .build())
                .build()));
    }

    @Test
    void remoteDeliveryShouldNotRelayAUnicodeLocalPartWhenRemoteLacksSmtpUtf8(DockerMockSmtp mockSmtp) throws Exception {
        // No SMTPUTF8 advertised, and a non-ASCII local part has no lossless
        // downgrade: the transaction must fail rather than mangle the address.
        mockSmtp.getConfigurationClient().setSMTPExtensions(SMTPExtensions.of());

        sendWithSmtpUtf8(FROM, UTF8_RECIPIENT);

        Thread.sleep(2000);
        assertThat(mockSmtp.getConfigurationClient().listMails()).isEmpty();
    }

    private void sendWithSmtpUtf8(String from, String recipient) throws Exception {
        SMTPClient smtpClient = new SMTPClient("UTF-8");
        try {
            smtpClient.connect("localhost", jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
            smtpClient.sendCommand("EHLO " + DEFAULT_DOMAIN);
            smtpClient.sendCommand("MAIL FROM:<" + from + "> SMTPUTF8");
            assertThat(smtpClient.getReplyCode()).isEqualTo(250);
            smtpClient.sendCommand("RCPT TO:<" + recipient + ">");
            assertThat(smtpClient.getReplyCode()).isEqualTo(250);
            smtpClient.sendShortMessageData("From: " + from + "\r\nSubject: test\r\n\r\nbody\r\n.\r\n");
        } finally {
            smtpClient.disconnect();
        }
    }
}
