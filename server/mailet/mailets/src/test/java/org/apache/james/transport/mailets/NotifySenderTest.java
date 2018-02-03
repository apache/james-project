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
import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class NotifySenderTest {

    private static final String MAILET_NAME = "mailetName";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private NotifySender notifySender;
    private FakeMailContext fakeMailContext;

    @Before
    public void setUp() throws Exception {
        DNSService dnsService = mock(DNSService.class);
        notifySender = new NotifySender(dnsService);
        fakeMailContext = FakeMailContext.builder()
                .postmaster(new MailAddress("postmaster@james.org"))
                .build();

        when(dnsService.getLocalHost()).thenThrow(new UnknownHostException());
    }

    @Test
    public void getMailetInfoShouldReturnValue() {
        assertThat(notifySender.getMailetInfo()).isEqualTo("NotifySender Mailet");
    }

    @Test
    public void getAllowedInitParametersShouldReturnTheParameters() {
        assertThat(notifySender.getAllowedInitParameters()).containsOnly("debug", "passThrough", "fakeDomainCheck", "inline", "attachment", "message", "notice", "sender", "sendingAddress", "prefix", "attachError", "to");
    }

    @Test
    public void initShouldFailWhenUnknownParameterIsConfigured() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("unknwon", "value")
                .build();
        expectedException.expect(MessagingException.class);

        notifySender.init(mailetConfig);
    }

    @Test
    public void getRecipientsShouldReturnSender() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
        notifySender.init(mailetConfig);

        assertThat(notifySender.getRecipients()).containsOnly(SpecialAddress.SENDER);
    }

    @Test
    public void getToShouldReturnSenderWhenToIsNotconfigured() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
        notifySender.init(mailetConfig);

        assertThat(notifySender.getTo()).containsOnly(SpecialAddress.SENDER.toInternetAddress());
    }

    @Test
    public void getToShouldReturnSenderWhenToIsEqualToSender() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("to", "sender")
                .build();
        notifySender.init(mailetConfig);

        assertThat(notifySender.getTo()).containsOnly(SpecialAddress.SENDER.toInternetAddress());
    }

    @Test
    public void getToShouldReturnUnalteredWhenToIsEqualToUnaltered() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("to", "unaltered")
                .build();
        notifySender.init(mailetConfig);

        assertThat(notifySender.getTo()).containsOnly(SpecialAddress.UNALTERED.toInternetAddress());
    }

    @Test
    public void getToShouldReturnFromWhenToIsEqualToFrom() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("to", "from")
                .build();
        notifySender.init(mailetConfig);

        assertThat(notifySender.getTo()).containsOnly(SpecialAddress.FROM.toInternetAddress());
    }

    @Test
    public void getToShouldReturnSenderWhenToIsNotEqualToSenderUnalteredOrFrom() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("to", "otherTo")
                .build();
        notifySender.init(mailetConfig);

        assertThat(notifySender.getTo()).containsOnly(SpecialAddress.SENDER.toInternetAddress());
    }

    @Test
    public void notifySenderShouldAddPrefixToSubjectWhenPrefixIsConfigured() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("prefix", "pre")
                .build();
        notifySender.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(MailAddressFixture.ANY_AT_JAMES)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("My subject"))
                .build();

        notifySender.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo("pre My subject");
    }
}