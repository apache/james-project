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

import static org.apache.james.MemoryJamesServerMain.SMTP_AND_IMAP_MODULE;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.apache.james.mock.smtp.server.ConfigurationClient.BehaviorsParamsBuilder.ConditionStep.anyInput;
import static org.apache.james.mock.smtp.server.ConfigurationClient.BehaviorsParamsBuilder.ConditionStep.inputContaining;
import static org.apache.james.mock.smtp.server.ConfigurationClient.BehaviorsParamsBuilder.ResponseStep.doesNotAcceptAnyMail;
import static org.apache.james.mock.smtp.server.ConfigurationClient.BehaviorsParamsBuilder.ResponseStep.serviceNotAvailable;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.InetAddress;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.mock.smtp.server.ConfigurationClient;
import org.apache.james.mock.smtp.server.model.Mail;
import org.apache.james.mock.smtp.server.model.SMTPCommand;
import org.apache.james.mock.smtp.server.testing.MockSmtpServerExtension;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.server.core.MailImpl;
import org.apache.james.transport.mailets.RemoteDelivery;
import org.apache.james.transport.matchers.All;
import org.apache.james.util.Host;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.util.docker.DockerContainer;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.google.common.collect.ImmutableList;

class RemoteDeliveryErrorTest {
    private static final String ANOTHER_DOMAIN = "other.com";
    private static final String FROM = "from@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT = "touser@" + ANOTHER_DOMAIN;
    private static final String RECIPIENT1 = "touser1@" + ANOTHER_DOMAIN;
    private static final String RECIPIENT2 = "touser2@" + ANOTHER_DOMAIN;

    private static final String MIME_MESSAGE = "FROM: " + FROM + "\r\n" +
        "subject: test\r\n" +
        "\r\n" +
        "content\r\n" +
        ".\r\n";
    private static final String BOUNCE_MESSAGE = "Hi. This is the James mail server at localhost.\n" +
        "I'm afraid I wasn't able to deliver your message to the following addresses.\n" +
        "This is a permanent error; I've given up. Sorry it didn't work out. Below\n" +
        "I include the list of recipients and the reason why I was unable to deliver\n" +
        "your message.";

    private static MailAddress FROM_ADDRESS;
    private static MailAddress RECIPIENT_ADDRESS;
    private static MailAddress RECIPIENT1_ADDRESS;
    private static MailAddress RECIPIENT2_ADDRESS;

    private static Mail.Envelope FROM_RECIPIENT_ENVELOPE;
    private static Mail.Envelope FROM_RECIPIENT1_ENVELOPE;
    private static Mail.Envelope FROM_RECIPIENT2_ENVELOPE;

