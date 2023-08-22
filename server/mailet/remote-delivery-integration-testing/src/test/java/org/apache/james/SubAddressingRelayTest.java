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

package org.apache.james;

import static org.apache.james.MemoryJamesServerMain.SMTP_AND_IMAP_MODULE;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.calmlyAwait;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.TEN_SECONDS;

import java.io.File;

import org.apache.commons.net.smtp.AuthenticatingSMTPClient;
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
import org.apache.james.smtpserver.dsn.DSNEhloHook;
import org.apache.james.smtpserver.dsn.DSNMailParameterHook;
import org.apache.james.smtpserver.dsn.DSNMessageHook;
import org.apache.james.smtpserver.dsn.DSNRcptParameterHook;
import org.apache.james.transport.mailets.RecipientRewriteTable;
import org.apache.james.transport.mailets.RemoteDelivery;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class SubAddressingRelayTest {
    private static final String ANOTHER_DOMAIN = "other.com";
    private static final String FROM = "from@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT = "touser+tag@" + ANOTHER_DOMAIN;

    private InMemoryDNSService inMemoryDNSService;

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    @RegisterExtension
    public static MockSmtpServerExtension mockSmtpExtension = new MockSmtpServerExtension();

    private TemporaryJamesServer jamesServer;

    @BeforeEach
    void setUp(@TempDir File temporaryFolder, DockerMockSmtp mockSmtp) throws Exception {
        inMemoryDNSService = new InMemoryDNSService()
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
                .addHook(DSNEhloHook.class.getName())
                .addHook(DSNMailParameterHook.class.getName())
                .addHook(DSNRcptParameterHook.class.getName())
                .addHook(DSNMessageHook.class.getName())
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .build(temporaryFolder);
        jamesServer.start();

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD);

        mockSmtp.getConfigurationClient().setSMTPExtensions(SMTPExtensions.of(SMTPExtension.of("dsn")));

        assertThat(mockSmtp.getConfigurationClient().version()).isEqualTo("0.4");
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void remoteDeliveryShouldCarryOverDSNParameters(DockerMockSmtp mockSmtp) throws Exception {
        AuthenticatingSMTPClient smtpClient = new AuthenticatingSMTPClient("TLS", "UTF-8");

        try {
            smtpClient.connect("localhost", jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
            smtpClient.ehlo(DEFAULT_DOMAIN);
            smtpClient.mail("<" + FROM + ">");
            smtpClient.rcpt("<" + RECIPIENT + "> NOTIFY=FAILURE,DELAY");
            smtpClient.sendShortMessageData("A short message...");
        } finally {
            smtpClient.disconnect();
        }

        calmlyAwait.atMost(TEN_SECONDS).untilAsserted(() -> assertThat(mockSmtp.getConfigurationClient().listMails())
            .hasSize(1)
            .extracting(Mail::getEnvelope)
            .containsOnly(Mail.Envelope.builder()
                .from(new MailAddress(FROM))
                .addRecipient(Mail.Recipient.builder()
                    .address(new MailAddress(RECIPIENT))
                    .addParameter(Mail.Parameter.builder()
                        .name("NOTIFY")
                        .value("FAILURE,DELAY")
                        .build())
                    .build())
                .build()));
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
                .addProperty("outgoingQueue", "outgoing")
                .addProperty("delayTime", "3 * 10 ms")
                .addProperty("maxRetries", "3")
                .addProperty("maxDnsProblemRetries", "0")
                .addProperty("deliveryThreads", "2")
                .addProperty("sendpartial", "true"));
    }
}
