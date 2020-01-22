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

import static org.apache.james.MemoryJamesServerMain.SMTP_ONLY_MODULE;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.apache.james.util.docker.Images.MOCK_SMTP_SERVER;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.jdkim.api.PublicKeyRecordRetriever;
import org.apache.james.jdkim.mailets.ConvertTo7Bit;
import org.apache.james.jdkim.mailets.DKIMSign;
import org.apache.james.jdkim.mailets.DKIMVerifier;
import org.apache.james.jdkim.mailets.MockPublicKeyRecordRetriever;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mock.smtp.server.ConfigurationClient;
import org.apache.james.mock.smtp.server.model.Mail;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.matchers.All;
import org.apache.james.util.Host;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.util.docker.DockerContainer;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.mailet.base.test.FakeMail;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteDeliveryDKIMIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteDeliveryDKIMIntegrationTest.class);

    private static final String JAMES_ANOTHER_DOMAIN = "james.com";
    private static final String FROM = "from@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT = "touser@" + JAMES_ANOTHER_DOMAIN;

    private static final String TESTING_PEM = "-----BEGIN RSA PRIVATE KEY-----\r\n" +
        "MIICXAIBAAKBgQDYDaYKXzwVYwqWbLhmuJ66aTAN8wmDR+rfHE8HfnkSOax0oIoT\r\n" +
        "M5zquZrTLo30870YMfYzxwfB6j/Nz3QdwrUD/t0YMYJiUKyWJnCKfZXHJBJ+yfRH\r\n" +
        "r7oW+UW3cVo9CG2bBfIxsInwYe175g9UjyntJpWueqdEIo1c2bhv9Mp66QIDAQAB\r\n" +
        "AoGBAI8XcwnZi0Sq5N89wF+gFNhnREFo3rsJDaCY8iqHdA5DDlnr3abb/yhipw0I\r\n" +
        "/1HlgC6fIG2oexXOXFWl+USgqRt1kTt9jXhVFExg8mNko2UelAwFtsl8CRjVcYQO\r\n" +
        "cedeH/WM/mXjg2wUqqZenBmlKlD6vNb70jFJeVaDJ/7n7j8BAkEA9NkH2D4Zgj/I\r\n" +
        "OAVYccZYH74+VgO0e7VkUjQk9wtJ2j6cGqJ6Pfj0roVIMUWzoBb8YfErR8l6JnVQ\r\n" +
        "bfy83gJeiQJBAOHk3ow7JjAn8XuOyZx24KcTaYWKUkAQfRWYDFFOYQF4KV9xLSEt\r\n" +
        "ycY0kjsdxGKDudWcsATllFzXDCQF6DTNIWECQEA52ePwTjKrVnLTfCLEG4OgHKvl\r\n" +
        "Zud4amthwDyJWoMEH2ChNB2je1N4JLrABOE+hk+OuoKnKAKEjWd8f3Jg/rkCQHj8\r\n" +
        "mQmogHqYWikgP/FSZl518jV48Tao3iXbqvU9Mo2T6yzYNCCqIoDLFWseNVnCTZ0Q\r\n" +
        "b+IfiEf1UeZVV5o4J+ECQDatNnS3V9qYUKjj/krNRD/U0+7eh8S2ylLqD3RlSn9K\r\n" +
        "tYGRMgAtUXtiOEizBH6bd/orzI9V9sw8yBz+ZqIH25Q=\r\n" +
        "-----END RSA PRIVATE KEY-----\r\n";
    private static final MailetConfiguration DKIMSIGN_MAILET = MailetConfiguration.builder()
        .matcher(All.class)
        .mailet(DKIMSign.class)
        .addProperty(
            "signatureTemplate",
            "v=1; s=selector; d=example.com; h=from:to:received:received; a=rsa-sha256; bh=; b=;")
        .addProperty("privateKey", TESTING_PEM)
        .build();
    private static final MailetConfiguration CONVERT_TO_7BIT_MAILET = MailetConfiguration.builder()
        .matcher(All.class)
        .mailet(ConvertTo7Bit.class)
        .build();
    private static final PublicKeyRecordRetriever MOCK_PUBLIC_KEY_RECORD_RETRIEVER = new MockPublicKeyRecordRetriever(
        "v=DKIM1; k=rsa; p=MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDYDaYKXzwVYwqWbLhmuJ66aTAN8wmDR+rfHE8HfnkSOax0oIoTM5zquZrTLo30870YMfYzxwfB6j/Nz3QdwrUD/t0YMYJiUKyWJnCKfZXHJBJ+yfRHr7oW+UW3cVo9CG2bBfIxsInwYe175g9UjyntJpWueqdEIo1c2bhv9Mp66QIDAQAB;",
        "selector", "example.com");

    @ClassRule
    public static DockerContainer mockSmtp = DockerContainer.fromName(MOCK_SMTP_SERVER)
        .withLogConsumer(outputFrame -> LOGGER.debug("MockSMTP: " + outputFrame.getUtf8String()));
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;
    private ConfigurationClient mockSMTPConfiguration;
    private DataProbe dataProbe;
    private DKIMVerifier dkimVerifier;

    @Before
    public void setUp() {
        mockSMTPConfiguration = configurationClient(mockSmtp);
        dkimVerifier = new DKIMVerifier(MOCK_PUBLIC_KEY_RECORD_RETRIEVER);
    }

    @After
    public void tearDown() {
        mockSMTPConfiguration.cleanServer();
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @Ignore("assertion failed:" +
        "org.apache.james.jdkim.exceptions.PermFailException: Computed bodyhash is different from the expected one")
    @Test
    public void remoteDeliveryShouldNotBreakDKIMSignWhen7BitMessageAndAllowing8BitMime() throws Exception {
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(JAMES_ANOTHER_DOMAIN, mockSmtp.getContainerIp());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
                .putProcessor(directResolutionTransport(MailetConfiguration.remoteDeliveryBuilder()))
                .putProcessor(CommonProcessors.bounces()))
            .build(temporaryFolder.newFolder());

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        FakeMail mail = FakeMail.builder()
            .name("a-mail-with-7bit-encoding")
            .sender(new MailAddress(FROM))
            .recipient(new MailAddress(RECIPIENT))
            .mimeMessage(MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream(("eml/message-text-only-7bit.eml"))))
            .build();
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(mail);

        MimeMessage sendMessage = toMimeMessage(getFirstRecivedMail());

        assertThat(dkimVerifier.verifyUsingCRLF(sendMessage))
            .isNotEmpty();
    }

    @Test
    public void remoteDeliveryShouldNotBreakDKIMSignWhen7BitAndBase64MessageAndAllowing8BitMime() throws Exception {
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(JAMES_ANOTHER_DOMAIN, mockSmtp.getContainerIp());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
                .putProcessor(directResolutionTransport(MailetConfiguration.remoteDeliveryBuilder()))
                .putProcessor(CommonProcessors.bounces()))
            .build(temporaryFolder.newFolder());

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        FakeMail mail = FakeMail.builder()
            .name("a-mail-with-7bit-base64-encoding")
            .sender(new MailAddress(FROM))
            .recipient(new MailAddress(RECIPIENT))
            .mimeMessage(MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream(("eml/message-multipart-7bit.eml"))))
            .build();
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(mail);

        MimeMessage sendMessage = toMimeMessage(getFirstRecivedMail());

        assertThat(dkimVerifier.verifyUsingCRLF(sendMessage))
            .isNotEmpty();
    }

    @Test
    public void remoteDeliveryShouldNotBreakDKIMSignWhen7BitMessageAndDisable8BitMime() throws Exception {
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(JAMES_ANOTHER_DOMAIN, mockSmtp.getContainerIp());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
                .putProcessor(directResolutionTransport(MailetConfiguration.remoteDeliveryBuilder()
                    .addProperty("mail.smtp.allow8bitmime", "false")))
                .putProcessor(CommonProcessors.bounces()))
            .build(temporaryFolder.newFolder());

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        FakeMail mail = FakeMail.builder()
            .name("a-mail-with-7bit-encoding")
            .sender(new MailAddress(FROM))
            .recipient(new MailAddress(RECIPIENT))
            .mimeMessage(MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream(("eml/message-text-only-7bit.eml"))))
            .build();
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(mail);

        MimeMessage sendMessage = toMimeMessage(getFirstRecivedMail());

        assertThat(dkimVerifier.verifyUsingCRLF(sendMessage))
            .isNotEmpty();
    }

    @Test
    public void remoteDeliveryShouldNotBreakDKIMSignWhen7BitAndBase64MessageAndDisable8BitMime() throws Exception {
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(JAMES_ANOTHER_DOMAIN, mockSmtp.getContainerIp());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
                .putProcessor(directResolutionTransport(MailetConfiguration.remoteDeliveryBuilder()
                    .addProperty("mail.smtp.allow8bitmime", "false")))
                .putProcessor(CommonProcessors.bounces()))
            .build(temporaryFolder.newFolder());

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        FakeMail mail = FakeMail.builder()
            .name("a-mail-with-7bit-base64-encoding")
            .sender(new MailAddress(FROM))
            .recipient(new MailAddress(RECIPIENT))
            .mimeMessage(MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream(("eml/message-multipart-7bit.eml"))))
            .build();
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(mail);

        MimeMessage sendMessage = toMimeMessage(getFirstRecivedMail());

        assertThat(dkimVerifier.verifyUsingCRLF(sendMessage))
            .isNotEmpty();
    }

    @Ignore("assertion failed:" +
        "org.apache.james.jdkim.exceptions.PermFailException: Computed bodyhash is different from the expected one")
    @Test
    public void remoteDeliveryShouldNotBreakDKIMSignWhen8BitMessageAndAllowing8BitMime() throws Exception {
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(JAMES_ANOTHER_DOMAIN, mockSmtp.getContainerIp());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
                .putProcessor(directResolutionTransport(MailetConfiguration.remoteDeliveryBuilder()))
                .putProcessor(CommonProcessors.bounces()))
            .build(temporaryFolder.newFolder());

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        FakeMail mail = FakeMail.builder()
            .name("a-mail-with-8bit-encoding")
            .sender(new MailAddress(FROM))
            .recipient(new MailAddress(RECIPIENT))
            .mimeMessage(MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream(("eml/message-text-only-7bit.eml"))))
            .build();
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(mail);

        MimeMessage sendMessage = toMimeMessage(getFirstRecivedMail());

        assertThat(dkimVerifier.verifyUsingCRLF(sendMessage))
            .isNotEmpty();
    }

    @Test
    public void remoteDeliveryShouldNotBreakDKIMSignWhen8BitAndBase64MessageAndAllowing8BitMime() throws Exception {
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(JAMES_ANOTHER_DOMAIN, mockSmtp.getContainerIp());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
                .putProcessor(directResolutionTransport(MailetConfiguration.remoteDeliveryBuilder()))
                .putProcessor(CommonProcessors.bounces()))
            .build(temporaryFolder.newFolder());

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        FakeMail mail = FakeMail.builder()
            .name("a-mail-with-8bit-base64-encoding")
            .sender(new MailAddress(FROM))
            .recipient(new MailAddress(RECIPIENT))
            .mimeMessage(MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream(("eml/message-multipart-8bit.eml"))))
            .build();
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(mail);

        MimeMessage sendMessage = toMimeMessage(getFirstRecivedMail());

        assertThat(dkimVerifier.verifyUsingCRLF(sendMessage))
            .isNotEmpty();
    }

    @Test
    public void remoteDeliveryShouldNotBreakDKIMSignWhen8BitMessageAndDisable8BitMime() throws Exception {
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(JAMES_ANOTHER_DOMAIN, mockSmtp.getContainerIp());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
                .putProcessor(directResolutionTransport(MailetConfiguration.remoteDeliveryBuilder()
                    .addProperty("mail.smtp.allow8bitmime", "false")))
                .putProcessor(CommonProcessors.bounces()))
            .build(temporaryFolder.newFolder());

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        FakeMail mail = FakeMail.builder()
            .name("a-mail-with-8bit-encoding")
            .sender(new MailAddress(FROM))
            .recipient(new MailAddress(RECIPIENT))
            .mimeMessage(MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream(("eml/message-text-only-8bit.eml"))))
            .build();
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(mail);

        MimeMessage sendMessage = toMimeMessage(getFirstRecivedMail());

        assertThat(dkimVerifier.verifyUsingCRLF(sendMessage))
            .isNotEmpty();
    }

    @Test
    public void remoteDeliveryShouldNotBreakDKIMSignWhen8BitAndBase64MessageAndDisable8BitMime() throws Exception {
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(JAMES_ANOTHER_DOMAIN, mockSmtp.getContainerIp());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withMailetContainer(TemporaryJamesServer.SIMPLE_MAILET_CONTAINER_CONFIGURATION
                .putProcessor(directResolutionTransport(MailetConfiguration.remoteDeliveryBuilder()
                    .addProperty("mail.smtp.allow8bitmime", "false")))
                .putProcessor(CommonProcessors.bounces()))
            .build(temporaryFolder.newFolder());

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        FakeMail mail = FakeMail.builder()
            .name("a-mail-with-8bit-base64-encoding")
            .sender(new MailAddress(FROM))
            .recipient(new MailAddress(RECIPIENT))
            .mimeMessage(MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream(("eml/message-multipart-8bit.eml"))))
            .build();
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(mail);

        MimeMessage sendMessage = toMimeMessage(getFirstRecivedMail());

        assertThat(dkimVerifier.verifyUsingCRLF(sendMessage))
            .isNotEmpty();
    }

    private MimeMessage toMimeMessage(Mail mail) {
        try {
            return MimeMessageUtil.mimeMessageFromString(mail.getMessage());
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    private Mail getFirstRecivedMail() {
        return awaitAtMostOneMinute
            .until(() -> mockSMTPConfiguration.listMails()
                .stream()
                .findFirst(), Optional::isPresent)
            .get();
    }

    private ProcessorConfiguration.Builder directResolutionTransport(MailetConfiguration.Builder remoteDeliveryConfiguration) {
        return ProcessorConfiguration.transport()
            .addMailet(MailetConfiguration.BCC_STRIPPER)
            .addMailet(CONVERT_TO_7BIT_MAILET)
            .addMailet(DKIMSIGN_MAILET)
            .addMailet(remoteDeliveryConfiguration
                .matcher(All.class));
    }

    private ConfigurationClient configurationClient(DockerContainer mockSmtp) {
        return ConfigurationClient.from(
            Host.from(mockSmtp.getHostIp(),
                mockSmtp.getMappedPort(8000)));
    }
}
