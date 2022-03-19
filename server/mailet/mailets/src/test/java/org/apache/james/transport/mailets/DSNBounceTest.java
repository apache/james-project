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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.DsnParameters;
import org.apache.mailet.Mail;
import org.apache.mailet.base.DateFormats;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.RFC2822Headers;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailContext.SentMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class DSNBounceTest {

    private static final String MAILET_NAME = "mailetName";
    private static final Attribute DELIVERY_ERROR_ATTRIBUTE = Attribute.convertToAttribute("delivery-error", "Delivery error");

    private DSNBounce dsnBounce;
    private FakeMailContext fakeMailContext;
    private MailAddress postmaster;

    @BeforeEach
    void setUp() throws Exception {
        postmaster = new MailAddress("postmaster@domain.com");
        
        DNSService dnsService = mock(DNSService.class);
        dsnBounce = new DSNBounce(dnsService, DateFormats.RFC822_DATE_FORMAT.withZone(ZoneId.of("UTC")));
        fakeMailContext = FakeMailContext.builder().postmaster(postmaster).build();

        InetAddress localHost = InetAddress.getLocalHost();
        when(dnsService.getLocalHost())
            .thenReturn(localHost);
        when(dnsService.getHostName(localHost))
            .thenReturn("myhost");
    }

    @Nested
    class Configuration {
        @Test
        void getMailetInfoShouldReturnValue() {
            assertThat(dsnBounce.getMailetInfo()).isEqualTo("DSNBounce Mailet");
        }

        @Test
        void getAllowedInitParametersShouldReturnTheParameters() {
            assertThat(dsnBounce.getAllowedInitParameters()).containsOnly("debug", "passThrough", "messageString", "attachment", "sender", "prefix", "action", "defaultStatus");
        }

        @Test
        void initShouldFailWhenUnknownParameterIsConfigured() {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("unknwon", "value")
                .build();

            assertThatThrownBy(() -> dsnBounce.init(mailetConfig))
                .isInstanceOf(MessagingException.class);
        }

        @Test
        void getRecipientsShouldReturnReversePathOnly() {
            assertThat(dsnBounce.getRecipients()).containsOnly(SpecialAddress.REVERSE_PATH);
        }

        @Test
        void getToShouldReturnReversePathOnly() {
            assertThat(dsnBounce.getTo()).containsOnly(SpecialAddress.REVERSE_PATH.toInternetAddress().get());
        }

        @Test
        void getReversePathShouldReturnNullSpecialAddress() {
            Mail mail = null;
            assertThat(dsnBounce.getReversePath(mail)).contains(SpecialAddress.NULL);
        }
    }

    @Nested
    class GenericTests {
        @Test
        void serviceShouldUpdateTheMailStateWhenNoSenderAndPassThroughIsFalse() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("passThrough", "false")
                .build();
            dsnBounce.init(mailetConfig);

            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .remoteAddr("remoteHost")
                .build();

            dsnBounce.service(mail);

            assertThat(mail.getState()).isEqualTo(Mail.GHOST);
        }

        @Test
        void serviceShouldNotUpdateTheMailStateWhenNoSenderPassThroughHasDefaultValue() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
            dsnBounce.init(mailetConfig);

            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .remoteAddr("remoteHost")
                .build();

            dsnBounce.service(mail);

            assertThat(mail.getState()).isNull();
        }

        @Test
        void serviceShouldNotUpdateTheMailStateWhenNoSenderPassThroughIsTrue() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("passThrough", "true")
                .build();
            dsnBounce.init(mailetConfig);

            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .remoteAddr("remoteHost")
                .build();

            dsnBounce.service(mail);

            assertThat(mail.getState()).isNull();
        }

        @Test
        void serviceShouldSetTheDateHeaderWhenNone() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .build();
            mail.getMessage().removeHeader(RFC2822Headers.DATE);

            dsnBounce.service(mail);

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            assertThat(sentMail.getSender()).isNull();
            assertThat(sentMail.getRecipients()).containsOnly(senderMailAddress);
            MimeMessage sentMessage = sentMail.getMsg();
            assertThat(sentMessage.getHeader(RFC2822Headers.DATE)).isNotNull();
        }

        @Test
        void serviceShouldNotModifyTheDateHeaderWhenAlreadyPresent() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            String expectedDate = "Wed, 28 Sep 2016 14:25:52 +0000 (UTC)";
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content")
                    .addHeader(RFC2822Headers.DATE, expectedDate))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .build();

            dsnBounce.service(mail);

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            assertThat(sentMail.getSender()).isNull();
            assertThat(sentMail.getRecipients()).containsOnly(senderMailAddress);
            MimeMessage sentMessage = sentMail.getMsg();
            assertThat(sentMessage.getHeader(RFC2822Headers.DATE)[0]).isEqualTo(expectedDate);
        }

        @Test
        void dsnBounceShouldAddPrefixToSubjectWhenPrefixIsConfigured() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("prefix", "pre")
                .build();
            dsnBounce.init(mailetConfig);

            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(MailAddressFixture.ANY_AT_JAMES)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("My subject"))
                .build();

            dsnBounce.service(mail);

            assertThat(fakeMailContext.getSentMails()).hasSize(1).allSatisfy(
                sentMail -> assertThat(sentMail.getSubject()).contains("pre My subject"));
        }

        @Test
        void dsnBounceShouldAllowSenderSpecialPostmaster() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("sender", "postmaster")
                .build();
            dsnBounce.init(mailetConfig);

            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(MailAddressFixture.ANY_AT_JAMES)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("My subject"))
                .build();

            dsnBounce.service(mail);

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);

            assertThat(sentMail.getMsg().getFrom())
                .containsOnly(fakeMailContext.getPostmaster().toInternetAddress().get());
            assertThat(sentMail.getRecipients()).containsOnly(mail.getSender());
        }

        @Test
        void dsnBounceShouldAllowSenderSpecialSender() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("sender", "sender")
                .setProperty("prefix", "pre")
                .build();
            dsnBounce.init(mailetConfig);

            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(MailAddressFixture.ANY_AT_JAMES)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("My subject"))
                .build();

            dsnBounce.service(mail);

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);

            assertThat(sentMail.getMsg().getFrom()).containsOnly(mail.getSender().toInternetAddress().get());
            assertThat(sentMail.getRecipients()).containsOnly(mail.getSender());
        }

        @Test
        void dsnBounceShouldAllowSenderSpecialUnaltered() throws Exception {

            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("sender", "unaltered")
                .build();
            dsnBounce.init(mailetConfig);

            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(MailAddressFixture.ANY_AT_JAMES)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("My subject"))
                .build();

            dsnBounce.service(mail);

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);

            assertThat(sentMail.getMsg().getFrom()).containsOnly(mail.getSender().toInternetAddress().get());
            assertThat(sentMail.getRecipients()).containsOnly(mail.getSender());
        }

        @Test
        void dsnBounceShouldAllowSenderSpecialAddress() throws Exception {

            MailAddress bounceSender = new MailAddress("bounces@domain.com");
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("sender", bounceSender.asPrettyString())
                .build();
            dsnBounce.init(mailetConfig);

            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(MailAddressFixture.ANY_AT_JAMES)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("My subject"))
                .build();

            dsnBounce.service(mail);

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);

            assertThat(sentMail.getMsg().getFrom()).containsOnly(bounceSender.toInternetAddress().get());
            assertThat(sentMail.getRecipients()).containsOnly(mail.getSender());
        }
    }

    @Nested
    class Attachments {
        @Test
        void serviceShouldNotAttachTheOriginalMailWhenAttachmentIsEqualToNoneAndDsnRet() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("attachment", "none")
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .build();
            mail.setDsnParameters(DsnParameters.builder().ret(DsnParameters.Ret.FULL).build().get());

            dsnBounce.service(mail);

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            assertThat(sentMail.getSender()).isNull();
            assertThat(sentMail.getRecipients()).containsOnly(senderMailAddress);
            MimeMessage sentMessage = sentMail.getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            assertThat(content.getCount()).isEqualTo(2);
        }

        @Test
        void serviceShouldAttachTheOriginalMailWhenAttachmentIsEqualToAll() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("attachment", "all")
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
                .setText("My content")
                .build();
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .mimeMessage(mimeMessage)
                .build();
            MimeMessage mimeMessageCopy = new MimeMessage(mimeMessage);

            dsnBounce.service(mail);

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            assertThat(sentMail.getSender()).isNull();
            assertThat(sentMail.getRecipients()).containsOnly(senderMailAddress);
            MimeMessage sentMessage = sentMail.getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();

            assertThat(sentMail.getMsg().getContentType()).startsWith("multipart/report;");
            assertThat(MimeMessageUtil.asString((MimeMessage) content.getBodyPart(2).getContent()))
                .isEqualTo(MimeMessageUtil.asString(mimeMessageCopy));
        }

        @Test
        void serviceShouldAttachTheOriginalMailHeadersOnlyWhenAttachmentIsEqualToHeads() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("attachment", "heads")
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content")
                    .addHeader("myHeader", "myValue")
                    .setSubject("mySubject"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .build();

            dsnBounce.service(mail);

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            assertThat(sentMail.getSender()).isNull();
            assertThat(sentMail.getRecipients()).containsOnly(senderMailAddress);
            MimeMessage sentMessage = sentMail.getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(2);
            SharedByteArrayInputStream actualContent = (SharedByteArrayInputStream) bodyPart.getContent();
            assertThat(IOUtils.toString(actualContent, StandardCharsets.UTF_8))
                .contains("Subject: mySubject")
                .contains("myHeader: myValue");
            assertThat(bodyPart.getContentType()).isEqualTo("text/rfc822-headers; name=mySubject");
        }

        @Test
        void serviceShouldAttachTheOriginalMailWhenRequestedByTheSMTPClient() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("attachment", "heads")
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
                .setText("My content")
                .build();
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .mimeMessage(mimeMessage)
                .build();
            mail.setDsnParameters(DsnParameters.builder().ret(DsnParameters.Ret.FULL).build().get());
            MimeMessage mimeMessageCopy = new MimeMessage(mimeMessage);

            dsnBounce.service(mail);

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            assertThat(sentMail.getSender()).isNull();
            assertThat(sentMail.getRecipients()).containsOnly(senderMailAddress);
            MimeMessage sentMessage = sentMail.getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();

            assertThat(sentMail.getMsg().getContentType()).startsWith("multipart/report;");
            assertThat(MimeMessageUtil.asString((MimeMessage) content.getBodyPart(2).getContent()))
                .isEqualTo(MimeMessageUtil.asString(mimeMessageCopy));
        }

        @Test
        void serviceShouldAttachTheOriginalHeadersWhenRequestedByTheSMTPClient() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("attachment", "all")
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content")
                    .addHeader("myHeader", "myValue")
                    .setSubject("mySubject"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .build();
            mail.setDsnParameters(DsnParameters.builder().ret(DsnParameters.Ret.HDRS).build().get());

            dsnBounce.service(mail);

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            assertThat(sentMail.getSender()).isNull();
            assertThat(sentMail.getRecipients()).containsOnly(senderMailAddress);
            MimeMessage sentMessage = sentMail.getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(2);
            SharedByteArrayInputStream actualContent = (SharedByteArrayInputStream) bodyPart.getContent();
            assertThat(IOUtils.toString(actualContent, StandardCharsets.UTF_8))
                .contains("Subject: mySubject")
                .contains("myHeader: myValue");
            assertThat(bodyPart.getContentType()).isEqualTo("text/rfc822-headers; name=mySubject");
        }
    }

    @Nested
    class FailedAction {
        @Test
        void serviceShouldSendMultipartMailToTheSender() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .build();

            dsnBounce.service(mail);

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            assertThat(sentMail.getSender()).isNull();
            assertThat(sentMail.getRecipients()).containsOnly(senderMailAddress);
            MimeMessage sentMessage = sentMail.getMsg();
            assertThat(sentMessage.getContentType()).contains("multipart/report;");
            assertThat(sentMessage.getContentType()).contains("report-type=delivery-status");
        }

        @Test
        void serviceShouldSendMultipartMailContainingTextPart() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .build();

            dsnBounce.service(mail);

            String hostname = InetAddress.getLocalHost().getHostName();
            String expectedContent = "Hi. This is the James mail server at " + hostname + ".\nI'm afraid I wasn't able to deliver your message to the following addresses.\nThis is a permanent error; I've given up. Sorry it didn't work out.  Below\nI include the list of recipients and the reason why I was unable to deliver\nyour message.\n\n" +
                "Failed recipient(s):\n" +
                "recipient@domain.com\n" +
                "\n" +
                "Error message:\n" +
                "Delivery error\n" +
                "\n";

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            MimeMessage sentMessage = sentMail.getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(0);
            assertThat(bodyPart.getContentType()).isEqualTo("text/plain; charset=us-ascii");
            assertThat(bodyPart.getContent()).isEqualTo(expectedContent);
        }

        @Test
        void originalSubjectShouldBeCarriedOver() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("Banana power!")
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .build();

            dsnBounce.service(mail);

            String hostname = InetAddress.getLocalHost().getHostName();
            String expectedContent = "Hi. This is the James mail server at " + hostname + ".\nI'm afraid I wasn't able to deliver your message to the following addresses.\nThis is a permanent error; I've given up. Sorry it didn't work out.  Below\nI include the list of recipients and the reason why I was unable to deliver\nyour message.\n\n" +
                "Original email subject: Banana power!\n\n" +
                "Failed recipient(s):\n" +
                "recipient@domain.com\n" +
                "\n" +
                "Error message:\n" +
                "Delivery error\n" +
                "\n";

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            MimeMessage sentMessage = sentMail.getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(0);
            assertThat(bodyPart.getContentType()).isEqualTo("text/plain; charset=us-ascii");
            assertThat(bodyPart.getContent()).isEqualTo(expectedContent);
        }

        @Test
        void serviceShouldSendMultipartMailContainingTextPartWhenCustomMessageIsConfigured() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("messageString", "My custom message\n")
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .build();

            dsnBounce.service(mail);

            String expectedContent = "My custom message\n\n" +
                "Failed recipient(s):\n" +
                "recipient@domain.com\n" +
                "\n" +
                "Error message:\n" +
                "Delivery error\n" +
                "\n";

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            MimeMessage sentMessage = sentMail.getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(0);
            assertThat(bodyPart.getContentType()).isEqualTo("text/plain; charset=us-ascii");
            assertThat(bodyPart.getContent()).isEqualTo(expectedContent);
        }

        @Test
        void serviceShouldSendMultipartMailContainingDSNPart() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .remoteAddr("remoteHost")
                .build();

            dsnBounce.service(mail);

            String expectedContent = "Reporting-MTA: dns; myhost\n" +
                "Received-From-MTA: dns; 111.222.333.444\n" +
                "\n" +
                "Final-Recipient: rfc822; recipient@domain.com\n" +
                "Action: failed\n" +
                "Status: Delivery error\n" +
                "Diagnostic-Code: X-James; Delivery error\n" +
                "Last-Attempt-Date: Thu, 8 Sep 2016 14:25:52 +0000\n";

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            MimeMessage sentMessage = sentMail.getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            SharedByteArrayInputStream actualContent = (SharedByteArrayInputStream) content.getBodyPart(1).getContent();
            assertThat(IOUtils.toString(actualContent, StandardCharsets.UTF_8)).isEqualTo(expectedContent);
        }
    }

    @Nested
    class DeliveredAction {
        @Test
        void serviceShouldSendMultipartMailToTheSender() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("action", "delivered")
                .setProperty("messageString", "Hi. Your mail was successfully delivered")
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .build();

            dsnBounce.service(mail);

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            assertThat(sentMail.getSender()).isNull();
            assertThat(sentMail.getRecipients()).containsOnly(senderMailAddress);
            MimeMessage sentMessage = sentMail.getMsg();
            assertThat(sentMessage.getContentType()).contains("multipart/report;");
            assertThat(sentMessage.getContentType()).contains("report-type=delivery-status");
        }

        @Test
        void serviceShouldSendMultipartMailContainingTextPart() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("action", "delivered")
                .setProperty("messageString", "Hi. Your mail was successfully delivered at [machine].\n")
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .build();

            dsnBounce.service(mail);

            String hostname = InetAddress.getLocalHost().getHostName();
            String expectedContent = "Hi. Your mail was successfully delivered at " + hostname + ".\n" +
                "\n" +
                "Delivered recipient(s):\n" +
                "recipient@domain.com\n" +
                "\n";

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            MimeMessage sentMessage = sentMail.getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(0);
            assertThat(bodyPart.getContentType()).isEqualTo("text/plain; charset=us-ascii");
            assertThat(bodyPart.getContent()).isEqualTo(expectedContent);
        }

        @Test
        void serviceShouldSendMultipartMailContainingDSNPart() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("action", "delivered")
                .setProperty("messageString", "Hi. Your mail was successfully delivered")
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .remoteAddr("remoteHost")
                .build();

            dsnBounce.service(mail);

            String expectedContent = "Reporting-MTA: dns; myhost\n" +
                "Received-From-MTA: dns; 111.222.333.444\n" +
                "\n" +
                "Final-Recipient: rfc822; recipient@domain.com\n" +
                "Action: delivered\n" +
                "Status: unknown\n" +
                "Last-Attempt-Date: Thu, 8 Sep 2016 14:25:52 +0000\n";

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            MimeMessage sentMessage = sentMail.getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            SharedByteArrayInputStream actualContent = (SharedByteArrayInputStream) content.getBodyPart(1).getContent();
            assertThat(IOUtils.toString(actualContent, StandardCharsets.UTF_8)).isEqualTo(expectedContent);
        }
    }

    @Nested
    class DelayedAction {
        @Test
        void serviceShouldSendMultipartMailToTheSender() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("action", "delayed")
                .setProperty("messageString", "Hi. Your mail was delayed at [machine]\n")
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .build();

            dsnBounce.service(mail);

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            assertThat(sentMail.getSender()).isNull();
            assertThat(sentMail.getRecipients()).containsOnly(senderMailAddress);
            MimeMessage sentMessage = sentMail.getMsg();
            assertThat(sentMessage.getContentType()).contains("multipart/report;");
            assertThat(sentMessage.getContentType()).contains("report-type=delivery-status");
        }

        @Test
        void serviceShouldSendMultipartMailContainingTextPart() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("action", "delayed")
                .setProperty("messageString", "Hi. Your mail was delayed at [machine].\n")
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .build();

            dsnBounce.service(mail);

            String hostname = InetAddress.getLocalHost().getHostName();
            String expectedContent = "Hi. Your mail was delayed at " + hostname + ".\n" +
                "\n" +
                "Delayed recipient(s):\n" +
                "recipient@domain.com\n" +
                "\n" +
                "Error message:\n" +
                "Delivery error\n\n";

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            MimeMessage sentMessage = sentMail.getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(0);
            assertThat(bodyPart.getContentType()).isEqualTo("text/plain; charset=us-ascii");
            assertThat(bodyPart.getContent()).isEqualTo(expectedContent);
        }

        @Test
        void serviceShouldSendMultipartMailContainingDSNPart() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("action", "delayed")
                .setProperty("messageString", "Hi. Your mail was delayed at [machine]\n")
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .remoteAddr("remoteHost")
                .build();

            dsnBounce.service(mail);

            String expectedContent = "Reporting-MTA: dns; myhost\n" +
                "Received-From-MTA: dns; 111.222.333.444\n" +
                "\n" +
                "Final-Recipient: rfc822; recipient@domain.com\n" +
                "Action: delayed\n" +
                "Status: Delivery error\n" +
                "Diagnostic-Code: X-James; Delivery error\n" +
                "Last-Attempt-Date: Thu, 8 Sep 2016 14:25:52 +0000\n";

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            MimeMessage sentMessage = sentMail.getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            SharedByteArrayInputStream actualContent = (SharedByteArrayInputStream) content.getBodyPart(1).getContent();
            assertThat(IOUtils.toString(actualContent, StandardCharsets.UTF_8)).isEqualTo(expectedContent);
        }
    }

    @Nested
    class RelayedAction {
        @Test
        void serviceShouldSendMultipartMailToTheSender() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("action", "relayed")
                .setProperty("messageString", "Hi. Your mail was relayed at [machine]\n")
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .build();

            dsnBounce.service(mail);

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            assertThat(sentMail.getSender()).isNull();
            assertThat(sentMail.getRecipients()).containsOnly(senderMailAddress);
            MimeMessage sentMessage = sentMail.getMsg();
            assertThat(sentMessage.getContentType()).contains("multipart/report;");
            assertThat(sentMessage.getContentType()).contains("report-type=delivery-status");
        }

        @Test
        void serviceShouldSendMultipartMailContainingTextPart() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("action", "relayed")
                .setProperty("messageString", "Hi. Your mail was relayed at [machine].\n")
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .build();

            dsnBounce.service(mail);

            String hostname = InetAddress.getLocalHost().getHostName();
            String expectedContent = "Hi. Your mail was relayed at " + hostname + ".\n" +
                "\n" +
                "Relayed recipient(s):\n" +
                "recipient@domain.com\n" +
                "\n";

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            MimeMessage sentMessage = sentMail.getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(0);
            assertThat(bodyPart.getContentType()).isEqualTo("text/plain; charset=us-ascii");
            assertThat(bodyPart.getContent()).isEqualTo(expectedContent);
        }

        @Test
        void serviceShouldSendMultipartMailContainingDSNPart() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("action", "relayed")
                .setProperty("messageString", "Hi. Your mail was delayed at [machine]\n")
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .remoteAddr("remoteHost")
                .build();

            dsnBounce.service(mail);

            String expectedContent = "Reporting-MTA: dns; myhost\n" +
                "Received-From-MTA: dns; 111.222.333.444\n" +
                "\n" +
                "Final-Recipient: rfc822; recipient@domain.com\n" +
                "Action: relayed\n" +
                "Status: unknown\n" +
                "Last-Attempt-Date: Thu, 8 Sep 2016 14:25:52 +0000\n";

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            MimeMessage sentMessage = sentMail.getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            SharedByteArrayInputStream actualContent = (SharedByteArrayInputStream) content.getBodyPart(1).getContent();
            assertThat(IOUtils.toString(actualContent, StandardCharsets.UTF_8)).isEqualTo(expectedContent);
        }
    }

    @Nested
    class ExpandedAction {
        @Test
        void serviceShouldSendMultipartMailToTheSender() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("action", "expanded")
                .setProperty("messageString", "Hi. Your mail was expanded at [machine]\n")
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .build();

            dsnBounce.service(mail);

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            assertThat(sentMail.getSender()).isNull();
            assertThat(sentMail.getRecipients()).containsOnly(senderMailAddress);
            MimeMessage sentMessage = sentMail.getMsg();
            assertThat(sentMessage.getContentType()).contains("multipart/report;");
            assertThat(sentMessage.getContentType()).contains("report-type=delivery-status");
        }

        @Test
        void serviceShouldSendMultipartMailContainingTextPart() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("action", "expanded")
                .setProperty("messageString", "Hi. Your mail was expanded at [machine].\n")
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .build();

            dsnBounce.service(mail);

            String hostname = InetAddress.getLocalHost().getHostName();
            String expectedContent = "Hi. Your mail was expanded at " + hostname + ".\n" +
                "\n" +
                "Expanded recipient(s):\n" +
                "recipient@domain.com\n" +
                "\n";

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            MimeMessage sentMessage = sentMail.getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(0);
            assertThat(bodyPart.getContentType()).isEqualTo("text/plain; charset=us-ascii");
            assertThat(bodyPart.getContent()).isEqualTo(expectedContent);
        }

        @Test
        void serviceShouldSendMultipartMailContainingDSNPart() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("action", "expanded")
                .setProperty("messageString", "Hi. Your mail was expanded at [machine]\n")
                .build();
            dsnBounce.init(mailetConfig);

            MailAddress senderMailAddress = new MailAddress("sender@domain.com");
            FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(senderMailAddress)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .remoteAddr("remoteHost")
                .build();

            dsnBounce.service(mail);

            String expectedContent = "Reporting-MTA: dns; myhost\n" +
                "Received-From-MTA: dns; 111.222.333.444\n" +
                "\n" +
                "Final-Recipient: rfc822; recipient@domain.com\n" +
                "Action: expanded\n" +
                "Status: unknown\n" +
                "Last-Attempt-Date: Thu, 8 Sep 2016 14:25:52 +0000\n";

            List<SentMail> sentMails = fakeMailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            SentMail sentMail = sentMails.get(0);
            MimeMessage sentMessage = sentMail.getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            SharedByteArrayInputStream actualContent = (SharedByteArrayInputStream) content.getBodyPart(1).getContent();
            assertThat(IOUtils.toString(actualContent, StandardCharsets.UTF_8)).isEqualTo(expectedContent);
        }
    }

    @Test
    void envIdShouldBePositioned() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName(MAILET_NAME)
            .mailetContext(fakeMailContext)
            .build();
        dsnBounce.init(mailetConfig);

        MailAddress senderMailAddress = new MailAddress("sender@domain.com");
        FakeMail mail = FakeMail.builder()
            .name(MAILET_NAME)
            .sender(senderMailAddress)
            .attribute(DELIVERY_ERROR_ATTRIBUTE)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("My content"))
            .recipient("recipient@domain.com")
            .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
            .remoteAddr("remoteHost")
            .build();
        mail.setDsnParameters(DsnParameters.builder().envId(DsnParameters.EnvId.of("xyz")).build().get());

        dsnBounce.service(mail);

        String expectedContent = "Reporting-MTA: dns; myhost\n" +
            "Received-From-MTA: dns; 111.222.333.444\n" +
            "Original-Envelope-Id: xyz\n" +
            "\n" +
            "Final-Recipient: rfc822; recipient@domain.com\n" +
            "Action: failed\n" +
            "Status: Delivery error\n" +
            "Diagnostic-Code: X-James; Delivery error\n" +
            "Last-Attempt-Date: Thu, 8 Sep 2016 14:25:52 +0000\n";

        List<SentMail> sentMails = fakeMailContext.getSentMails();
        assertThat(sentMails).hasSize(1);
        SentMail sentMail = sentMails.get(0);
        MimeMessage sentMessage = sentMail.getMsg();
        MimeMultipart content = (MimeMultipart) sentMessage.getContent();
        SharedByteArrayInputStream actualContent = (SharedByteArrayInputStream) content.getBodyPart(1).getContent();
        assertThat(IOUtils.toString(actualContent, StandardCharsets.UTF_8)).isEqualTo(expectedContent);
    }

    @Test
    void arrivalDateShouldBePositioned() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName(MAILET_NAME)
            .mailetContext(fakeMailContext)
            .build();
        dsnBounce.init(mailetConfig);

        MailAddress senderMailAddress = new MailAddress("sender@domain.com");
        FakeMail mail = FakeMail.builder()
            .name(MAILET_NAME)
            .sender(senderMailAddress)
            .attribute(DELIVERY_ERROR_ATTRIBUTE)
            .attribute(new Attribute(AttributeName.of("dsn-arrival-date"), AttributeValue.of(ZonedDateTime.parse("2015-10-30T16:12:00Z"))))
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("My content"))
            .recipient("recipient@domain.com")
            .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
            .remoteAddr("remoteHost")
            .build();
        mail.setDsnParameters(DsnParameters.builder().envId(DsnParameters.EnvId.of("xyz")).build().get());

        dsnBounce.service(mail);

        String expectedContent = "Reporting-MTA: dns; myhost\n" +
            "Received-From-MTA: dns; 111.222.333.444\n" +
            "Original-Envelope-Id: xyz\n" +
            "Arrival-Date: Fri, 30 Oct 2015 16:12:00 +0000\n" +
            "\n" +
            "Final-Recipient: rfc822; recipient@domain.com\n" +
            "Action: failed\n" +
            "Status: Delivery error\n" +
            "Diagnostic-Code: X-James; Delivery error\n" +
            "Last-Attempt-Date: Thu, 8 Sep 2016 14:25:52 +0000\n";

        List<SentMail> sentMails = fakeMailContext.getSentMails();
        assertThat(sentMails).hasSize(1);
        SentMail sentMail = sentMails.get(0);
        MimeMessage sentMessage = sentMail.getMsg();
        MimeMultipart content = (MimeMultipart) sentMessage.getContent();
        SharedByteArrayInputStream actualContent = (SharedByteArrayInputStream) content.getBodyPart(1).getContent();
        assertThat(IOUtils.toString(actualContent, StandardCharsets.UTF_8)).isEqualTo(expectedContent);
    }

    @Test
    void defaultStatusShouldBeUsedWhenNone() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName(MAILET_NAME)
            .mailetContext(fakeMailContext)
            .setProperty("defaultStatus", "4.0.0")
            .build();
        dsnBounce.init(mailetConfig);

        MailAddress senderMailAddress = new MailAddress("sender@domain.com");
        FakeMail mail = FakeMail.builder()
            .name(MAILET_NAME)
            .sender(senderMailAddress)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("My content"))
            .recipient("recipient@domain.com")
            .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
            .remoteAddr("remoteHost")
            .build();
        mail.setDsnParameters(DsnParameters.builder().envId(DsnParameters.EnvId.of("xyz")).build().get());

        dsnBounce.service(mail);

        String expectedContent = "Reporting-MTA: dns; myhost\n" +
            "Received-From-MTA: dns; 111.222.333.444\n" +
            "Original-Envelope-Id: xyz\n" +
            "\n" +
            "Final-Recipient: rfc822; recipient@domain.com\n" +
            "Action: failed\n" +
            "Status: 4.0.0\n" +
            "Diagnostic-Code: X-James; 4.0.0\n" +
            "Last-Attempt-Date: Thu, 8 Sep 2016 14:25:52 +0000\n";

        List<SentMail> sentMails = fakeMailContext.getSentMails();
        assertThat(sentMails).hasSize(1);
        SentMail sentMail = sentMails.get(0);
        MimeMessage sentMessage = sentMail.getMsg();
        MimeMultipart content = (MimeMultipart) sentMessage.getContent();
        SharedByteArrayInputStream actualContent = (SharedByteArrayInputStream) content.getBodyPart(1).getContent();
        assertThat(IOUtils.toString(actualContent, StandardCharsets.UTF_8)).isEqualTo(expectedContent);
    }

    @Test
    void errorMessagePartShouldNotBeAddedWhenNoError() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName(MAILET_NAME)
            .mailetContext(fakeMailContext)
            .setProperty("defaultStatus", "4.0.0")
            .build();
        dsnBounce.init(mailetConfig);

        MailAddress senderMailAddress = new MailAddress("sender@domain.com");
        FakeMail mail = FakeMail.builder()
            .name(MAILET_NAME)
            .sender(senderMailAddress)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("My content"))
            .recipient("recipient@domain.com")
            .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
            .remoteAddr("remoteHost")
            .build();
        mail.setDsnParameters(DsnParameters.builder().envId(DsnParameters.EnvId.of("xyz")).build().get());

        dsnBounce.service(mail);

        List<SentMail> sentMails = fakeMailContext.getSentMails();
        assertThat(sentMails).hasSize(1);
        SentMail sentMail = sentMails.get(0);
        MimeMessage sentMessage = sentMail.getMsg();

        assertThat(MimeMessageUtil.asString(sentMessage))
            .doesNotContain("Error message:");
    }

    @Test
    void errorShouldBeAdded() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName(MAILET_NAME)
            .mailetContext(fakeMailContext)
            .setProperty("defaultStatus", "4.0.0")
            .build();
        dsnBounce.init(mailetConfig);

        MailAddress senderMailAddress = new MailAddress("sender@domain.com");
        FakeMail mail = FakeMail.builder()
            .name(MAILET_NAME)
            .sender(senderMailAddress)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("My content"))
            .recipient("recipient@domain.com")
            .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
            .remoteAddr("remoteHost")
            .errorMessage("This is what happen...")
            .build();
        mail.setDsnParameters(DsnParameters.builder().envId(DsnParameters.EnvId.of("xyz")).build().get());

        dsnBounce.service(mail);

        List<SentMail> sentMails = fakeMailContext.getSentMails();
        assertThat(sentMails).hasSize(1);
        SentMail sentMail = sentMails.get(0);
        MimeMessage sentMessage = sentMail.getMsg();

        assertThat(MimeMessageUtil.asString(sentMessage))
            .contains("Error message:\nThis is what happen...");
    }

    @Test
    void deliveryErrorShouldBeAdded() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName(MAILET_NAME)
            .mailetContext(fakeMailContext)
            .setProperty("defaultStatus", "4.0.0")
            .build();
        dsnBounce.init(mailetConfig);

        MailAddress senderMailAddress = new MailAddress("sender@domain.com");
        FakeMail mail = FakeMail.builder()
            .name(MAILET_NAME)
            .sender(senderMailAddress)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("My content"))
            .recipient("recipient@domain.com")
            .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
            .remoteAddr("remoteHost")
            .attribute(new Attribute(AttributeName.of("delivery-error"), AttributeValue.of("This is what happen...")))
            .build();
        mail.setDsnParameters(DsnParameters.builder().envId(DsnParameters.EnvId.of("xyz")).build().get());

        dsnBounce.service(mail);

        List<SentMail> sentMails = fakeMailContext.getSentMails();
        assertThat(sentMails).hasSize(1);
        SentMail sentMail = sentMails.get(0);
        MimeMessage sentMessage = sentMail.getMsg();

        assertThat(MimeMessageUtil.asString(sentMessage))
            .contains("Error message:\nThis is what happen...");
    }
}