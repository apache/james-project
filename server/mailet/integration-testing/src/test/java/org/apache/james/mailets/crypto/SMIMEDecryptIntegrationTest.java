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

import static com.jayway.awaitility.Duration.ONE_MINUTE;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.IMAP_PORT;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.calmlyAwait;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;

import org.apache.commons.io.IOUtils;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.transport.mailets.SMIMEDecrypt;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SMIMEDecryptIntegrationTest {
    private static final ZonedDateTime DATE_2015 = ZonedDateTime.parse("2015-10-15T14:10:00Z");
    private static final int SMTP_SECURE_PORT = 10465;
    private static final String FROM = "sender@" + DEFAULT_DOMAIN;
    private static final String PASSWORD = "secret";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public IMAPMessageReader imapMessageReader = new IMAPMessageReader();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @Before
    public void setup() throws Exception {
        MailetContainer mailetContainer = MailetContainer.builder()
            .addProcessor(CommonProcessors.root())
            .addProcessor(CommonProcessors.error())
            .addProcessor(ProcessorConfiguration.transport()
                .enableJmx(true)
                .addMailet(MailetConfiguration.BCC_STRIPPER)
                .addMailet(MailetConfiguration.builder()
                    .mailet(SMIMEDecrypt.class)
                    .matcher(All.class)
                    .addProperty("keyStoreFileName", temporaryFolder.getRoot().getAbsoluteFile().getAbsolutePath() + "/conf/smime.p12")
                    .addProperty("keyStorePassword", "secret")
                    .addProperty("keyStoreType", "PKCS12")
                    .addProperty("debug", "true"))
                .addMailet(MailetConfiguration.builder()
                    .matcher(RecipientIsLocal.class)
                    .mailet(LocalDelivery.class)))
            .build();

        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withOverrides(binder -> binder.bind(ZonedDateTimeProvider.class).toInstance(() -> DATE_2015))
            .withMailetContainer(mailetContainer)
            .build(temporaryFolder);

        DataProbeImpl serverProbe = jamesServer.getProbe(DataProbeImpl.class);
        serverProbe.addDomain(DEFAULT_DOMAIN);
        serverProbe.addUser(FROM, PASSWORD);
        MailboxProbeImpl mailboxProbe = jamesServer.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, FROM, "INBOX");
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void cryptedMessageShouldBeDecryptedWhenCertificateMatches() throws Exception {
        messageSender.connect(LOCALHOST_IP, SMTP_SECURE_PORT)
            .authenticate(FROM, PASSWORD)
            .sendMessageWithHeaders(FROM, FROM,
                IOUtils.toString(ClassLoader.getSystemResourceAsStream("eml/crypted.eml"), StandardCharsets.US_ASCII))
            .awaitSent(calmlyAwait.atMost(ONE_MINUTE));

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(FROM, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(calmlyAwait.atMost(ONE_MINUTE));
        assertThat(imapMessageReader.readFirstMessage()).containsSequence("Crypted content");
    }

    @Test
    public void cryptedMessageWithAttachmentShouldBeDecryptedWhenCertificateMatches() throws Exception {
        messageSender.connect(LOCALHOST_IP, SMTP_SECURE_PORT)
            .authenticate(FROM, PASSWORD)
            .sendMessageWithHeaders(FROM, FROM,
                IOUtils.toString(ClassLoader.getSystemResourceAsStream("eml/crypted_with_attachment.eml"), StandardCharsets.US_ASCII))
            .awaitSent(calmlyAwait.atMost(ONE_MINUTE));

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(FROM, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(calmlyAwait.atMost(ONE_MINUTE));
        assertThat(imapMessageReader.readFirstMessage())
            .containsSequence("Crypted Content with attachment");
    }

    @Test
    public void cryptedMessageShouldNotBeDecryptedWhenCertificateDoesntMatch() throws Exception {
        messageSender.connect(LOCALHOST_IP, SMTP_SECURE_PORT)
            .authenticate(FROM, PASSWORD)
            .sendMessageWithHeaders(FROM, FROM,
                IOUtils.toString(ClassLoader.getSystemResourceAsStream("eml/bad_crypted.eml"), StandardCharsets.US_ASCII))
            .awaitSent(calmlyAwait.atMost(ONE_MINUTE));

        imapMessageReader.connect(LOCALHOST_IP, IMAP_PORT)
            .login(FROM, PASSWORD)
            .select(IMAPMessageReader.INBOX)
            .awaitMessage(calmlyAwait.atMost(ONE_MINUTE));
        assertThat(imapMessageReader.readFirstMessage())
            .containsSequence("MIAGCSqGSIb3DQEHA6CAMIACAQAxggKpMIICpQIBADCBjDCBhjELMAkGA1UE");
    }

}
