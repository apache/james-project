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

import java.time.ZonedDateTime;

import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.Test;

public abstract class SMIMECheckSignatureIntegrationTest {
    public static final ZonedDateTime DATE_2015 = ZonedDateTime.parse("2015-10-15T14:10:00Z");
    public static final String FROM = "user@" + DEFAULT_DOMAIN;
    public static final String RECIPIENT = "user2@" + DEFAULT_DOMAIN;
    public static final String PASSWORD = "secret";

    abstract TestIMAPClient testIMAPClient();

    abstract SMTPMessageSender messageSender();

    abstract TemporaryJamesServer jamesServer();

    @Test
    public void checkSMIMESignatureShouldAddGoodSMIMEStatusWhenSignatureIsGood() throws Exception {
        messageSender().connect(LOCALHOST_IP, jamesServer().getProbe(SmtpGuiceProbe.class).getSmtpAuthRequiredPort())
            .authenticate(FROM, PASSWORD)
            .sendMessageWithHeaders(FROM, RECIPIENT,
                ClassLoaderUtils.getSystemResourceAsString("smime-test-resource-set/mail_with_signature.eml"));

        testIMAPClient().connect(LOCALHOST_IP, jamesServer().getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient().readFirstMessage()).containsSequence("X-SMIME-Status: Good signature");
    }

    @Test
    public void checkSMIMESignatureShouldAddGoodSMIMEStatusWhenSignatureIsGoodAndContentTypeIsXPkcs7Mime() throws Exception {
        messageSender().connect(LOCALHOST_IP, jamesServer().getProbe(SmtpGuiceProbe.class).getSmtpAuthRequiredPort())
            .authenticate(FROM, PASSWORD)
            .sendMessageWithHeaders(FROM, RECIPIENT,
                ClassLoaderUtils.getSystemResourceAsString("smime-test-resource-set/mail_with_signature_and_content_type_xpkcs7mime.eml"));

        testIMAPClient().connect(LOCALHOST_IP, jamesServer().getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient().readFirstMessage()).containsSequence("X-SMIME-Status: Good signature");
    }

    @Test
    public void checkSMIMESignatureShouldAddGoodSMIMEStatusWhenSignatureIsGoodAndMailContainsMultiCerts() throws Exception {
        messageSender().connect(LOCALHOST_IP, jamesServer().getProbe(SmtpGuiceProbe.class).getSmtpAuthRequiredPort())
            .authenticate(FROM, PASSWORD)
            .sendMessageWithHeaders(FROM, RECIPIENT,
                ClassLoaderUtils.getSystemResourceAsString("smime-test-resource-set/mail-with-signature-and-multi-certs.eml"));

        testIMAPClient().connect(LOCALHOST_IP, jamesServer().getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient().readFirstMessage()).containsSequence("X-SMIME-Status: Good signature");
    }

    @Test
    public void checkSMIMESignatureShouldAddBadSMIMEStatusWhenSignatureIsBad() throws Exception {
        messageSender().connect(LOCALHOST_IP, jamesServer().getProbe(SmtpGuiceProbe.class).getSmtpAuthRequiredPort())
            .authenticate(FROM, PASSWORD)
            .sendMessageWithHeaders(FROM, RECIPIENT,
                ClassLoaderUtils.getSystemResourceAsString("eml/mail_with_bad_signature.eml"));

        testIMAPClient().connect(LOCALHOST_IP, jamesServer().getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient().readFirstMessage()).containsSequence("X-SMIME-Status: Bad signature");
    }

    @Test
    public void checkSMIMESignatureShouldAddNotSignedStatusWhenNoSignature() throws Exception {
        messageSender().connect(LOCALHOST_IP, jamesServer().getProbe(SmtpGuiceProbe.class).getSmtpAuthRequiredPort())
            .authenticate(FROM, PASSWORD)
            .sendMessageWithHeaders(FROM, RECIPIENT,
                ClassLoaderUtils.getSystemResourceAsString("eml/mail_with_no_signature.eml"));

        testIMAPClient().connect(LOCALHOST_IP, jamesServer().getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient().readFirstMessage()).containsSequence("X-SMIME-Status: Not signed");
    }

    @Test
    public void checkSMIMESignatureShouldDoNothingWhenItIsNonSMIMEMail() throws Exception {
        messageSender().connect(LOCALHOST_IP, jamesServer().getProbe(SmtpGuiceProbe.class).getSmtpAuthRequiredPort())
            .authenticate(FROM, PASSWORD)
            .sendMessageWithHeaders(FROM, RECIPIENT,
                ClassLoaderUtils.getSystemResourceAsString("eml/non_smime_mail.eml"));

        testIMAPClient().connect(LOCALHOST_IP, jamesServer().getProbe(ImapGuiceProbe.class).getImapPort())
            .login(RECIPIENT, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
        assertThat(testIMAPClient().readFirstMessage()).doesNotContain("X-SMIME-Status");
    }
}
