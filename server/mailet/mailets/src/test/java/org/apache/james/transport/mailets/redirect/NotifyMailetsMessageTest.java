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

package org.apache.james.transport.mailets.redirect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotifyMailetsMessageTest {
    private TimeZone timeZone;

    @BeforeEach
    void setUp() {
        timeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(timeZone);
    }

    @Test
    void generateMessageShouldReturnTheMessageWhenSimpleMimeMessage() throws Exception {
        FakeMail mail = FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder())
                .sender(new MailAddress("user", "james.org"))
                .build();

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("my message\n")
            .contains("Message details:")
            .contains("  MAIL FROM: user@james.org\n");
    }

    @Test
    void generateMessageShouldAddErrorMessageWhenMimeMessageAsSome() throws Exception {
        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder());
        String myErrorMessage = "my error message";
        mail.setErrorMessage(myErrorMessage);

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains(
                "Error message below:\n" +
                "my error message\n");
    }

    @Test
    void generateMessageShouldAddSubjectWhenMimeMessageAsSome() throws Exception {
        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("my subject"));

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("Subject: my subject");
    }

    @Test
    void generateMessageShouldAddSentDateWhenMimeMessageAsSome() throws Exception {
        MimeMessage message = MimeMessageUtil.defaultMimeMessage();
        message.setSentDate(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")));
        FakeMail mail = FakeMail.from(message);

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("Sent date: Thu Sep 08 14:25:52 UTC 2016");
    }

    @Test
    void generateMessageShouldAddRecipientsWhenMimeMessageAsSome() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("name")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder())
            .recipients("user@james.org", "user2@james.org")
            .build();

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("RCPT TO: user@james.org\n" +
            "           user2@james.org");
    }

    @Test
    void generateMessageShouldAddFromWhenMimeMessageAsSome() throws Exception {
        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder()
            .addFrom("user@james.org"));

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("From: \n" +
            "user@james.org");
    }

    @Test
    void generateMessageShouldAddToWhenMimeMessageAsSome() throws Exception {
        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder()
            .addToRecipient("user@james.org", "user2@james.org"));

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("To: \n" +
            "user@james.org \n" +
            "user2@james.org");
    }

    @Test
    void generateMessageShouldAddCCWhenMimeMessageAsSome() throws Exception {
        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder()
            .addCcRecipient("user@james.org", "user2@james.org"));

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("CC: \n" +
            "user@james.org \n" +
            "user2@james.org");
    }

    @Test
    void generateMessageShouldAddSizeWhenPossible() throws Exception {
        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder());
        mail.setMessageSize(6);

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("Size: 6 B");
    }

    @Test
    void generateMessageShouldSpecifySizeInAReadableWay() throws Exception {
        String content = "MIME-Version: 1.0\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "\r\n" +
            "test\r\n";


        FakeMail mail = FakeMail.from(MimeMessageUtil.mimeMessageFromString(content));
        mail.setMessageSize((long)(5.9 * 1024));

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("Size: 5.9 KiB");
    }

    @Test
    void getMessageInternalSizeShouldTransformMessagingErrorIntoEmpty() throws MessagingException {
        Mail mail = mock(Mail.class);
        when(mail.getMessageSize()).thenThrow(new MessagingException());

        assertThat(NotifyMailetsMessage.getMessageSizeEstimation(mail))
            .isEqualTo(Optional.empty());
    }

    @Test
    void getMessageInternalSizeShouldTransformZeroSizeIntoEmpty() throws MessagingException {
        Mail mail = mock(Mail.class);
        when(mail.getMessageSize()).thenReturn(0L);

        assertThat(NotifyMailetsMessage.getMessageSizeEstimation(mail))
            .isEqualTo(Optional.empty());
    }

    @Test
    void getMessageInternalSizeShouldTransformNegativeIntoEmpty() throws MessagingException {
        Mail mail = mock(Mail.class);
        when(mail.getMessageSize()).thenReturn(-1L);

        assertThat(NotifyMailetsMessage.getMessageSizeEstimation(mail))
            .isEqualTo(Optional.empty());
    }

    @Test
    void getMessageInternalSizeShouldReturnSizeWhenAvailable() throws MessagingException {
        long size = 42L;

        Mail mail = mock(Mail.class);
        when(mail.getMessageSize()).thenReturn(size);

        assertThat(NotifyMailetsMessage.getMessageSizeEstimation(mail))
            .isEqualTo(Optional.of(size));
    }

    @Test
    void generateMessageShouldDecodeEncodedSubject() throws Exception {
        String content = "MIME-Version: 1.0\r\n" +
            "Subject: =?UTF-8?Q?Cl=c3=b4ture_&_Paie_du_mois?=\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "\r\n" +
            "test\r\n";

        FakeMail mail = FakeMail.from(MimeMessageUtil.mimeMessageFromString(content));

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("Subject: Clôture & Paie du mois");
    }

    @Test
    void generateMessageShouldDecodeEncodedFrom() throws Exception {
        String content = "MIME-Version: 1.0\r\n" +
            "From: =?UTF-8?Q?=F0=9F=90=83@linagora.com?=\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "\r\n" +
            "test\r\n";

        FakeMail mail = FakeMail.from(MimeMessageUtil.mimeMessageFromString(content));

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("  From: \n" +
            "🐃@linagora.com");
    }

    @Test
    void generateMessageShouldDecodeEncodedTo() throws Exception {
        String content = "MIME-Version: 1.0\r\n" +
            "To: =?UTF-8?Q?=F0=9F=9A=BE@linagora.com?=\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "\r\n" +
            "test\r\n";

        FakeMail mail = FakeMail.from(MimeMessageUtil.mimeMessageFromString(content));

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("  To: \n" +
            "🚾@linagora.com");
    }

    @Test
    void generateMessageShouldDecodeEncodedCc() throws Exception {
        String content = "MIME-Version: 1.0\r\n" +
            "Cc: =?UTF-8?Q?=F0=9F=9A=B2@linagora.com?=\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "\r\n" +
            "test\r\n";

        FakeMail mail = FakeMail.from(MimeMessageUtil.mimeMessageFromString(content));

        String generateMessage = new NotifyMailetsMessage().generateMessage("my message", mail);

        assertThat(generateMessage).contains("  CC: \n" +
            "🚲@linagora.com");
    }

    @Test
    void safelyDecodeShouldReturnTextNotEncodedUnmodified() {
        String text = "Why not unicode for Llama";

        assertThat(NotifyMailetsMessage.safelyDecode(text))
            .isEqualTo(text);
    }

    @Test
    void safelyDecodeShouldCorrectlyDecodeQuotedPrintable() {
        assertThat(NotifyMailetsMessage.safelyDecode("=?UTF-8?Q?=E2=99=A5=F0=9F=9A=B2?="))
            .isEqualTo("♥🚲");
    }

    @Test
    void safelyDecodeShouldReturnInvalidEncodedTextUnmodified() {
        String invalidEncodedText = "=?UTF-8?Q?=E2=99=A5=FX=9F=9A=B2?=";

        assertThat(NotifyMailetsMessage.safelyDecode(invalidEncodedText))
            .isEqualTo(invalidEncodedText);
    }

    @Test
    void safelyDecodeShouldReturnEncodedTextUnmodifiedWhenUnknownCharset() {
        String encodedTextWithUnknownCharset = "=?UTF-9?Q?=E2=99=A5=F0=9F=9A=B2?=";

        assertThat(NotifyMailetsMessage.safelyDecode(encodedTextWithUnknownCharset))
            .isEqualTo(encodedTextWithUnknownCharset);
    }
}
