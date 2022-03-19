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
import java.util.Collection;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ForwardTest {

    private static final String MAILET_NAME = "mailetName";

    private Forward forward;
    private FakeMailContext fakeMailContext;
    private MailAddress postmaster;

    @BeforeEach
    void setUp() throws Exception {
        DNSService dnsService = mock(DNSService.class);
        forward = new Forward(dnsService);
        postmaster = new MailAddress("postmaster@james.org");
        fakeMailContext = FakeMailContext.builder()
                .postmaster(postmaster)
                .build();

        when(dnsService.getLocalHost()).thenThrow(new UnknownHostException());
    }

    @Test
    void getMailetInfoShouldReturnValue() {
        assertThat(forward.getMailetInfo()).isEqualTo("Forward Mailet");
    }

    @Test
    void initShouldThrowWhenUnkownParameter() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("unknown", "error")
                .build();

        assertThatThrownBy(() -> forward.init(mailetConfig))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void initShouldNotThrowWhenEveryParameters() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("debug", "true")
                .setProperty("passThrough", "false")
                .setProperty("fakeDomainCheck", "false")
                .setProperty("forwardto", "other@james.org")
                .setProperty("forwardTo", "other@james.org")
                .build();

        forward.init(mailetConfig);
    }

    @Test
    void initShouldThrowWhenNoForwardToParameters() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("isStatic", "true")
                .build();

        assertThatThrownBy(() -> forward.init(mailetConfig))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void initShouldThrowWhenUnparsableForwardToAddress() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("isStatic", "true")
                .setProperty("forwardTo", "user@james@org")
                .build();

        assertThatThrownBy(() -> forward.init(mailetConfig))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void initShouldThrowWhenForwardToIsEmpty() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("isStatic", "true")
                .setProperty("forwardTo", "")
                .build();

        assertThatThrownBy(() -> forward.init(mailetConfig))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getToShouldReturnEmpty() throws Exception {
        assertThat(forward.getTo()).isEmpty();
    }

    @Test
    void getReplyToShouldReturnNull() throws Exception {
        assertThat(forward.getReplyTo()).isEmpty();
    }

    @Test
    void getReversePathShouldReturnAbsent() throws Exception {
        assertThat(forward.getReversePath()).isEmpty();
    }

    @Test
    void getSenderShouldReturnAbsent() throws Exception {
        assertThat(forward.getSender()).isEmpty();
    }

    @Test
    void getRecipientsShouldReturnRecipientsWhenForwardtoParameters() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("forwardto",
                        MailAddressFixture.ANY_AT_JAMES.toString() +
                        ", " + 
                        MailAddressFixture.OTHER_AT_JAMES.toString())
                .build();
        forward.init(mailetConfig);

        Collection<MailAddress> recipients = forward.getRecipients();
        assertThat(recipients).containsOnly(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES);
    }

    @Test
    void getRecipientsShouldReturnRecipientsWhenForwardToParameters() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("forwardTo",
                        MailAddressFixture.ANY_AT_JAMES.toString() +
                        ", " + 
                        MailAddressFixture.OTHER_AT_JAMES.toString())
                .build();
        forward.init(mailetConfig);

        Collection<MailAddress> recipients = forward.getRecipients();
        assertThat(recipients).containsOnly(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES);
    }

    @Test
    void getRecipientsShouldReturnSpecialAddressWhenForwardToIsMatchingOne() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("forwardTo", "postmaster")
                .build();
        forward.init(mailetConfig);

        assertThat(forward.getRecipients()).containsOnly(postmaster);
    }

    @Test
    void forwardShouldNotModifySubject() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("forwardTo", "other@james.org")
                .build();
        forward.init(mailetConfig);

        String expectedSubject = "My subject";
        FakeMail mail = FakeMail.builder()
                .name(MAILET_NAME)
                .sender(MailAddressFixture.ANY_AT_JAMES)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("My subject"))
                .build();

        forward.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo(expectedSubject);
    }
}
