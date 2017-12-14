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

import static org.apache.james.MemoryJamesServerMain.SMTP_AND_IMAP_MODULE;
import static org.apache.james.MemoryJamesServerMain.SMTP_ONLY_MODULE;
import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.james.util.scanner.SpamAssassinInvoker;
import org.apache.james.util.streams.ContainerNames;
import org.apache.james.util.streams.SwarmGenericContainer;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.MimeMessageBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.testcontainers.containers.wait.HostPortWaitStrategy;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import com.jayway.awaitility.core.ConditionFactory;

public class SpamAssassinTest {
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final int IMAP_PORT = 1143;
    private static final int SMTP_PORT = 1025;

    public static final String JAMES_ORG = "james.org";
    public static final String SENDER = "sender@" + JAMES_ORG;
    public static final String TO = "to@" + JAMES_ORG;
    public static final String PASSWORD = "secret";

    public SwarmGenericContainer spamAssassinContainer = new SwarmGenericContainer(ContainerNames.SPAMASSASSIN)
        .withExposedPorts(783)
        .withAffinityToContainer()
        .waitingFor(new HostPortWaitStrategy());

    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public RuleChain chain = RuleChain.outerRule(temporaryFolder).around(spamAssassinContainer);

    private TemporaryJamesServer jamesServer;
    private ConditionFactory calmlyAwait;

    @Before
    public void setup() throws Exception {
        MailetContainer mailets = MailetContainer
            .builder()
            .threads(5)
            .postmaster(SENDER)
            .addProcessor(CommonProcessors.root())
            .addProcessor(CommonProcessors.error())
            .addProcessor(
                ProcessorConfiguration.builder()
                    .state("transport")
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(SpamAssassin.class)
                        .addProperty(SpamAssassin.SPAMD_HOST, spamAssassinContainer.getContainerIp())
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(MailAttributesToMimeHeaders.class)
                        .addProperty("simplemapping",
                            SpamAssassinInvoker.FLAG_MAIL_ATTRIBUTE_NAME + ";" + SpamAssassinInvoker.FLAG_MAIL_ATTRIBUTE_NAME + "," +
                            SpamAssassinInvoker.STATUS_MAIL_ATTRIBUTE_NAME + ";" + SpamAssassinInvoker.STATUS_MAIL_ATTRIBUTE_NAME)
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(RemoveMimeHeader.class)
                        .addProperty("name", "bcc")
                        .build())
                    .addMailet(MailetConfiguration.builder()
                        .matcher(RecipientIsLocal.class)
                        .mailet(LocalDelivery.class)
                        .build())
                    .build())
            .build();
        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .build(temporaryFolder, mailets);
        Duration slowPacedPollInterval = Duration.FIVE_HUNDRED_MILLISECONDS;
        calmlyAwait = Awaitility.with().pollInterval(slowPacedPollInterval).and().with().pollDelay(slowPacedPollInterval).await();

        DataProbeImpl probe = jamesServer.getProbe(DataProbeImpl.class);
        probe.addDomain(JAMES_ORG);
        probe.addUser(SENDER, PASSWORD);
        probe.addUser(TO, PASSWORD);
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void spamAssassinShouldAppendNewHeaderOnMessage() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setSender(SENDER)
            .addToRecipient(TO)
            .setSubject("This is the subject")
            .setText("This is -SPAM- my email")
            .build();
        FakeMail mail = FakeMail.builder()
            .mimeMessage(message)
            .sender(new MailAddress(SENDER))
            .recipients(new MailAddress(TO))
            .build();

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_ORG);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {

            messageSender.sendMessage(mail);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(messageSender::messageHasBeenSent);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() -> imapMessageReader.userReceivedMessage(TO, PASSWORD));

            String receivedHeaders = imapMessageReader.readFirstMessageHeadersInInbox(TO, PASSWORD);

            assertThat(receivedHeaders).contains(SpamAssassinInvoker.FLAG_MAIL_ATTRIBUTE_NAME, SpamAssassinInvoker.STATUS_MAIL_ATTRIBUTE_NAME);
        }
    }

    @Test
    public void spamAssassinShouldAppendNewHeaderWhichDetectIsSpamWhenSpamMessage() throws Exception {
        String spamContent = "XJS*C4JDBQADN1.NSBN3*2IDNEN*GTUBE-STANDARD-ANTI-UBE-TEST-EMAIL*C.34X";
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setSender(SENDER)
            .addToRecipient(TO)
            .setSubject("This is the subject")
            .setText(spamContent)
            .build();
        FakeMail mail = FakeMail.builder()
            .mimeMessage(message)
            .sender(new MailAddress(SENDER))
            .recipients(new MailAddress(TO))
            .build();

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_ORG);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {

            messageSender.sendMessage(mail);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(messageSender::messageHasBeenSent);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() -> imapMessageReader.userReceivedMessage(TO, PASSWORD));

            String receivedHeaders = imapMessageReader.readFirstMessageInInbox(TO, PASSWORD);

            assertThat(receivedHeaders).contains(SpamAssassinInvoker.FLAG_MAIL_ATTRIBUTE_NAME + ": YES");
            assertThat(receivedHeaders).contains(SpamAssassinInvoker.STATUS_MAIL_ATTRIBUTE_NAME + ": Yes");
        }
    }

    @Test
    public void spamAssassinShouldAppendNewHeaderWhichNoWhenNonSpamMessage() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setSender(SENDER)
            .addToRecipient(TO)
            .setSubject("This is the subject")
            .setText("This is the content")
            .build();
        FakeMail mail = FakeMail.builder()
            .mimeMessage(message)
            .sender(new MailAddress(SENDER))
            .recipients(new MailAddress(TO))
            .build();

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, JAMES_ORG);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {

            messageSender.sendMessage(mail);

            calmlyAwait.atMost(Duration.ONE_MINUTE).until(messageSender::messageHasBeenSent);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() -> imapMessageReader.userReceivedMessage(TO, PASSWORD));

            String receivedHeaders = imapMessageReader.readFirstMessageInInbox(TO, PASSWORD);

            assertThat(receivedHeaders).contains(SpamAssassinInvoker.FLAG_MAIL_ATTRIBUTE_NAME + ": NO");
            assertThat(receivedHeaders).contains(SpamAssassinInvoker.STATUS_MAIL_ATTRIBUTE_NAME + ": No");
        }
    }
}
