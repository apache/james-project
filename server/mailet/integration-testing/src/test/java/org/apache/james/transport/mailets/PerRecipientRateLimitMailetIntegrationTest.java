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
import java.io.IOException;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.Constants;
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
import org.apache.james.utils.TestIMAPClient;
import org.apache.mailet.base.test.FakeMail;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerRecipientRateLimitMailetIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(PerRecipientRateLimitMailetIntegrationTest.class);
    private static final int SEARCH_LIMIT_DEFAULT = 100;
    private static final String SENDER = "sender@" + DEFAULT_DOMAIN;
    private static final String SENDER2 = "sender2@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT = "recipient@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT2 = "recipient2@" + DEFAULT_DOMAIN;

    private TemporaryJamesServer jamesServer;
    private MailboxProbeImpl mailboxProbe;

    @RegisterExtension
    public TestIMAPClient imapClient = new TestIMAPClient();
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
                    .mailet(PerRecipientRateLimitMailet.class)
                    .addProperty("duration", "60s")
                    .addProperty("count", "1")
                    .addProperty("size", "6K")
                    .build())
                .addMailetsFrom(CommonProcessors.transport()));

        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(mailetContainer)
            .withOverrides(new MemoryRateLimiterModule())
            .build(temporaryFolder);
        jamesServer.start();

        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(RECIPIENT, PASSWORD)
            .addUser(RECIPIENT2, PASSWORD)
            .addUser(SENDER, PASSWORD)
            .addUser(SENDER2, PASSWORD);

        mailboxProbe = jamesServer.getProbe(MailboxProbeImpl.class);
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    private void awaitFirstMessage() {
        try {
            imapClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(RECIPIENT, PASSWORD)
                .select(TestIMAPClient.INBOX)
                .awaitMessage(Constants.awaitAtMostOneMinute);
        } catch (IOException e) {
            LOGGER.error("Unexpected error", e);
        }
    }

    @Test
    void recipientShouldReceivedEmailWhenRateLimitIsAcceptable() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient(RECIPIENT)
                    .setSender(SENDER)
                    .setText("Content1"))
                .sender(SENDER)
                .recipient(RECIPIENT));

        awaitFirstMessage();
        assertThat(imapClient.readFirstMessage()).contains("Content1");
    }

    @Test
    void recipientShouldNotReceivedEmailWhenRateLimitExceeded() throws Exception {
        // acceptable
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient(RECIPIENT)
                    .setSender(SENDER)
                    .setText("Content1"))
                .sender(SENDER)
                .recipient(RECIPIENT));

        awaitFirstMessage();

        // exceeded
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient(RECIPIENT)
                    .setSender(SENDER)
                    .setText("Content2"))
                .sender(SENDER)
                .recipient(RECIPIENT));

        // Then
        awaitAtMostOneMinute.until(() -> jamesServer.getProbe(MailRepositoryProbeImpl.class).getRepositoryMailCount(ERROR_REPOSITORY) == 1);

        assertThat(mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build(), RECIPIENT, SEARCH_LIMIT_DEFAULT)
            .size())
            .isEqualTo(1);
    }

    @Test
    void rateLimitShouldBeAppliedPerRecipient() throws Exception {
        // acceptable
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient(RECIPIENT)
                    .setSender(SENDER)
                    .setText("Content1"))
                .sender(SENDER)
                .recipient(RECIPIENT));

        awaitFirstMessage();

        // RECIPIENT: exceeded, RECIPIENT2: acceptable
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient(RECIPIENT, RECIPIENT2)
                    .setSender(SENDER)
                    .setText("Content2"))
                .sender(SENDER)
                .recipients(RECIPIENT, RECIPIENT2));

        // Then
        awaitAtMostOneMinute.until(() -> jamesServer.getProbe(MailRepositoryProbeImpl.class).getRepositoryMailCount(ERROR_REPOSITORY) == 1);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build(), RECIPIENT, SEARCH_LIMIT_DEFAULT).size())
                .isEqualTo(1);
            softly.assertThat(mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build(), RECIPIENT2, SEARCH_LIMIT_DEFAULT).size())
                .isEqualTo(1);
        });
    }

    @Test
    void allRecipientShouldNotReceivedEmailWhenAllRateLimitExceeded() throws Exception {
        // acceptable
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient(RECIPIENT, RECIPIENT2)
                    .setSender(SENDER)
                    .setText("Content1"))
                .sender(SENDER)
                .recipients(RECIPIENT, RECIPIENT2));

        awaitFirstMessage();

        // exceeded all
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient(RECIPIENT, RECIPIENT2)
                    .setSender(SENDER)
                    .setText("Content2"))
                .sender(SENDER)
                .recipients(RECIPIENT, RECIPIENT2));

        // Then
        awaitAtMostOneMinute.until(() -> jamesServer.getProbe(MailRepositoryProbeImpl.class).getRepositoryMailCount(ERROR_REPOSITORY) == 1);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build(), RECIPIENT, SEARCH_LIMIT_DEFAULT).size())
                .isEqualTo(1);
            softly.assertThat(mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build(), RECIPIENT2, SEARCH_LIMIT_DEFAULT).size())
                .isEqualTo(1);
        });
    }

    @Test
    void rateLimitShouldNotBeAppliedPerSender() throws Exception {
        // acceptable. SENDER
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient(RECIPIENT)
                    .setSender(SENDER)
                    .setText("Content1"))
                .sender(SENDER)
                .recipient(RECIPIENT));

        awaitFirstMessage();

        // exceeded. SENDER2
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER2, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addToRecipient(RECIPIENT)
                    .setSender(SENDER2)
                    .setText("Content2"))
                .sender(SENDER2)
                .recipient(RECIPIENT));

        awaitAtMostOneMinute.until(() -> jamesServer.getProbe(MailRepositoryProbeImpl.class).getRepositoryMailCount(ERROR_REPOSITORY) == 1);

        assertThat(mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build(), RECIPIENT, SEARCH_LIMIT_DEFAULT).size())
            .isEqualTo(1);
    }

    @Test
    void recipientShouldNotReceivedEmailWhenSizeLimitExceeded() throws Exception {
        // send a message with size 7420 bytes
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("test")
                    .setText("01234567\r\n".repeat(700))
                    .build())
                .sender(SENDER)
                .recipients(RECIPIENT));

        awaitAtMostOneMinute.until(() -> jamesServer.getProbe(MailRepositoryProbeImpl.class).getRepositoryMailCount(ERROR_REPOSITORY) == 1);
        assertThat(mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build(), RECIPIENT, SEARCH_LIMIT_DEFAULT).size())
            .isEqualTo(0);
    }
}
