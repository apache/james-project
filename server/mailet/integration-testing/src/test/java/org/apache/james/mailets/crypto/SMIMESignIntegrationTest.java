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

package org.apache.james.mailets.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.DataProbeImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import com.jayway.awaitility.core.ConditionFactory;

public class SMIMESignIntegrationTest {

    private static final ZonedDateTime DATE_2015 = ZonedDateTime.parse("2015-10-15T14:10:00Z");
    private static final String DEFAULT_DOMAIN = "domain";
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final int IMAP_PORT = 1143;
    private static final int SMTP_PORT = 1025;
    private static final int SMTP_SECURE_PORT = 10465;
    private static final String PASSWORD = "secret";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private TemporaryJamesServer jamesServer;
    private ConditionFactory calmlyAwait;
    public static final String FROM = "user@" + DEFAULT_DOMAIN;
    public static final String RECIPIENT = "user2@" + DEFAULT_DOMAIN;

    @Before
    public void setup() throws Exception {
        MailetContainer mailetContainer = MailetContainer.builder()
            .postmaster("postmaster@" + DEFAULT_DOMAIN)
            .threads(5)
            .addProcessor(CommonProcessors.root())
            .addProcessor(CommonProcessors.error())
            .addProcessor(ProcessorConfiguration.builder()
                .state("transport")
                .enableJmx(true)
                .addMailet(MailetConfiguration.builder()
                    .match("SMTPAuthSuccessful")
                    .clazz("SetMimeHeader")
                    .addProperty("name", "X-UserIsAuth")
                    .addProperty("value", "true")
                    .build())
                .addMailet(MailetConfiguration.builder()
                    .match("HasMailAttribute=org.apache.james.SMIMECheckSignature")
                    .clazz("SetMimeHeader")
                    .addProperty("name", "X-WasSigned")
                    .addProperty("value", "true")
                    .build())
                .addMailet(MailetConfiguration.builder()
                    .match("All")
                    .clazz("RemoveMimeHeader")
                    .addProperty("name", "bcc")
                    .build())
                .addMailet(MailetConfiguration.builder()
                    .match("All")
                    .clazz("RecipientRewriteTable")
                    .build())
                .addMailet(MailetConfiguration.builder()
                    .match("RecipientIsLocal")
                    .clazz("org.apache.james.jmap.mailet.VacationMailet")
                    .build())
                .addMailet(MailetConfiguration.builder()
                    .clazz("SMIMESign")
                    .match("SenderIsLocal")
                    .addProperty("keyStoreFileName", temporaryFolder.getRoot().getAbsoluteFile().getAbsolutePath() + "/conf/smime.p12")
                    .addProperty("keyStorePassword", "secret")
                    .addProperty("keyStoreType", "PKCS12")
                    .addProperty("debug", "true")
                    .build())
                .addMailet(MailetConfiguration.builder()
                    .match("RecipientIsLocal")
                    .clazz("LocalDelivery")
                    .build())
                .addMailet(MailetConfiguration.builder()
                    .match("SMTPAuthSuccessful")
                    .clazz("RemoteDelivery")
                    .addProperty("outgoingQueue", "outgoing")
                    .addProperty("delayTime", "5000, 100000, 500000")
                    .addProperty("maxRetries", "25")
                    .addProperty("maxDnsProblemRetries", "0")
                    .addProperty("deliveryThreads", "10")
                    .addProperty("sendpartial", "true")
                    .addProperty("bounceProcessor", "bounces")
                    .build())
                .addMailet(MailetConfiguration.builder()
                    .match("All")
                    .clazz("ToProcessor")
                    .addProperty("processor", "relay-denied")
                    .build())
                .build())
            .addProcessor(CommonProcessors.spam())
            .addProcessor(CommonProcessors.localAddressError())
            .addProcessor(CommonProcessors.relayDenied())
            .addProcessor(CommonProcessors.bounces())
            .addProcessor(CommonProcessors.sieveManagerCheck())
            .build();

        jamesServer = new TemporaryJamesServer(temporaryFolder, mailetContainer,
                binder -> binder.bind(ZonedDateTimeProvider.class).toInstance(() -> DATE_2015));
        Duration slowPacedPollInterval = Duration.FIVE_HUNDRED_MILLISECONDS;
        calmlyAwait = Awaitility.with().pollInterval(slowPacedPollInterval).and().with().pollDelay(slowPacedPollInterval).await();

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(FROM, PASSWORD);
        dataProbe.addUser(RECIPIENT, PASSWORD);
        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, RECIPIENT, "INBOX");
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void authenticatedMessagesShouldBeSigned() throws Exception {

        try (SMTPMessageSender messageSender = SMTPMessageSender.authentication(LOCALHOST_IP, SMTP_SECURE_PORT, DEFAULT_DOMAIN, FROM, PASSWORD);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(FROM, RECIPIENT);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(messageSender::messageHasBeenSent);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() -> imapMessageReader.userReceivedMessage(RECIPIENT, PASSWORD));

            assertThat(imapMessageReader.readFirstMessageInInbox(RECIPIENT, PASSWORD))
                .containsSequence("Content-Description: S/MIME Cryptographic Signature");
        }
    }

    @Test
    public void NonAuthenticatedMessagesShouldNotBeSigned() throws Exception {
        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, DEFAULT_DOMAIN);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(FROM, RECIPIENT);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(messageSender::messageHasBeenSent);
            calmlyAwait.atMost(Duration.ONE_MINUTE).until(() -> imapMessageReader.userReceivedMessage(RECIPIENT, PASSWORD));

            assertThat(imapMessageReader.readFirstMessageInInbox(RECIPIENT, PASSWORD))
                .doesNotContain("Content-Description: S/MIME Cryptographic Signature");
        }
    }

}