    private InMemoryDNSService inMemoryDNSService;
    private ConfigurationClient mockSMTP1Configuration;
    private ConfigurationClient mockSMTP2Configuration;

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);
    @RegisterExtension
    public static MockSmtpServerExtension mockSmtp1 = new MockSmtpServerExtension();
    @RegisterExtension
    public static MockSmtpServerExtension mockSmtp2 = new MockSmtpServerExtension();

    private TemporaryJamesServer jamesServer;

    @BeforeAll
    static void setUpClass() throws AddressException {
        FROM_ADDRESS = new MailAddress(FROM);
        RECIPIENT_ADDRESS = new MailAddress(RECIPIENT);
        RECIPIENT1_ADDRESS = new MailAddress(RECIPIENT1);
        RECIPIENT2_ADDRESS = new MailAddress(RECIPIENT2);

        FROM_RECIPIENT_ENVELOPE = Mail.Envelope.ofAddresses(FROM_ADDRESS, RECIPIENT_ADDRESS);
        FROM_RECIPIENT1_ENVELOPE = Mail.Envelope.ofAddresses(FROM_ADDRESS, RECIPIENT1_ADDRESS);
        FROM_RECIPIENT2_ENVELOPE = Mail.Envelope.ofAddresses(FROM_ADDRESS, RECIPIENT2_ADDRESS);
    }

    @BeforeEach
    void setUp(@TempDir File temporaryFolder) throws Exception {
        inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(DEFAULT_DOMAIN, LOCALHOST_IP)
            .registerMxRecord(ANOTHER_DOMAIN, mockSmtp1.getMockSmtp().getIPAddress());

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
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD);

        mockSMTP1Configuration = mockSmtp1.getMockSmtp().getConfigurationClient();
        mockSMTP2Configuration = mockSmtp2.getMockSmtp().getConfigurationClient();

        assertThat(mockSMTP1Configuration.version()).isEqualTo("0.4");
        assertThat(mockSMTP2Configuration.version()).isEqualTo("0.4");
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void remoteDeliveryShouldBounceWhenAlwaysRCPT421() throws Exception {
        mockSMTP1Configuration
            .addNewBehavior()
                .expect(SMTPCommand.RCPT_TO)
                .matching(anyInput())
                .thenRespond(serviceNotAvailable("mock response"))
                .anyTimes()
            .post();

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage())
            .contains(BOUNCE_MESSAGE);
    }

    @Test
    void remoteDeliveryShouldBounceWhenAlwaysFROM421() throws Exception {
        mockSMTP1Configuration
            .addNewBehavior()
                .expect(SMTPCommand.MAIL_FROM)
                .matching(anyInput())
                .thenRespond(serviceNotAvailable("mock response"))
                .anyTimes()
            .post();

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage())
            .contains(BOUNCE_MESSAGE);
    }

    @Test
    void remoteDeliveryShouldBounceWhenAlwaysDATA421() throws Exception {
        mockSMTP1Configuration
            .addNewBehavior()
                .expect(SMTPCommand.DATA)
                .matching(anyInput())
                .thenRespond(serviceNotAvailable("mock response"))
                .anyTimes()
            .post();

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage())
            .contains(BOUNCE_MESSAGE);
    }

    @Test
    void remoteDeliveryShouldNotRetryWhenRCPT500() throws Exception {
        mockSMTP1Configuration
            .addNewBehavior()
                .expect(SMTPCommand.RCPT_TO)
                .matching(anyInput())
                .thenRespond(doesNotAcceptAnyMail("mock message"))
                .onlySomeTimes(1)
            .post();

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage())
            .contains(BOUNCE_MESSAGE);
    }

    @Test
    void remoteDeliveryShouldNotRetryWhenFROM500() throws Exception {
        mockSMTP1Configuration
            .addNewBehavior()
                .expect(SMTPCommand.MAIL_FROM)
                .matching(anyInput())
                .thenRespond(doesNotAcceptAnyMail("mock message"))
                .onlySomeTimes(1)
            .post();

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage())
            .contains(BOUNCE_MESSAGE);
    }

    @Test
    void remoteDeliveryShouldNotRetryWhenDATA500() throws Exception {
        mockSMTP1Configuration
            .addNewBehavior()
                .expect(SMTPCommand.DATA)
                .matching(anyInput())
                .thenRespond(doesNotAcceptAnyMail("mock message"))
                .onlySomeTimes(1)
            .post();

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage())
            .contains(BOUNCE_MESSAGE);
    }

    @Test
    void remoteDeliveryShouldRetryWhenRCPT421() throws Exception {
        mockSMTP1Configuration
            .addNewBehavior()
                .expect(SMTPCommand.RCPT_TO)
                .matching(anyInput())
                .thenRespond(serviceNotAvailable("mock response"))
                .onlySomeTimes(2)
            .post();

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        awaitAtMostOneMinute.untilAsserted(() ->
            assertThat(mockSMTP1Configuration.listMails())
                .hasSize(1)
                .anySatisfy(mail -> {
                    assertThat(mail.getEnvelope()).isEqualTo(FROM_RECIPIENT_ENVELOPE);
                    assertThat(mail.getMessage()).contains("subject: test");
                }));
    }

    @Test
    void remoteDeliveryShouldRetryWhenFROM421() throws Exception {
        mockSMTP1Configuration
            .addNewBehavior()
                .expect(SMTPCommand.MAIL_FROM)
                .matching(anyInput())
                .thenRespond(serviceNotAvailable("mock response"))
                .onlySomeTimes(2)
            .post();

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        awaitAtMostOneMinute.untilAsserted(() ->
            assertThat(mockSMTP1Configuration.listMails())
                .hasSize(1)
                .anySatisfy(mail -> {
                    assertThat(mail.getEnvelope()).isEqualTo(FROM_RECIPIENT_ENVELOPE);
                    assertThat(mail.getMessage()).contains("subject: test");
                }));
    }

    @Test
    void remoteDeliveryShouldRetryWhenDATA421() throws Exception {
        mockSMTP1Configuration
            .addNewBehavior()
                .expect(SMTPCommand.DATA)
                .matching(anyInput())
                .thenRespond(serviceNotAvailable("mock response"))
                .onlySomeTimes(2)
            .post();

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(FROM, RECIPIENT);

        awaitAtMostOneMinute.untilAsserted(() ->
            assertThat(mockSMTP1Configuration.listMails())
                .hasSize(1)
                .anySatisfy(mail -> {
                    assertThat(mail.getEnvelope()).isEqualTo(FROM_RECIPIENT_ENVELOPE);
                    assertThat(mail.getMessage()).contains("subject: test");
                }));
    }

    @Test
    void remoteDeliveryShouldNotDuplicateContentWhenSendPartial() throws Exception {
        mockSMTP1Configuration
            .addNewBehavior()
                .expect(SMTPCommand.RCPT_TO)
                .matching(inputContaining(RECIPIENT1))
                .thenRespond(serviceNotAvailable("mock response"))
                .onlySomeTimes(1)
            .post();

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(MailImpl.builder()
                .name("name")
                .sender(new MailAddress(FROM))
                .addRecipient(RECIPIENT1)
                .addRecipient(RECIPIENT2)
                .mimeMessage(MimeMessageUtil.mimeMessageFromString(MIME_MESSAGE))
                .build());

        awaitAtMostOneMinute.untilAsserted(() ->
            assertThat(mockSMTP1Configuration.listMails())
                .hasSize(2)
                .extracting(Mail::getEnvelope)
                .containsOnly(FROM_RECIPIENT1_ENVELOPE, FROM_RECIPIENT2_ENVELOPE));
    }

    @Test
    void remoteDeliveryShouldNotDuplicateContentWhenSendPartialWhenFailover() throws Exception {
        ImmutableList<InetAddress> addresses = ImmutableList.of(InetAddress.getByName(mockSmtp1.getMockSmtp().getIPAddress()));
        ImmutableList<String> mxs = ImmutableList.of(mockSmtp1.getMockSmtp().getIPAddress(), mockSmtp2.getMockSmtp().getIPAddress());
        ImmutableList<String> txtRecords = ImmutableList.of();

        inMemoryDNSService.registerRecord(ANOTHER_DOMAIN, addresses, mxs, txtRecords)
            .registerMxRecord(mockSmtp1.getMockSmtp().getIPAddress(), mockSmtp1.getMockSmtp().getIPAddress())
            .registerMxRecord(mockSmtp2.getMockSmtp().getIPAddress(), mockSmtp2.getMockSmtp().getIPAddress());

        mockSMTP1Configuration
            .addNewBehavior()
                .expect(SMTPCommand.RCPT_TO)
                .matching(inputContaining(RECIPIENT2))
                .thenRespond(serviceNotAvailable("mock response"))
                .anyTimes()
            .post();

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(MailImpl.builder()
                .name("name")
                .sender(new MailAddress(FROM))
                .addRecipient(RECIPIENT1)
                .addRecipient(RECIPIENT2)
                .mimeMessage(MimeMessageUtil.mimeMessageFromString(MIME_MESSAGE))
                .build());

        awaitAtMostOneMinute.untilAsserted(() ->
            assertThat(mockSMTP1Configuration.listMails())
                .hasSize(1)
                .anySatisfy(mail -> {
                    assertThat(mail.getEnvelope()).isEqualTo(FROM_RECIPIENT1_ENVELOPE);
                    assertThat(mail.getMessage()).contains("subject: test");
                }));
        awaitAtMostOneMinute.untilAsserted(() ->
            assertThat(mockSMTP2Configuration.listMails())
                .hasSize(1)
                .anySatisfy(mail -> {
                    assertThat(mail.getEnvelope()).isEqualTo(FROM_RECIPIENT2_ENVELOPE);
                    assertThat(mail.getMessage()).contains("subject: test");
                }));
    }

    private ProcessorConfiguration.Builder directResolutionTransport() {
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(MailetConfiguration.LOCAL_DELIVERY)
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

    private ConfigurationClient configurationClient(DockerContainer mockSmtp) {
        return ConfigurationClient.from(
            Host.from(mockSmtp.getHostIp(),
                mockSmtp.getMappedPort(8000)));
    }
}
