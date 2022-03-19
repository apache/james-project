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

package org.apache.james.spamassassin;

import static org.apache.james.MemoryJamesServerMain.SMTP_AND_IMAP_MODULE;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.FROM;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT;
import static org.apache.james.mailets.configuration.Constants.RECIPIENT2;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import jakarta.mail.MessagingException;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.transport.matchers.All;
import org.apache.james.util.Host;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;

class SpamAssassinIntegrationTest {
    private static final String SPAM_CONTENT = "XJS*C4JDBQADN1.NSBN3*2IDNEN*GTUBE-STANDARD-ANTI-UBE-TEST-EMAIL*C.34X";

    @RegisterExtension
    public static SpamAssassinExtension spamAssassinExtension = new SpamAssassinExtension();
    @RegisterExtension
    public TestIMAPClient messageReader = new TestIMAPClient();
    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @BeforeEach
    void setup(@TempDir File temporaryFolder) throws Exception {
        MailetContainer.Builder mailets = TemporaryJamesServer.defaultMailetContainerConfiguration()
            .putProcessor(
                ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(SpamAssassin.class))
                    .addMailetsFrom(CommonProcessors.deliverOnlyTransport()));

        jamesServer = TemporaryJamesServer.builder()
            .withBase(SMTP_AND_IMAP_MODULE)
            .withMailetContainer(mailets)
            .withOverrides(new Module() {
                @Override
                public void configure(Binder binder) {
                }
                @Provides
                @Singleton
                private SpamAssassinConfiguration provideSpamAssassinConfiguration(){
                    return new SpamAssassinConfiguration(Host.parseConfString("localhost", spamAssassinExtension.getSpamAssassin().getBindingPort()));
                }
            })
            .build(temporaryFolder);
        jamesServer.start();

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD)
            .addUser(RECIPIENT, PASSWORD)
            .addUser(RECIPIENT2, PASSWORD);
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void spamAssassinShouldAppendNewHeaderOnMessage() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(mailWithContent("This is the content", RECIPIENT));

        messageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        assertThat(messageReader.readFirstMessageHeaders())
            .contains(
                SpamAssassinResult.FLAG_MAIL.asString(),
                SpamAssassinResult.STATUS_MAIL.asString());
    }

    @Test
    void spamAssassinShouldAppendNewHeaderWhichDetectIsSpamWhenSpamMessage() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(mailWithContent(SPAM_CONTENT, RECIPIENT));

        messageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        String receivedHeaders = messageReader.readFirstMessageHeaders();
        assertThat(receivedHeaders).contains(SpamAssassinResult.FLAG_MAIL.asString() + ": YES");
        assertThat(receivedHeaders).contains(SpamAssassinResult.STATUS_MAIL.asString() + ": Yes");
    }

    @Test
    void spamAssassinShouldAppendNewHeaderWhichNoWhenNonSpamMessage() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(mailWithContent("This is the content", RECIPIENT));

        messageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        String receivedHeaders = messageReader.readFirstMessageHeaders();
        assertThat(receivedHeaders).contains(SpamAssassinResult.FLAG_MAIL.asString() + ": NO");
        assertThat(receivedHeaders).contains(SpamAssassinResult.STATUS_MAIL.asString() + ": No");
    }

    @Test
    void spamAssassinShouldAppendNewHeaderPerRecipientOnMessage() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(FROM, PASSWORD)
            .sendMessage(mailWithContent("This is the content", RECIPIENT, RECIPIENT2));

        messageReader.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        assertThat(messageReader.readFirstMessageHeaders())
            .contains(
                SpamAssassinResult.FLAG_MAIL.asString(),
                SpamAssassinResult.STATUS_MAIL.asString());

        messageReader.disconnect()
            .connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT2, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        assertThat(messageReader.readFirstMessageHeaders())
            .contains(
                SpamAssassinResult.FLAG_MAIL.asString(),
                SpamAssassinResult.STATUS_MAIL.asString());
    }

    private FakeMail.Builder mailWithContent(String textContent, String... recipients) throws MessagingException {
        return FakeMail.builder()
            .name("name")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSender(FROM)
                .addToRecipient(recipients)
                .setSubject("This is the subject")
                .setText(textContent))
            .sender(FROM)
            .recipients(recipients);
    }

}
