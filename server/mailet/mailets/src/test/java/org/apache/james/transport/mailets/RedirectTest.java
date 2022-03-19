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

import java.net.UnknownHostException;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailContext.SentMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedirectTest {

    private static final String MAILET_NAME = "mailetName";

    private Redirect redirect;
    private FakeMailContext fakeMailContext;
    private MailAddress postmaster;

    @BeforeEach
    void setUp() throws Exception {
        DNSService dnsService = mock(DNSService.class);
        redirect = new Redirect(dnsService);
        postmaster = new MailAddress("postmaster@james.org");
        fakeMailContext = FakeMailContext.builder()
                .postmaster(postmaster)
                .build();

        when(dnsService.getLocalHost()).thenThrow(new UnknownHostException());
    }

    @Test
    void getMailetInfoShouldReturnValue() {
        assertThat(redirect.getMailetInfo()).isEqualTo("Redirect Mailet");
    }

    @Test
    void initShouldThrowWhenUnkownParameter() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("unknown", "error")
                .build();

        assertThatThrownBy(() -> redirect.init(mailetConfig))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void initShouldNotThrowWhenEveryParameters() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("static", "true")
                .setProperty("debug", "true")
                .setProperty("passThrough", "false")
                .setProperty("fakeDomainCheck", "false")
                .setProperty("inline", "true")
                .setProperty("attachment", "true")
                .setProperty("message", "mess")
                .setProperty("recipients", "user@james.org, user2@james.org")
                .setProperty("to", "to@james.org")
                .setProperty("replyTo", "replyTo@james.org")
                .setProperty("replyto", "replyto@james.org")
                .setProperty("reversePath", "reverse@james.org")
                .setProperty("sender", "sender@james.org")
                .setProperty("subject", "subj")
                .setProperty("prefix", "pref")
                .setProperty("attachError", "true")
                .setProperty("isReply", "true")
                .build();

        redirect.init(mailetConfig);
    }

    @Test
    void initShouldReturnEmptyWhenNoRecipientsOrToParameters() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
        redirect.init(mailetConfig);

        assertThat(redirect.getRecipients()).isEmpty();
    }

    @Test
    void getRecipientsShouldThrowWhenUnparsableRecipientsAddress() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("recipients", "user@james@org")
                .build();
        redirect.init(mailetConfig);

        assertThatThrownBy(() -> redirect.getRecipients())
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getRecipientsShouldThrowWhenUnparsableToAddress() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("to", "user@james@org")
                .build();
        redirect.init(mailetConfig);

        assertThatThrownBy(() -> redirect.getRecipients())
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void getRecipientsShouldThrowWhenRecipientsAndToAreEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("recipients", "")
                .setProperty("to", "")
                .build();
        redirect.init(mailetConfig);

        assertThatThrownBy(() -> redirect.getRecipients())
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getRecipientsShouldReturnSpecialAddressWhenRecipientsIsMatchingOne() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("recipients", "postmaster")
                .build();
        redirect.init(mailetConfig);

        assertThat(redirect.getRecipients()).containsOnly(postmaster);
    }

    @Test
    void getToShouldThrowWhenUnparsableRecipientsAddress() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("recipients", "user@james@org")
                .build();
        redirect.init(mailetConfig);

        assertThatThrownBy(() -> redirect.getTo())
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getToShouldThrowWhenUnparsableToAddress() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("to", "user@james@org")
                .build();
        redirect.init(mailetConfig);

        assertThatThrownBy(() -> redirect.getTo())
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getToShouldThrowWhenRecipientsAndToAreEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("recipients", "")
                .setProperty("to", "")
                .build();
        redirect.init(mailetConfig);

        assertThatThrownBy(() -> redirect.getTo())
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getToShouldReturnSpecialAddressWhenRecipientsIsMatchingOne() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("recipients", "postmaster")
                .build();
        redirect.init(mailetConfig);

        assertThat(redirect.getTo()).containsOnly(postmaster.toInternetAddress().get());
    }

    @Test
    void getReversePathShouldReturnAbsentWhenNoReversePathParameter() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
        redirect.init(mailetConfig);

        assertThat(redirect.getReversePath()).isEmpty();
    }

    @Test
    void getReversePathShouldThrowWhenUnparsableReversePathParameter() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("reversePath", "reverse@james@org")
                .build();
        redirect.init(mailetConfig);

        assertThatThrownBy(() -> redirect.getReversePath())
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getReversePathShouldReturnSpecialAddressWhenReversePathIsMatchingOne() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("reversePath", "postmaster")
                .build();
        redirect.init(mailetConfig);

        assertThat(redirect.getReversePath()).contains(postmaster);
    }

    @Test
    void getReversePathShouldReturnMailAddressWhenReversePathIsGiven() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("reversePath", "reverse@james.org")
                .build();
        redirect.init(mailetConfig);

        assertThat(redirect.getReversePath()).contains(new MailAddress("reverse@james.org"));
    }

    @Test
    void getReversePathWithMailShouldReturnAbsentWhenNotStaticAndReversePathParameters() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
        redirect.init(mailetConfig);

        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("subject")
            .setText("This is a fake mail"));

        assertThat(redirect.getReversePath(mail)).isEmpty();
    }

    @Test
    void getReversePathWithMailShouldReturnReversePathWhenReversePathIsGiven() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("reversePath", "reverse@james.org")
                .build();
        redirect.init(mailetConfig);

        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("subject")
            .setText("This is a fake mail"));

        assertThat(redirect.getReversePath(mail)).contains(new MailAddress("reverse@james.org"));
    }

    @Test
    void getReversePathWithMailShouldReturnSpecialAddressWhenReversePathIsMatchingOne() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("reversePath", "postmaster")
                .build();
        redirect.init(mailetConfig);

        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("subject")
            .setText("This is a fake mail"));

        assertThat(redirect.getReversePath(mail)).contains(postmaster);
    }

    @Test
    void getReversePathWithMailShouldReturnSpecialAddressWhenNotStaticAndNewReversePathIsMatchingOne() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("static", "false")
                .setProperty("reversePath", "postmaster")
                .build();
        redirect.init(mailetConfig);

        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("subject")
            .setText("This is a fake mail"));

        assertThat(redirect.getReversePath(mail)).contains(postmaster);
    }

    @Test
    void getReversePathWithMailShouldReturnSenderWhenNoReversePath() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("sender", "sender@james.org")
                .build();
        redirect.init(mailetConfig);

        FakeMail mail = FakeMail.from(MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("subject")
            .setText("This is a fake mail"));

        assertThat(redirect.getReversePath(mail)).contains(new MailAddress("sender@james.org"));
    }

    @Test
    void redirectShouldNotModifyOriginalSubject() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("subject", "subj")
                .setProperty("prefix", "pref")
                .build();
        redirect.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(MailAddressFixture.ANY_AT_JAMES)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("My subject")
                    .setText("content"))
                .build();

        redirect.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo("My subject");
    }

    @Test
    void redirectShouldAddPrefixAndSubjectToSentMail() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("subject", "subj")
                .setProperty("prefix", "pre")
                .build();
        redirect.init(mailetConfig);


        FakeMail mail = FakeMail.builder()
            .name(MAILET_NAME)
            .recipient(MailAddressFixture.RECIPIENT1)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("My subject")
                .setText("content"))
            .build();

        redirect.service(mail);

        SentMail newMail = fakeMailContext.getSentMails().get(0);
        assertThat(newMail.getSubject()).contains("pre subj");
    }

    @Test
    void unalteredShouldPreserveMessageId() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName(MAILET_NAME)
            .mailetContext(fakeMailContext)
            .setProperty("inline", "unaltered")
            .build();
        redirect.init(mailetConfig);

        String messageId = "<matchme@localhost>";
        FakeMail mail = FakeMail.builder()
            .name(MAILET_NAME)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .recipient(MailAddressFixture.RECIPIENT1)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader("Message-ID", messageId)
                .setSubject("My subject")
                .setText("Content"))
            .build();

        redirect.service(mail);

        SentMail newMail = fakeMailContext.getSentMails().get(0);
        assertThat(newMail.getMsg().getMessageID())
            .isEqualTo(messageId);
    }

    @Test
    void alteredShouldResetMessageId() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName(MAILET_NAME)
            .mailetContext(fakeMailContext)
            .setProperty("inline", "all")
            .build();
        redirect.init(mailetConfig);

        String messageId = "<matchme@localhost>";
        FakeMail mail = FakeMail.builder()
            .name(MAILET_NAME)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .recipient(MailAddressFixture.RECIPIENT1)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader("Message-ID", messageId)
                .setSubject("My subject")
                .setText("Content"))
            .build();

        redirect.service(mail);

        SentMail newMail = fakeMailContext.getSentMails().get(0);
        assertThat(newMail.getMsg().getMessageID())
            .isNotEqualTo(messageId);
    }
}
