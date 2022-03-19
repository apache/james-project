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

package org.apache.james.mailetcontainer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Properties;

import jakarta.activation.DataHandler;
import jakarta.mail.BodyPart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;

import com.google.common.base.Joiner;

class AutomaticallySentMailDetectorImplTest {
    @Test
    void nullSenderMailsShouldBeConsideredAsAutomaticMails() throws Exception {
        assertThat(
            new AutomaticallySentMailDetectorImpl()
                .isAutomaticallySent(FakeMail.builder()
                    .name("mail")
                    .build()))
            .isTrue();
    }

    @Test
    void nonNullSenderMailsShouldNotBeConsideredAsAutomaticMails() throws Exception {
        assertThat(
            new AutomaticallySentMailDetectorImpl()
                .isAutomaticallySent(FakeMail.builder()
                    .name("mail")
                    .sender(MailAddressFixture.ANY_AT_JAMES)
                    .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                        .setText("any"))
                    .build()))
            .isFalse();
    }

    @Test
    void ownerIsAMailingListPrefix() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
                .name("mail")
                .sender("owner-list@any.com")
                .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    void requestIsAMailingListPrefix() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
                .name("mail")
                .sender("list-request@any.com")
                .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    void mailerDaemonIsReserved() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
                .name("mail")
                .sender("MAILER-DAEMON@any.com")
                .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    void listservIsReserved() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
                .name("mail")
                .sender("LISTSERV@any.com")
                .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    void majordomoIsReserved() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
                .name("mail")
                .sender("majordomo@any.com")
                .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    void listIdShouldBeDetected() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .sender("any@any.com")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader("List-Id", "any"))
            .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    void listHelpShouldBeDetected() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .sender("any@any.com")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader("List-Help", "any"))
            .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    void listSubscribeShouldBeDetected() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .sender("any@any.com")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader("List-Subscribe", "any"))
            .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    void listUnsubscribeShouldBeDetected() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
                .name("mail")
                .sender("any@any.com")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addHeader("List-Unsubscribe", "any"))
                .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    void listPostShouldBeDetected() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .sender("any@any.com")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader("List-Post", "any"))
            .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    void listOwnerShouldBeDetected() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .sender("any@any.com")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader("List-Owner", "any"))
            .build();


        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    void listArchiveShouldBeDetected() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .sender("any@any.com")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader("List-Archive", "any"))
            .build();


        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    void normalMailShouldNotBeIdentifiedAsMailingList() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
                .name("mail")
                .sender("any@any.com")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder())
                .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isFalse();
    }

    @Test
    void isAutoSubmittedShouldNotMatchNonAutoSubmittedMails() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
                .name("mail")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder())
                .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isAutoSubmitted(fakeMail)).isFalse();
    }

    @Test
    void isAutoSubmittedShouldBeDetected() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
                .name("mail")
                .sender("any@any.com")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addHeader("Auto-Submitted", "auto-replied"))
                .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isAutoSubmitted(fakeMail)).isTrue();
    }

    @Test
    void isAutoSubmittedShouldBeDetectedWhenAutoGenerated() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
                .name("mail")
                .sender("any@any.com")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addHeader("Auto-Submitted", "auto-generated"))
                .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isAutoSubmitted(fakeMail)).isTrue();
    }

    @Test
    void isAutoSubmittedShouldBeDetectedWhenAutoNotified() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
                .name("mail")
                .sender("any@any.com")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addHeader("Auto-Submitted", "auto-notified"))
                .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isAutoSubmitted(fakeMail)).isTrue();
    }

    @Test
    void isMdnSentAutomaticallyShouldBeDetected() throws Exception {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        MimeMultipart multipart = new MimeMultipart();
        multipart.setSubType("report");
        MimeBodyPart scriptPart = new MimeBodyPart();
        scriptPart.setDataHandler(
                new DataHandler(
                        new ByteArrayDataSource(
                            Joiner.on("\r\n").join(
                                "Final-Recipient: rfc822;any@any.com",
                                "Disposition: automatic-action/MDN-sent-automatically; displayed",
                                ""),
                                "message/disposition-notification;")
                        ));
        scriptPart.setHeader("Content-Type", "message/disposition-notification");
        multipart.addBodyPart(scriptPart);
        message.setContent(multipart);
        message.saveChanges();

        FakeMail fakeMail = FakeMail.builder()
                .name("mail")
                .sender("any@any.com")
                .mimeMessage(message)
                .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isMdnSentAutomatically(fakeMail)).isTrue();
    }

    @Test
    void isMdnSentAutomaticallyShouldNotFilterManuallySentMdn() throws Exception {
        MimeMessage message = MimeMessageUtil.defaultMimeMessage();
        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart scriptPart = new MimeBodyPart();
        scriptPart.setDataHandler(
                new DataHandler(
                        new ByteArrayDataSource(
                            Joiner.on("\r\n").join(
                                "Final-Recipient: rfc822;any@any.com",
                                "Disposition: manual-action/MDN-sent-manually; displayed",
                                ""),
                                "message/disposition-notification; charset=UTF-8")
                        ));
        scriptPart.setHeader("Content-Type", "message/disposition-notification");
        multipart.addBodyPart(scriptPart);
        message.setContent(multipart);
        message.saveChanges();
        
        FakeMail fakeMail = FakeMail.builder()
                .name("mail")
                .sender("any@any.com")
                .mimeMessage(message)
                .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isMdnSentAutomatically(fakeMail)).isFalse();
    }

    @Test
    void isMdnSentAutomaticallyShouldManageItsMimeType() throws Exception {
        MimeMessage message = MimeMessageUtil.defaultMimeMessage();
        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart scriptPart = new MimeBodyPart();
        scriptPart.setDataHandler(
                new DataHandler(
                        new ByteArrayDataSource(
                                "Disposition: MDN-sent-automatically",
                                "text/plain")
                        ));
        scriptPart.setHeader("Content-Type", "text/plain");
        multipart.addBodyPart(scriptPart);
        message.setContent(multipart);
        message.saveChanges();
        
        FakeMail fakeMail = FakeMail.builder()
                .name("mail")
                .sender("any@any.com")
                .mimeMessage(message)
                .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isMdnSentAutomatically(fakeMail)).isFalse();
    }

    @Test
    void isMdnSentAutomaticallyShouldNotThrowOnBodyPartsWithManyLines() throws Exception {
        int mime4jDefaultMaxHeaderCount = 1000;
        int headerCount = mime4jDefaultMaxHeaderCount + 10;
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .addHeaders()
            .setMultipartWithBodyParts(MimeMessageBuilder.bodyPartBuilder()
                .addHeaders(Collections.nCopies(headerCount, new MimeMessageBuilder.Header("name", "value")))
                .data("The body part have 1010 headers, which overpass MIME4J default limits"));

        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(message)
            .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isMdnSentAutomatically(fakeMail)).isFalse();
    }

    @Test
    void isMdnSentAutomaticallyShouldNotThrowWhenBiggerThan1MB() throws Exception {
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .addHeaders()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("12345678\r\n".repeat(150 * 1024)), // ~ 1.5 MB
                MimeMessageBuilder.bodyPartBuilder()
                    .data("12345678\r\n"));

        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(message)
            .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isMdnSentAutomatically(fakeMail)).isFalse();
    }

    @Test
    void isMdnSentAutomaticallyShouldDetectBigMDN() throws Exception {
        MimeMessage message = MimeMessageUtil.defaultMimeMessage();
        MimeMultipart multipart = new MimeMultipart();
        multipart.setSubType("report");
        MimeBodyPart scriptPart = new MimeBodyPart();
        scriptPart.setDataHandler(
            new DataHandler(
                new ByteArrayDataSource(
                    Joiner.on("\r\n").join(
                        "Final-Recipient: rfc822;any@any.com",
                        "Disposition: automatic-action/MDN-sent-automatically; displayed",
                        ""),
                    "message/disposition-notification;")
            ));
        scriptPart.setHeader("Content-Type", "message/disposition-notification");
        BodyPart bigBody = MimeMessageBuilder.bodyPartBuilder() // ~3MB
            .data("12345678\r\n".repeat(300 * 1024))
            .build();
        multipart.addBodyPart(bigBody);
        multipart.addBodyPart(scriptPart);
        message.setContent(multipart);
        message.saveChanges();

        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(message)
            .build();

        assertThat(new AutomaticallySentMailDetectorImpl().isMdnSentAutomatically(fakeMail)).isTrue();
    }
}
