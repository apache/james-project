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
import static org.assertj.guava.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.UnknownHostException;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.mailet.MailAddress;
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
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("unknown", "error");
        expectedException.expect(MessagingException.class);

        redirect.init(mailetConfig);
    }

    @Test
    public void initShouldNotThrowWhenEveryParameters() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("static", "true");
        mailetConfig.setProperty("debug", "true");
        mailetConfig.setProperty("passThrough", "false");
        mailetConfig.setProperty("fakeDomainCheck", "false");
        mailetConfig.setProperty("inline", "true");
        mailetConfig.setProperty("attachment", "true");
        mailetConfig.setProperty("message", "mess");
        mailetConfig.setProperty("recipients", "user@james.org, user2@james.org");
        mailetConfig.setProperty("to", "to@james.org");
        mailetConfig.setProperty("replyTo", "replyTo@james.org");
        mailetConfig.setProperty("replyto", "replyto@james.org");
        mailetConfig.setProperty("reversePath", "reverse@james.org");
        mailetConfig.setProperty("sender", "sender@james.org");
        mailetConfig.setProperty("subject", "subj");
        mailetConfig.setProperty("prefix", "pref");
        mailetConfig.setProperty("attachError", "true");
        mailetConfig.setProperty("isReply", "true");

        redirect.init(mailetConfig);
    }

    @Test
    public void initShouldReturnEmptyWhenNoRecipientsOrToParameters() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        redirect.init(mailetConfig);

        assertThat(redirect.getRecipients()).isEmpty();
    }

    @Test
    public void getRecipientsShouldThrowWhenUnparsableRecipientsAddress() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("recipients", "user@james@org");
        redirect.init(mailetConfig);

        expectedException.expect(MessagingException.class);
        redirect.getRecipients();
    }

    @Test
    public void getRecipientsShouldThrowWhenUnparsableToAddress() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("to", "user@james@org");
        redirect.init(mailetConfig);

        expectedException.expect(MessagingException.class);
        redirect.getRecipients();
    }

    @Test
    public void getRecipientsShouldThrowWhenRecipientsAndToAreEmpty() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("recipients", "");
        mailetConfig.setProperty("to", "");
        redirect.init(mailetConfig);

        expectedException.expect(MessagingException.class);
        redirect.getRecipients();
    }

    @Test
    public void getRecipientsShouldReturnSpecialAddressWhenRecipientsIsMatchingOne() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("recipients", "postmaster");
        redirect.init(mailetConfig);

        assertThat(redirect.getRecipients()).containsOnly(postmaster);
    }

    @Test
    public void getToShouldThrowWhenUnparsableRecipientsAddress() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("recipients", "user@james@org");
        redirect.init(mailetConfig);

        expectedException.expect(MessagingException.class);
        redirect.getTo();
    }

    @Test
    public void getToShouldThrowWhenUnparsableToAddress() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("to", "user@james@org");
        redirect.init(mailetConfig);

        expectedException.expect(MessagingException.class);
        redirect.getTo();
    }

    @Test
    public void getToShouldThrowWhenRecipientsAndToAreEmpty() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("recipients", "");
        mailetConfig.setProperty("to", "");
        redirect.init(mailetConfig);

        expectedException.expect(MessagingException.class);
        redirect.getTo();
    }

    @Test
    public void getToShouldReturnSpecialAddressWhenRecipientsIsMatchingOne() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("recipients", "postmaster");
        redirect.init(mailetConfig);

        assertThat(redirect.getTo()).containsOnly(postmaster.toInternetAddress());
    }

    @Test
    public void getReversePathShouldReturnNullWhenNoReversePathParameter() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        redirect.init(mailetConfig);

        assertThat(redirect.getReversePath()).isNull();
    }

    @Test
    public void getReversePathShouldThrowWhenUnparsableReversePathParameter() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("reversePath", "reverse@james@org");
        redirect.init(mailetConfig);

        expectedException.expect(MessagingException.class);
        redirect.getReversePath();
    }

    @Test
    public void getReversePathShouldReturnSpecialAddressWhenReversePathIsMatchingOne() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("reversePath", "postmaster");
        redirect.init(mailetConfig);

        assertThat(redirect.getReversePath()).isEqualTo(postmaster);
    }

    @Test
    public void getReversePathShouldReturnMailAddressWhenReversePathIsGiven() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("reversePath", "reverse@james.org");
        redirect.init(mailetConfig);

        assertThat(redirect.getReversePath()).isEqualTo(new MailAddress("reverse@james.org"));
    }

    @Test
    public void getReversePathWithMailShouldReturnNullWhenNotStaticAndReversePathParameters() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        redirect.init(mailetConfig);

        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("subject");
        message.setText("This is a fake mail");
        FakeMail mail = FakeMail.from(message);

        assertThat(redirect.getReversePath(mail)).isNull();
    }

    @Test
    public void getReversePathWithMailShouldReturnReversePathWhenReversePathIsGiven() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("reversePath", "reverse@james.org");
        redirect.init(mailetConfig);

        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("subject");
        message.setText("This is a fake mail");
        FakeMail mail = FakeMail.from(message);

        assertThat(redirect.getReversePath(mail)).isEqualTo(new MailAddress("reverse@james.org"));
    }

    @Test
    public void getReversePathWithMailShouldReturnSpecialAddressWhenReversePathIsMatchingOne() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("reversePath", "postmaster");
        redirect.init(mailetConfig);

        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("subject");
        message.setText("This is a fake mail");
        FakeMail mail = FakeMail.from(message);

        assertThat(redirect.getReversePath(mail)).isEqualTo(postmaster);
    }

    @Test
    public void getReversePathWithMailShouldReturnSpecialAddressWhenNotStaticAndNewReversePathIsMatchingOne() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("static", "false");
        redirect.init(mailetConfig);

        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("subject");
        message.setText("This is a fake mail");
        FakeMail mail = FakeMail.from(message);

        mailetConfig.setProperty("reversePath", "postmaster");
        assertThat(redirect.getReversePath(mail)).isEqualTo(postmaster);
    }

    @Test
    public void getReversePathWithMailShouldReturnSenderWhenNoReversePath() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("sender", "sender@james.org");
        redirect.init(mailetConfig);

        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("subject");
        message.setText("This is a fake mail");
        FakeMail mail = FakeMail.from(message);

        assertThat(redirect.getReversePath(mail)).isEqualTo(new MailAddress("sender@james.org"));
    }

    @Test
    public void redirectShouldNotModifyOriginalSubject() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("subject", "subj");
        mailetConfig.setProperty("prefix", "pref");
        redirect.init(mailetConfig);

        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        mimeMessage.setSubject("My subject");
        mimeMessage.setText("content");
        FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(MailAddressFixture.ANY_AT_JAMES)
                .mimeMessage(mimeMessage)
                .build();

        redirect.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo("My subject");
    }

    @Test
    public void redirectShouldAddPrefixAndSubjectToSentMail() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("subject", "subj");
        mailetConfig.setProperty("prefix", "pre");
        redirect.init(mailetConfig);

        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        mimeMessage.setSubject("My subject");
        mimeMessage.setText("content");
        FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(MailAddressFixture.ANY_AT_JAMES)
                .mimeMessage(mimeMessage)
                .build();

        redirect.service(mail);

        SentMail newMail = fakeMailContext.getSentMails().get(0);
        assertThat(newMail.getSubject()).contains("pre subj");
    }
}
