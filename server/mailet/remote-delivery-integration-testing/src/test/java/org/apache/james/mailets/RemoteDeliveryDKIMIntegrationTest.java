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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.util.Optional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.jdkim.api.PublicKeyRecordRetriever;
import org.apache.james.jdkim.exceptions.PermFailException;
import org.apache.james.jdkim.mailets.ConvertTo7Bit;
import org.apache.james.jdkim.mailets.DKIMSign;
import org.apache.james.jdkim.mailets.DKIMVerifier;
import org.apache.james.jdkim.mailets.MockPublicKeyRecordRetriever;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mailets.configuration.SmtpConfiguration;
import org.apache.james.mock.smtp.server.model.Mail;
import org.apache.james.mock.smtp.server.testing.MockSmtpServerExtension;
import org.apache.james.mock.smtp.server.testing.MockSmtpServerExtension.DockerMockSmtp;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.matchers.All;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SMTPMessageSenderExtension;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RemoteDeliveryDKIMIntegrationTest {

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

    @RegisterExtension
    static MockSmtpServerExtension mockSmtpServerExtension = new MockSmtpServerExtension();
    @TempDir
    static File tempDir;

    @RegisterExtension
    SMTPMessageSenderExtension smtpSenderExtension = new SMTPMessageSenderExtension(Domain.of(DEFAULT_DOMAIN));

    private TemporaryJamesServer jamesServer;
    private DataProbe dataProbe;
    private DKIMVerifier dkimVerifier;

    @BeforeEach
    void setUp() {
        dkimVerifier = new DKIMVerifier(MOCK_PUBLIC_KEY_RECORD_RETRIEVER);
    }

    @AfterEach
    void tearDown() {
        if (jamesServer != null) {
            jamesServer.shutdown();
        }
    }

    @CsvSource({
        "a-mail-with-7bit-encoding, eml/message-text-only-7bit.eml",
        "a-mail-with-8bit-encoding, eml/message-text-only-8bit.eml",
    })
    @ParameterizedTest
    void remoteDeliveryCouldBreakDKIMSignWhenTextMessageWhenEnable8BitMime(String mailName, String emlPath,
                                                                           SMTPMessageSender messageSender, DockerMockSmtp dockerMockSmtp) throws Exception {
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(JAMES_ANOTHER_DOMAIN, dockerMockSmtp.getIPAddress());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .withMailetContainer(TemporaryJamesServer.simpleMailetContainerConfiguration()
                .putProcessor(directResolutionTransport(MailetConfiguration.remoteDeliveryBuilder()
                    .addProperty("mail.smtp.allow8bitmime", "true")))
                .putProcessor(CommonProcessors.bounces()))
            .build(tempDir);
        jamesServer.start();

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        FakeMail mail = FakeMail.builder()
            .name(mailName)
            .sender(new MailAddress(FROM))
            .recipient(new MailAddress(RECIPIENT))
            .mimeMessage(MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream(emlPath)))
            .build();
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(mail);

        MimeMessage sendMessage = toMimeMessage(getFirstRecivedMail(dockerMockSmtp));

        assertThatThrownBy(() -> dkimVerifier.verifyUsingCRLF(sendMessage))
            .isInstanceOf(PermFailException.class)
            .hasMessageContaining("Computed bodyhash is different from the expected one");
    }

    @CsvSource({
        "a-mail-with-7bit-base64-encoding, eml/message-multipart-7bit.eml",
        "a-mail-with-8bit-base64-encoding, eml/message-multipart-8bit.eml"
    })
    @ParameterizedTest
    void remoteDeliveryShouldNotBreakDKIMSignWhenEnable8BitMime(String mailName, String emlPath,
                                                                SMTPMessageSender messageSender, DockerMockSmtp dockerMockSmtp) throws Exception {
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(JAMES_ANOTHER_DOMAIN, dockerMockSmtp.getIPAddress());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .withMailetContainer(TemporaryJamesServer.simpleMailetContainerConfiguration()
                .putProcessor(directResolutionTransport(MailetConfiguration.remoteDeliveryBuilder()
                    .addProperty("mail.smtp.allow8bitmime", "true")))
                .putProcessor(CommonProcessors.bounces()))
            .build(tempDir);
        jamesServer.start();

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        FakeMail mail = FakeMail.builder()
            .name(mailName)
            .sender(new MailAddress(FROM))
            .recipient(new MailAddress(RECIPIENT))
            .mimeMessage(MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream(emlPath)))
            .build();
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(mail);

        MimeMessage sendMessage = toMimeMessage(getFirstRecivedMail(dockerMockSmtp));

        assertThat(dkimVerifier.verifyUsingCRLF(sendMessage))
            .isNotEmpty();
    }

    @CsvSource({
        "a-mail-with-7bit-encoding, eml/message-text-only-7bit.eml",
        "a-mail-with-7bit-base64-encoding, eml/message-multipart-7bit.eml",
        "a-mail-with-8bit-encoding, eml/message-text-only-8bit.eml",
        "a-mail-with-8bit-base64-encoding, eml/message-multipart-8bit.eml"
    })
    @ParameterizedTest
    void remoteDeliveryShouldNotBreakDKIMSignWhenDisable8BitMime(String mailName, String emlPath,
                                                                 SMTPMessageSender messageSender, DockerMockSmtp dockerMockSmtp) throws Exception {
        InMemoryDNSService inMemoryDNSService = new InMemoryDNSService()
            .registerMxRecord(JAMES_ANOTHER_DOMAIN, dockerMockSmtp.getIPAddress());

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_ONLY_MODULE)
            .withOverrides(binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))
            .withSmtpConfiguration(SmtpConfiguration.builder()
                .withAutorizedAddresses("0.0.0.0/0.0.0.0"))
            .withMailetContainer(TemporaryJamesServer.simpleMailetContainerConfiguration()
                .putProcessor(directResolutionTransport(MailetConfiguration.remoteDeliveryBuilder()))
                .putProcessor(CommonProcessors.bounces()))
            .build(tempDir);
        jamesServer.start();

        dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);

        FakeMail mail = FakeMail.builder()
            .name(mailName)
            .sender(new MailAddress(FROM))
            .recipient(new MailAddress(RECIPIENT))
            .mimeMessage(MimeMessageUtil.mimeMessageFromStream(
                ClassLoader.getSystemResourceAsStream(emlPath)))
            .build();
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessage(mail);

        MimeMessage sendMessage = toMimeMessage(getFirstRecivedMail(dockerMockSmtp));

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

    private Mail getFirstRecivedMail(DockerMockSmtp dockerMockSmtp) {
        return awaitAtMostOneMinute
            .until(() -> dockerMockSmtp.getConfigurationClient().listMails()
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
}
