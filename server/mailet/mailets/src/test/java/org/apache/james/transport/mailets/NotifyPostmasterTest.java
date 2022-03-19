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
import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotifyPostmasterTest {

    private static final String MAILET_NAME = "mailetName";

    private NotifyPostmaster notifyPostmaster;
    private MailAddress postmaster;
    private FakeMailContext fakeMailContext;

    @BeforeEach
    void setUp() throws Exception {
        DNSService dnsService = mock(DNSService.class);
        notifyPostmaster = new NotifyPostmaster(dnsService);
        postmaster = new MailAddress("postmaster@james.org");
        fakeMailContext = FakeMailContext.builder()
                .postmaster(postmaster)
                .build();

        when(dnsService.getLocalHost()).thenThrow(new UnknownHostException());
    }

    @Test
    void getMailetInfoShouldReturnValue() {
        assertThat(notifyPostmaster.getMailetInfo()).isEqualTo("NotifyPostmaster Mailet");
    }

    @Test
    void getAllowedInitParametersShouldReturnTheParameters() {
        assertThat(notifyPostmaster.getAllowedInitParameters()).containsOnly("debug", "passThrough", "fakeDomainCheck", "inline", "attachment", "message", "notice", "sender", "sendingAddress", "prefix", "attachError", "to");
    }

    @Test
    void initShouldFailWhenUnknownParameterIsConfigured() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("unknwon", "value")
                .build();

        assertThatThrownBy(() -> notifyPostmaster.init(mailetConfig))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getRecipientsShouldReturnPostmaster() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
        notifyPostmaster.init(mailetConfig);

        assertThat(notifyPostmaster.getRecipients()).containsOnly(postmaster);
    }

    @Test
    void getToShouldReturnPostmasterWhenToIsNotconfigured() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .build();
        notifyPostmaster.init(mailetConfig);

        assertThat(notifyPostmaster.getTo()).containsOnly(postmaster.toInternetAddress().get());
    }

    @Test
    void getToShouldReturnPostmasterWhenToIsEqualToPostmaster() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("to", "postmaster")
                .build();
        notifyPostmaster.init(mailetConfig);

        assertThat(notifyPostmaster.getTo()).containsOnly(postmaster.toInternetAddress().get());
    }

    @Test
    void getToShouldReturnUnalteredWhenToIsEqualToUnaltered() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("to", "unaltered")
                .build();
        notifyPostmaster.init(mailetConfig);

        assertThat(notifyPostmaster.getTo()).containsOnly(SpecialAddress.UNALTERED.toInternetAddress().get());
    }

    @Test
    void getToShouldReturnPostmasterWhenToIsNotEqualToPostmasterOrUnaltered() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("to", "otherTo")
                .build();
        notifyPostmaster.init(mailetConfig);

        assertThat(notifyPostmaster.getTo()).containsOnly(postmaster.toInternetAddress().get());
    }

    @Test
    void notifyPostmasterShouldNotAddPrefixToSubjectOfInFlightMailWhenPrefixIsConfigured() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName(MAILET_NAME)
            .mailetContext(fakeMailContext)
            .setProperty("prefix", "pre")
            .build();
        notifyPostmaster.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
            .name(MAILET_NAME)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .recipient(MailAddressFixture.ANY_AT_JAMES2)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("My subject"))
            .build();

        notifyPostmaster.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo("My subject");
    }

    @Test
    void notifyPostmasterShouldAddPrefixToSubjectOfSentEmailWhenPrefixIsConfigured() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName(MAILET_NAME)
            .mailetContext(fakeMailContext)
            .setProperty("prefix", "pre")
            .build();
        notifyPostmaster.init(mailetConfig);

        FakeMail mail = FakeMail.builder()
            .name(MAILET_NAME)
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .recipient(MailAddressFixture.ANY_AT_JAMES2)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("My subject"))
            .build();

        notifyPostmaster.service(mail);

        assertThat(fakeMailContext.getSentMails())
            .first()
            .extracting(s -> s.getSubject().get())
            .isEqualTo("pre My subject");
    }
}
