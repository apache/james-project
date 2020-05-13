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

import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.time.ZonedDateTime;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.transport.mailets.SMIMEDecrypt;
import org.apache.james.transport.matchers.All;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SMIMEDecryptIntegrationTest {
    private static final ZonedDateTime DATE_2015 = ZonedDateTime.parse("2015-10-15T14:10:00Z");
    private static final String FROM = "sender@" + DEFAULT_DOMAIN;
    private static final String PASSWORD = "secret";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public TestIMAPClient testIMAPClient = new TestIMAPClient();
    @Rule
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

    @Before
    public void setup() throws Exception {
        File workingDir = temporaryFolder.newFolder();
        MailetContainer mailetContainer = MailetContainer.builder()
            .putProcessor(CommonProcessors.root())
            .putProcessor(CommonProcessors.error())
            .putProcessor(ProcessorConfiguration.transport()
                .addMailet(MailetConfiguration.BCC_STRIPPER)
                .addMailet(MailetConfiguration.builder()
                    .mailet(SMIMEDecrypt.class)
                    .matcher(All.class)
                    .addProperty("keyStoreFileName", workingDir.toPath().resolve("conf").resolve("smime.p12").toAbsolutePath().toString())
                    .addProperty("keyStorePassword", "secret")
                    .addProperty("keyStoreType", "PKCS12")
                    .addProperty("debug", "true"))
                .addMailet(MailetConfiguration.LOCAL_DELIVERY))
            .build();

        jamesServer = TemporaryJamesServer.builder()
            .withBase(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE)
            .withOverrides(binder -> binder.bind(ZonedDateTimeProvider.class).toInstance(() -> DATE_2015))
            .withMailetContainer(mailetContainer)
            .build(workingDir);

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(FROM, PASSWORD);
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void cryptedMessageShouldBeDecryptedWhenCertificateMatches() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpAuthRequiredPort())
            .authenticate(FROM, PASSWORD)
            .sendMessageWithHeaders(FROM, FROM,
                ClassLoaderUtils.getSystemResourceAsString("eml/crypted.eml"));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage()).containsSequence("Crypted content");
    }

    @Test
    public void cryptedMessageWithAttachmentShouldBeDecryptedWhenCertificateMatches() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpAuthRequiredPort())
            .authenticate(FROM, PASSWORD)
            .sendMessageWithHeaders(FROM, FROM,
                ClassLoaderUtils.getSystemResourceAsString("eml/crypted_with_attachment.eml"));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage())
            .containsSequence("Crypted Content with attachment");
    }

    @Test
    public void cryptedMessageShouldNotBeDecryptedWhenCertificateDoesntMatch() throws Exception {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpAuthRequiredPort())
            .authenticate(FROM, PASSWORD)
            .sendMessageWithHeaders(FROM, FROM,
                ClassLoaderUtils.getSystemResourceAsString("eml/bad_crypted.eml"));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(FROM, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient.readFirstMessage())
            .containsSequence("MIAGCSqGSIb3DQEHA6CAMIACAQAxggKpMIICpQIBADCBjDCBhjELMAkGA1UE");
    }

}
