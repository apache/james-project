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
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.FROM;
import static org.apache.james.mailets.configuration.Constants.IMAP_PORT;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT2;
import static org.apache.james.mailets.configuration.Constants.SMTP_PORT;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.MessagingException;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.spamassassin.SpamAssassinResult;
import org.apache.james.transport.matchers.All;
import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.SwarmGenericContainer;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.mailet.base.test.FakeMail;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

public class SpamAssassinTest {
    private static final String SPAM_CONTENT = "XJS*C4JDBQADN1.NSBN3*2IDNEN*GTUBE-STANDARD-ANTI-UBE-TEST-EMAIL*C.34X";

    @Rule
    public SwarmGenericContainer spamAssassinContainer = new SwarmGenericContainer(Images.SPAMASSASSIN)
        .withExposedPorts(783)
        .withAffinityToContainer()
        .waitingFor(new HostPortWaitStrategy());
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public IMAPMessageReader messageReader = new IMAPMessageReader();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @Before
    public void setup() throws Exception {
        MailetContainer.Builder mailets = TemporaryJamesServer.DEFAULT_MAILET_CONTAINER_CONFIGURATION
            .putProcessor(
                ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(SpamAssassin.class)
                        .addProperty(SpamAssassin.SPAMD_HOST, spamAssassinContainer.getContainerIp()))
                    .addMailetsFrom(CommonProcessors.deliverOnlyTransport()));

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(mailets)
            .build(temporaryFolder);

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD)
            .addUser(RECIPIENT2, PASSWORD);
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void spamAssassinShouldAppendNewHeaderOnMessage() throws Exception {
        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(mailWithContent("This is the content", RECIPIENT));

        messageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(RECIPIENT, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        assertThat(messageReader.readFirstMessageHeaders())
            .contains(
                SpamAssassinResult.FLAG_MAIL_ATTRIBUTE_NAME,
                SpamAssassinResult.STATUS_MAIL_ATTRIBUTE_NAME);
    }

    @Test
    public void spamAssassinShouldAppendNewHeaderWhichDetectIsSpamWhenSpamMessage() throws Exception {
        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(mailWithContent(SPAM_CONTENT, RECIPIENT));

        messageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(RECIPIENT, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        String receivedHeaders = messageReader.readFirstMessageHeaders();
        assertThat(receivedHeaders).contains(SpamAssassinResult.FLAG_MAIL_ATTRIBUTE_NAME + ": YES");
        assertThat(receivedHeaders).contains(SpamAssassinResult.STATUS_MAIL_ATTRIBUTE_NAME + ": Yes");
    }

    @Test
    public void spamAssassinShouldAppendNewHeaderWhichNoWhenNonSpamMessage() throws Exception {
        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(mailWithContent("This is the content", RECIPIENT));

        messageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(RECIPIENT, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        String receivedHeaders = messageReader.readFirstMessageHeaders();
        assertThat(receivedHeaders).contains(SpamAssassinResult.FLAG_MAIL_ATTRIBUTE_NAME + ": NO");
        assertThat(receivedHeaders).contains(SpamAssassinResult.STATUS_MAIL_ATTRIBUTE_NAME + ": No");
    }

    @Test
    public void spamAssassinShouldAppendNewHeaderPerRecipientOnMessage() throws Exception {
        messageSender.connect(LOCALHOST_IP, SMTP_PORT)
            .sendMessage(mailWithContent("This is the content", RECIPIENT, RECIPIENT2));

        messageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(RECIPIENT, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        assertThat(messageReader.readFirstMessageHeaders())
            .contains(
                SpamAssassinResult.FLAG_MAIL_ATTRIBUTE_NAME,
                SpamAssassinResult.STATUS_MAIL_ATTRIBUTE_NAME);

        messageReader.disconnect()
            .connect(LOCALHOST_IP, IMAP_PORT)
            .login(RECIPIENT2, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        assertThat(messageReader.readFirstMessageHeaders())
            .contains(
                SpamAssassinResult.FLAG_MAIL_ATTRIBUTE_NAME,
                SpamAssassinResult.STATUS_MAIL_ATTRIBUTE_NAME);
    }

    private FakeMail.Builder mailWithContent(String textContent, String... recipients) throws MessagingException {
        return FakeMail.builder()
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSender(FROM)
                .addToRecipient(recipients)
                .setSubject("This is the subject")
                .setText(textContent))
            .sender(FROM)
            .recipients(recipients);
    }
}
