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
import org.apache.mailet.Mail;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BounceTest {

    private static final String MAILET_NAME = "mailetName";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private Bounce bounce;
    private FakeMailContext fakeMailContext;

    @Before
    public void setUp() throws Exception {
        DNSService dnsService = mock(DNSService.class);
        bounce = new Bounce(dnsService);
        fakeMailContext = FakeMailContext.defaultContext();

        when(dnsService.getLocalHost()).thenThrow(new UnknownHostException());
    }

    @Test
    public void getMailetInfoShouldReturnValue() {
        assertThat(bounce.getMailetInfo()).isEqualTo("Bounce Mailet");
    }

    @Test
    public void initShouldThrowWhenUnkownParameter() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("unknown", "error")
                .build();
        expectedException.expect(MessagingException.class);

        bounce.init(mailetConfig);
    }

    @Test
    public void initShouldNotThrowWhenEveryParameters() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("debug", "true")
                .setProperty("passThrough", "false")
                .setProperty("fakeDomainCheck", "false")
                .setProperty("inline", "all")
                .setProperty("attachment", "none")
                .setProperty("message", "custom message")
                .setProperty("notice", "")
                .setProperty("sender", "sender@domain.org")
                .setProperty("sendingAddress", "sender@domain.org")
                .setProperty("prefix", "my prefix")
                .setProperty("attachError", "true")
                .build();

        bounce.init(mailetConfig);
    }

    @Test
    public void bounceShouldReturnAMailToTheSenderWithoutAttributes() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
        bounce.init(mailetConfig);

        MailAddress senderMailAddress = new MailAddress("sender@domain.com");
        FakeMail mail = FakeMail.builder()
            .name(MAILET_NAME)
            .sender(senderMailAddress)
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("My content"))
            .build();

        bounce.service(mail);

        FakeMailContext.SentMail expected = FakeMailContext.sentMailBuilder()
            .recipient(senderMailAddress)
            .fromMailet()
            .build();
        assertThat(fakeMailContext.getSentMails()).containsOnly(expected);
    }

    @Test
    public void bounceShouldNotSendEmailToNullSender() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
        bounce.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
            .name(MAILET_NAME)
            .sender(MailAddress.nullSender())
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("My content"))
            .build();

        bounce.service(mail);

        assertThat(fakeMailContext.getSentMails()).isEmpty();
    }

    @Test
    public void bounceShouldChangeTheStateWhenNoSenderAndPassThroughEqualsFalse() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("passThrough", "false")
                .build();
        bounce.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
            .name(MAILET_NAME)
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("My content"))
            .build();

        bounce.service(mail);

        assertThat(mail.getState()).isEqualTo(Mail.GHOST);
    }

    @Test
    public void bounceShouldNotChangeTheStateWhenNoSenderAndPassThroughEqualsTrue() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("passThrough", "true")
                .build();
        bounce.init(mailetConfig);

        String initialState = "initial";
        FakeMail mail = FakeMail.builder()
            .name(MAILET_NAME)
            .state(initialState)
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setText("My content"))
            .build();

        bounce.service(mail);

        assertThat(mail.getState()).isEqualTo(initialState);
    }

    @Test
    public void bounceShouldAddPrefixToSubjectWhenPrefixIsConfigured() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("prefix", "pre")
                .build();
        bounce.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
            .name(MAILET_NAME)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .recipient(MailAddressFixture.ANY_AT_JAMES2)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("My subject"))
            .build();

        bounce.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo("pre My subject");
    }
}
