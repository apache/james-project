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

package org.apache.james.transport.mailets;

import static org.apache.james.mailets.configuration.CommonProcessors.ERROR_REPOSITORY;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.rate.limiter.memory.MemoryRateLimiterModule;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.MailRepositoryProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SpoolerProbe;
import org.apache.james.utils.TestIMAPClient;
import org.apache.mailet.base.test.FakeMail;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

public class GlobalRateLimitIntegrationTest {

    private static final String SENDER1 = "sender1@" + DEFAULT_DOMAIN;
    private static final String SENDER2 = "sender2@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT1 = "recipient1@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT2 = "recipient2@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT3 = "recipient3@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT4 = "recipient4@" + DEFAULT_DOMAIN;
    private static final String MESSAGE_CONTENT = "any text";
    private static final long BIG_NUMBER = 1000;

    private TemporaryJamesServer jamesServer;
    private MailboxProbe mailboxProbe;
    private MimeMessage message;

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    @BeforeEach
    void setup(@TempDir File temporaryFolder) throws Exception {
        MailetContainer.Builder mailetContainer = TemporaryJamesServer.simpleMailetContainerConfiguration()
            .putProcessor(ProcessorConfiguration.error()
                .enableJmx(false)
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(ToRepository.class)
                    .addProperty("repositoryPath", ERROR_REPOSITORY.asString()))
                .build())
            .putProcessor(ProcessorConfiguration.transport()
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(GlobalRateLimit.class)
                    .addProperty("duration", "100s")
                    .addProperty("precision", "1s")
                    .addProperty("count", "2")
                    .addProperty("recipients", "3")
                    .addProperty("size", "13K")
                    .addProperty("totalSize", "20K")
                    .build())
                .addMailetsFrom(CommonProcessors.transport()));

        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(mailetContainer)
            .withOverrides(new MemoryRateLimiterModule())
            .build(temporaryFolder);
        jamesServer.start();

        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(SENDER1, PASSWORD)
            .addUser(SENDER2, PASSWORD)
            .addUser(RECIPIENT1, PASSWORD)
            .addUser(RECIPIENT2, PASSWORD)
            .addUser(RECIPIENT3, PASSWORD)
            .addUser(RECIPIENT4, PASSWORD);

        mailboxProbe = jamesServer.getProbe(MailboxProbeImpl.class);

        message = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("test")
            .setText(MESSAGE_CONTENT)
            .build();
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void sendMessageShouldSucceedWhenCountLimitIsAcceptable() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER1, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(SENDER1)
                .recipient(RECIPIENT1));
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER2, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(SENDER2)
                .recipient(RECIPIENT1));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT1, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessageCount(awaitAtMostOneMinute, 2);
    }

    @Test
    void sendMessageShouldFailWhenCountLimitExceeded() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER1, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(SENDER1)
                .recipient(RECIPIENT1));
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER2, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(SENDER2)
                .recipient(RECIPIENT1));

        // await first 2 message
        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT1, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessageCount(awaitAtMostOneMinute, 2);

        // send third message should exceed count limit
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER1, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(SENDER1)
                .recipient(RECIPIENT2));

        awaitAtMostOneMinute.until(() -> jamesServer.getProbe(MailRepositoryProbeImpl.class).getRepositoryMailCount(ERROR_REPOSITORY) == 1);
        assertThat(testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT2, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .getMessageCount(TestIMAPClient.INBOX)).isEqualTo(0);
    }

    @Test
    void sendMessageShouldSucceedWhenRecipientsLimitIsAcceptable() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER1, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(SENDER1)
                .recipient(RECIPIENT1));
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER2, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(SENDER2)
                .recipients(RECIPIENT1));

        assertThat(testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT1, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessageCount(awaitAtMostOneMinute, 2)
            .getMessageCount(TestIMAPClient.INBOX)).isEqualTo(2);
    }

    @Test
    void sendMessageShouldFailWhenRecipientsLimitExceeded() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER1, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(SENDER1)
                .recipient(RECIPIENT1));
        Thread.sleep(100);
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER2, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(SENDER2)
                .recipients(RECIPIENT2, RECIPIENT3, RECIPIENT4));

        awaitAtMostOneMinute.untilAsserted(() -> jamesServer.getProbe(SpoolerProbe.class).processingFinished());

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.matchAll()).build(), RECIPIENT1, BIG_NUMBER).size())
                .isEqualTo(1);
            softly.assertThat(mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.matchAll()).build(), RECIPIENT2, BIG_NUMBER).size())
                .isEqualTo(0);
            softly.assertThat(mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.matchAll()).build(), RECIPIENT3, BIG_NUMBER).size())
                .isEqualTo(0);
            softly.assertThat(mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.matchAll()).build(), RECIPIENT4, BIG_NUMBER).size())
                .isEqualTo(0);
            awaitAtMostOneMinute.until(() -> jamesServer.getProbe(MailRepositoryProbeImpl.class).getRepositoryMailCount(ERROR_REPOSITORY) == 1);
        });
    }

    @Test
    void sendMessageShouldSucceedWhenSizeLimitIsAcceptable() throws Exception {
        // sender1 and sender2 send 2 message with size ~ 5420 * 2 bytes < 13 KB
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER1, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("test")
                    .setText("01234567\r\n".repeat(500))
                    .build())
                .sender(SENDER1)
                .recipients(RECIPIENT1));
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER2, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("test")
                    .setText("01234567\r\n".repeat(500))
                    .build())
                .sender(SENDER2)
                .recipients(RECIPIENT1));

        assertThat(testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT1, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessageCount(awaitAtMostOneMinute, 2)
            .getMessageCount(TestIMAPClient.INBOX)).isEqualTo(2);
    }

    @Test
    void sendMessageShouldFailWhenSizeLimitExceeded() throws Exception {
        // sender1 send a message with size ~ 7420 bytes
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER1, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("test")
                    .setText("01234567\r\n".repeat(700))
                    .build())
                .sender(SENDER1)
                .recipients(RECIPIENT1));
        // sender2 send a message with size ~ 7420 bytes should exceed 13 KB
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER2, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("test")
                    .setText("01234567\r\n".repeat(700))
                    .build())
                .sender(SENDER2)
                .recipients(RECIPIENT2));

        awaitAtMostOneMinute.until(() -> jamesServer.getProbe(MailRepositoryProbeImpl.class).getRepositoryMailCount(ERROR_REPOSITORY) == 1);
        assertThat(testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT2, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .getMessageCount(TestIMAPClient.INBOX)).isEqualTo(0);
    }

    @Test
    void sendMessageShouldSucceedWhenTotalSizeLimitIsAcceptable() throws Exception {
        // send 2 mail with totalSize ~ 5420 * 3 bytes <  20 KB
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER1, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("test")
                    .setText("01234567\r\n".repeat(500))
                    .build())
                .sender(SENDER1)
                .recipients(RECIPIENT1, RECIPIENT2));
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER2, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("test")
                    .setText("01234567\r\n".repeat(500))
                    .build())
                .sender(SENDER2)
                .recipients(RECIPIENT1));

        assertThat(testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT1, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessageCount(awaitAtMostOneMinute, 2)
            .getMessageCount(TestIMAPClient.INBOX)).isEqualTo(2);

        assertThat(testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT2, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute)
            .getMessageCount(TestIMAPClient.INBOX)).isEqualTo(1);
    }

    @Test
    void sendMessageShouldFailWhenTotalSizeLimitExceeded() throws Exception {
        // sender1 send first mail with totalSize ~ 7420 * 2 bytes = 14840 bytes
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER1, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("test")
                    .setText("01234567\r\n".repeat(700))
                    .build())
                .sender(SENDER1)
                .recipients(RECIPIENT1, RECIPIENT2));
        // sender2 send second mail with totalSize ~ 7420 bytes should exceed 20 KB
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER2, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("test")
                    .setText("01234567\r\n".repeat(700))
                    .build())
                .sender(SENDER2)
                .recipients(RECIPIENT3));

        awaitAtMostOneMinute.until(() -> jamesServer.getProbe(MailRepositoryProbeImpl.class).getRepositoryMailCount(ERROR_REPOSITORY) == 1);
        assertThat(testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT3, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .getMessageCount(TestIMAPClient.INBOX)).isEqualTo(0);
    }

}
