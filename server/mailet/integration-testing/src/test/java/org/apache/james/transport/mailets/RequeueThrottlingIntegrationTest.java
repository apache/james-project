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
import java.time.Duration;
import java.util.ArrayList;
import java.util.stream.IntStream;

import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.jmap.MessageIdProbe;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
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
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.github.fge.lambdas.Throwing;

class RequeueThrottlingIntegrationTest {
    private static final String SENDER = "sender1@" + DEFAULT_DOMAIN;
    private static final String RECIPIENT1 = "recipient1@" + DEFAULT_DOMAIN;
    private TemporaryJamesServer jamesServer;
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
                    .mailet(PerSenderRateLimit.class)
                    .addProperty("duration", "3s")
                    .addProperty("precision", "1s")
                    .addProperty("count", "1")
                    .addProperty("exceededProcessor", "tooMuchEmails")
                    .build())
                .addMailetsFrom(CommonProcessors.transport()))
            .putProcessor(ProcessorConfiguration.builder()
                .state("tooMuchEmails")
                .addMailet(MailetConfiguration.builder()
                    .matcher(All.class)
                    .mailet(Requeue.class)
                    .addProperty("delay", "4s")
                    .build())
                .build());

        jamesServer = TemporaryJamesServer.builder()
            .withMailetContainer(mailetContainer)
            .withOverrides(new MemoryRateLimiterModule())
            .build(temporaryFolder);
        jamesServer.start();

        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(SENDER, PASSWORD)
            .addUser(RECIPIENT1, PASSWORD);

        message = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("test")
            .setText("text1")
            .build();
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void throttlingShouldWork() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(RECIPIENT1));

        // await first message
        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT1, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        // the second message should exceed count limit and re-enqueued
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(SENDER, PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(message)
                .sender(SENDER)
                .recipient(RECIPIENT1));

        // Then the second message should be sent to recipient after delay
        MailboxProbeImpl mailboxProbe = jamesServer.getProbe(MailboxProbeImpl.class);
        awaitAtMostOneMinute.until(() -> mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build(), RECIPIENT1, Integer.MAX_VALUE).size() == 2);

        // Checking the received dates of the emails to make sure emails were throttled
        ArrayList<MessageId> messageIds = new ArrayList<>(mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build(), RECIPIENT1, Integer.MAX_VALUE));
        Username recipient = Username.of(RECIPIENT1);
        MessageIdProbe messageIdProbe = jamesServer.getProbe(MessageIdProbe.class);
        MessageResult firstMessage = messageIdProbe.getMessages(messageIds.get(0), recipient).get(0);
        MessageResult secondMessage = messageIdProbe.getMessages(messageIds.get(1), recipient).get(0);

        assertThat(Duration.between(
            firstMessage.getInternalDate().toInstant(),
            secondMessage.getInternalDate().toInstant()).abs())
            .isGreaterThan(Duration.ofSeconds(3));
    }

    @Test
    void throttlingShouldWorkWhenSeveralMessagesIsRateLimitExceeded() {
        IntStream.range(1, 4)
            .forEach(i -> Throwing.runnable(() -> messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
                .authenticate(SENDER, PASSWORD)
                .sendMessage(FakeMail.builder()
                    .name("name")
                    .mimeMessage(message)
                    .sender(SENDER)
                    .recipient(RECIPIENT1))).sneakyThrow().run());

        awaitAtMostOneMinute.until(() -> testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT1, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute)
            .getMessageCount(TestIMAPClient.INBOX) == 3);
    }
}
