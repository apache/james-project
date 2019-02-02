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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.base.DateFormats;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.RFC2822Headers;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailContext.SentMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DSNBounceTest {

    private static final String MAILET_NAME = "mailetName";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private DSNBounce dsnBounce;
    private FakeMailContext fakeMailContext;

    @Before
    public void setUp() throws Exception {
        DNSService dnsService = mock(DNSService.class);
        dsnBounce = new DSNBounce(dnsService, DateFormats.getRFC822FormatForTimeZone(TimeZone.getTimeZone("UTC")));
        fakeMailContext = FakeMailContext.defaultContext();

        InetAddress localHost = InetAddress.getLocalHost();
        when(dnsService.getLocalHost())
            .thenReturn(localHost);
        when(dnsService.getHostName(localHost))
            .thenReturn("myhost");
    }

    @Test
    public void getMailetInfoShouldReturnValue() {
        assertThat(dsnBounce.getMailetInfo()).isEqualTo("DSNBounce Mailet");
    }

    @Test
    public void getAllowedInitParametersShouldReturnTheParameters() {
        assertThat(dsnBounce.getAllowedInitParameters()).containsOnly("debug", "passThrough", "messageString", "attachment", "sender", "prefix");
    }

    @Test
    public void initShouldFailWhenUnknownParameterIsConfigured() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("unknwon", "value")
                .build();
        expectedException.expect(MessagingException.class);

        dsnBounce.init(mailetConfig);
    }

    @Test
    public void getRecipientsShouldReturnReversePathOnly() {
        assertThat(dsnBounce.getRecipients()).containsOnly(SpecialAddress.REVERSE_PATH);
    }

    @Test
    public void getToShouldReturnReversePathOnly() {
        assertThat(dsnBounce.getTo()).containsOnly(SpecialAddress.REVERSE_PATH.toInternetAddress());
    }

    @Test
    public void getReversePathShouldReturnNullSpecialAddress() {
        Mail mail = null;
        assertThat(dsnBounce.getReversePath(mail)).contains(SpecialAddress.NULL);
    }

    @Test
    public void serviceShouldSendMultipartMailToTheSender() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
        dsnBounce.init(mailetConfig);

        MailAddress senderMailAddress = new MailAddress("sender@domain.com");
        FakeMail mail = FakeMail.builder()
                .sender(senderMailAddress)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .name(MAILET_NAME)
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
    public void serviceShouldSendMultipartMailContainingTextPart() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
        dsnBounce.init(mailetConfig);

        MailAddress senderMailAddress = new MailAddress("sender@domain.com");
        FakeMail mail = FakeMail.builder()
                .sender(senderMailAddress)
                .attribute("delivery-error", "Delivery error")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .name(MAILET_NAME)
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
    public void serviceShouldSendMultipartMailContainingTextPartWhenCustomMessageIsConfigured() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("messageString", "My custom message\n")
                .build();
        dsnBounce.init(mailetConfig);

        MailAddress senderMailAddress = new MailAddress("sender@domain.com");
        FakeMail mail = FakeMail.builder()
                .sender(senderMailAddress)
                .attribute("delivery-error", "Delivery error")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .name(MAILET_NAME)
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
    public void serviceShouldSendMultipartMailContainingDSNPart() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
        dsnBounce.init(mailetConfig);

        MailAddress senderMailAddress = new MailAddress("sender@domain.com");
        FakeMail mail = FakeMail.builder()
                .sender(senderMailAddress)
                .attribute("delivery-error", "Delivery error")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .name(MAILET_NAME)
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
                "Last-Attempt-Date: Thu, 8 Sep 2016 14:25:52 XXXXX (UTC)\n";

        List<SentMail> sentMails = fakeMailContext.getSentMails();
        assertThat(sentMails).hasSize(1);
        SentMail sentMail = sentMails.get(0);
        MimeMessage sentMessage = sentMail.getMsg();
        MimeMultipart content = (MimeMultipart) sentMessage.getContent();
        SharedByteArrayInputStream actualContent = (SharedByteArrayInputStream) content.getBodyPart(1).getContent();
        assertThat(IOUtils.toString(actualContent, StandardCharsets.UTF_8)).isEqualTo(expectedContent);
    }

    @Test
    public void serviceShouldUpdateTheMailStateWhenNoSenderAndPassThroughIsFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("passThrough", "false")
                .build();
        dsnBounce.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
                .attribute("delivery-error", "Delivery error")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .name(MAILET_NAME)
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .remoteAddr("remoteHost")
                .build();

        dsnBounce.service(mail);

        assertThat(mail.getState()).isEqualTo(Mail.GHOST);
    }

    @Test
    public void serviceShouldNotUpdateTheMailStateWhenNoSenderPassThroughHasDefaultValue() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
        dsnBounce.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
                .attribute("delivery-error", "Delivery error")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .name(MAILET_NAME)
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .remoteAddr("remoteHost")
                .build();

        dsnBounce.service(mail);

        assertThat(mail.getState()).isNull();
    }

    @Test
    public void serviceShouldNotUpdateTheMailStateWhenNoSenderPassThroughIsTrue() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("passThrough", "true")
                .build();
        dsnBounce.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
                .attribute("delivery-error", "Delivery error")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .name(MAILET_NAME)
                .recipient("recipient@domain.com")
                .lastUpdated(Date.from(Instant.parse("2016-09-08T14:25:52.000Z")))
                .remoteAddr("remoteHost")
                .build();

        dsnBounce.service(mail);

        assertThat(mail.getState()).isNull();
    }

    @Test
    public void serviceShouldNotAttachTheOriginalMailWhenAttachmentIsEqualToNone() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("attachment", "none")
                .build();
        dsnBounce.init(mailetConfig);

        MailAddress senderMailAddress = new MailAddress("sender@domain.com");
        FakeMail mail = FakeMail.builder()
                .sender(senderMailAddress)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .name(MAILET_NAME)
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
        assertThat(content.getCount()).isEqualTo(2);
    }

    @Test
    public void serviceShouldAttachTheOriginalMailWhenAttachmentIsEqualToAll() throws Exception {
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
            .sender(senderMailAddress)
            .name(MAILET_NAME)
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
    public void serviceShouldAttachTheOriginalMailHeadersOnlyWhenAttachmentIsEqualToHeads() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("attachment", "heads")
                .build();
        dsnBounce.init(mailetConfig);

        MailAddress senderMailAddress = new MailAddress("sender@domain.com");
        FakeMail mail = FakeMail.builder()
                .sender(senderMailAddress)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content")
                    .addHeader("myHeader", "myValue")
                    .setSubject("mySubject"))
                .name(MAILET_NAME)
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
    public void serviceShouldSetTheDateHeaderWhenNone() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
        dsnBounce.init(mailetConfig);

        MailAddress senderMailAddress = new MailAddress("sender@domain.com");
        FakeMail mail = FakeMail.builder()
                .sender(senderMailAddress)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content"))
                .name(MAILET_NAME)
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
        assertThat(sentMessage.getHeader(RFC2822Headers.DATE)).isNotNull();
    }

    @Test
    public void serviceShouldNotModifyTheDateHeaderWhenAlreadyPresent() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
        dsnBounce.init(mailetConfig);

        MailAddress senderMailAddress = new MailAddress("sender@domain.com");
        String expectedDate = "Wed, 28 Sep 2016 14:25:52 +0000 (UTC)";
        FakeMail mail = FakeMail.builder()
                .sender(senderMailAddress)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setText("My content")
                    .addHeader(RFC2822Headers.DATE, expectedDate))
                .name(MAILET_NAME)
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
    public void dsnBounceShouldAddPrefixToSubjectWhenPrefixIsConfigured() throws Exception {
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

        assertThat(mail.getMessage().getSubject()).isEqualTo("pre My subject");
    }
}