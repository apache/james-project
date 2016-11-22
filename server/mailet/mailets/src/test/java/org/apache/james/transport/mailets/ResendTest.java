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

public class ResendTest {

    private static final String MAILET_NAME = "mailetName";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private Resend resend;
    private FakeMailContext fakeMailContext;
    private MailAddress postmaster;

    @Before
    public void setUp() throws Exception {
        DNSService dnsService = mock(DNSService.class);
        resend = new Resend(dnsService);
        postmaster = new MailAddress("postmaster@james.org");
        fakeMailContext = FakeMailContext.builder()
                .postmaster(postmaster)
                .build();

        when(dnsService.getLocalHost()).thenThrow(new UnknownHostException());
    }

    @Test
    public void getMailetInfoShouldReturnValue() {
        assertThat(resend.getMailetInfo()).isEqualTo("Redirect Mailet");
    }

    @Test
    public void initShouldThrowWhenUnknownParameter() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("unknown", "error");
        expectedException.expect(MessagingException.class);

        resend.init(mailetConfig);
    }

    @Test
    public void initShouldNotThrowWhenEveryParameters() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
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

        resend.init(mailetConfig);
    }

    @Test
    public void resendShouldNotModifyOriginalSubject() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("subject", "subj");
        mailetConfig.setProperty("prefix", "pref");
        mailetConfig.setProperty("recipients", "user@james.org, user2@james.org");
        mailetConfig.setProperty("to", "to@james.org");
        resend.init(mailetConfig);

        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        mimeMessage.setSubject("My subject");
        mimeMessage.setText("content");
        FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(MailAddressFixture.ANY_AT_JAMES)
                .mimeMessage(mimeMessage)
                .build();

        resend.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo("My subject");
    }

    @Test
    public void resendShouldAddPrefixAndSubjectToSentMail() throws Exception {
        FakeMailetConfig mailetConfig = new FakeMailetConfig(MAILET_NAME, fakeMailContext);
        mailetConfig.setProperty("subject", "subj");
        mailetConfig.setProperty("prefix", "pre");
        mailetConfig.setProperty("recipients", "user@james.org, user2@james.org");
        mailetConfig.setProperty("to", "to@james.org");
        resend.init(mailetConfig);

        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        mimeMessage.setSubject("My subject");
        mimeMessage.setText("content");
        FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(MailAddressFixture.ANY_AT_JAMES)
                .mimeMessage(mimeMessage)
                .build();

        resend.service(mail);

        SentMail newMail = fakeMailContext.getSentMails().get(0);
        assertThat(newMail.getSubject()).contains("pre subj");
    }
}
