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

import java.net.UnknownHostException;

import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailContext.SentMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RedirectTest {

    private static final String MAILET_NAME = "mailetName";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private Redirect redirect;
    private FakeMailContext fakeMailContext;
    private MailAddress postmaster;

    @Before
    public void setUp() throws Exception {
        DNSService dnsService = mock(DNSService.class);
        redirect = new Redirect(dnsService);
        postmaster = new MailAddress("postmaster@james.org");
        fakeMailContext = FakeMailContext.builder()
                .postmaster(postmaster)
                .build();

        when(dnsService.getLocalHost()).thenThrow(new UnknownHostException());
    }

    @Test
    public void getMailetInfoShouldReturnValue() {
        assertThat(redirect.getMailetInfo()).isEqualTo("Redirect Mailet");
    }

    @Test
    public void initShouldThrowWhenUnkownParameter() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("unknown", "error")
                .build();
        expectedException.expect(MessagingException.class);

        redirect.init(mailetConfig);
    }

    @Test
    public void initShouldNotThrowWhenEveryParameters() throws Exception {
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
    public void initShouldReturnEmptyWhenNoRecipientsOrToParameters() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
        redirect.init(mailetConfig);

        assertThat(redirect.getRecipients()).isEmpty();
    }

    @Test
    public void getRecipientsShouldThrowWhenUnparsableRecipientsAddress() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("recipients", "user@james@org")
                .build();
        redirect.init(mailetConfig);

        expectedException.expect(MessagingException.class);
        redirect.getRecipients();
    }

    @Test
    public void getRecipientsShouldThrowWhenUnparsableToAddress() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("to", "user@james@org")
                .build();
        redirect.init(mailetConfig);

        expectedException.expect(MessagingException.class);
        redirect.getRecipients();
    }

    @Test
    public void getRecipientsShouldThrowWhenRecipientsAndToAreEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("recipients", "")
                .setProperty("to", "")
                .build();
        redirect.init(mailetConfig);

        expectedException.expect(MessagingException.class);
        redirect.getRecipients();
    }

    @Test
    public void getRecipientsShouldReturnSpecialAddressWhenRecipientsIsMatchingOne() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("recipients", "postmaster")
                .build();
        redirect.init(mailetConfig);

        assertThat(redirect.getRecipients()).containsOnly(postmaster);
    }

    @Test
    public void getToShouldThrowWhenUnparsableRecipientsAddress() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("recipients", "user@james@org")
                .build();
        redirect.init(mailetConfig);

        expectedException.expect(MessagingException.class);
        redirect.getTo();
    }

    @Test
    public void getToShouldThrowWhenUnparsableToAddress() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("to", "user@james@org")
                .build();
        redirect.init(mailetConfig);

        expectedException.expect(MessagingException.class);
        redirect.getTo();
    }

    @Test
    public void getToShouldThrowWhenRecipientsAndToAreEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("recipients", "")
                .setProperty("to", "")
                .build();
        redirect.init(mailetConfig);

        expectedException.expect(MessagingException.class);
        redirect.getTo();
    }

    @Test
    public void getToShouldReturnSpecialAddressWhenRecipientsIsMatchingOne() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("recipients", "postmaster")
                .build();
        redirect.init(mailetConfig);

        assertThat(redirect.getTo()).containsOnly(postmaster.toInternetAddress());
    }

    @Test
    public void getReversePathShouldReturnAbsentWhenNoReversePathParameter() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
        redirect.init(mailetConfig);

        assertThat(redirect.getReversePath()).isEmpty();
    }

    @Test
    public void getReversePathShouldThrowWhenUnparsableReversePathParameter() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("reversePath", "reverse@james@org")
                .build();
        redirect.init(mailetConfig);

        expectedException.expect(MessagingException.class);
        redirect.getReversePath();
    }

    @Test
    public void getReversePathShouldReturnSpecialAddressWhenReversePathIsMatchingOne() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("reversePath", "postmaster")
                .build();
        redirect.init(mailetConfig);

        assertThat(redirect.getReversePath()).contains(postmaster);
    }

    @Test
    public void getReversePathShouldReturnMailAddressWhenReversePathIsGiven() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("reversePath", "reverse@james.org")
                .build();
        redirect.init(mailetConfig);

        assertThat(redirect.getReversePath()).contains(new MailAddress("reverse@james.org"));
    }

    @Test
    public void getReversePathWithMailShouldReturnAbsentWhenNotStaticAndReversePathParameters() throws Exception {
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
    public void getReversePathWithMailShouldReturnReversePathWhenReversePathIsGiven() throws Exception {
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
    public void getReversePathWithMailShouldReturnSpecialAddressWhenReversePathIsMatchingOne() throws Exception {
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
    public void getReversePathWithMailShouldReturnSpecialAddressWhenNotStaticAndNewReversePathIsMatchingOne() throws Exception {
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
    public void getReversePathWithMailShouldReturnSenderWhenNoReversePath() throws Exception {
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
    public void redirectShouldNotModifyOriginalSubject() throws Exception {
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
    public void redirectShouldAddPrefixAndSubjectToSentMail() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("subject", "subj")
                .setProperty("prefix", "pre")
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

        SentMail newMail = fakeMailContext.getSentMails().get(0);
        assertThat(newMail.getSubject()).contains("pre subj");
    }
}
