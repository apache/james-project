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
import java.util.Collection;

import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ForwardTest {

    private static final String MAILET_NAME = "mailetName";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private Forward forward;
    private FakeMailContext fakeMailContext;
    private MailAddress postmaster;

    @Before
    public void setUp() throws Exception {
        DNSService dnsService = mock(DNSService.class);
        forward = new Forward(dnsService);
        postmaster = new MailAddress("postmaster@james.org");
        fakeMailContext = FakeMailContext.builder()
                .postmaster(postmaster)
                .build();

        when(dnsService.getLocalHost()).thenThrow(new UnknownHostException());
    }

    @Test
    public void getMailetInfoShouldReturnValue() {
        assertThat(forward.getMailetInfo()).isEqualTo("Forward Mailet");
    }

    @Test
    public void initShouldThrowWhenUnkownParameter() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("unknown", "error")
                .build();
        expectedException.expect(MessagingException.class);

        forward.init(mailetConfig);
    }

    @Test
    public void initShouldNotThrowWhenEveryParameters() throws Exception {
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
    public void initShouldThrowWhenNoForwardToParameters() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("isStatic", "true")
                .build();
        expectedException.expect(MessagingException.class);

        forward.init(mailetConfig);
    }

    @Test
    public void initShouldThrowWhenUnparsableForwardToAddress() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("isStatic", "true")
                .setProperty("forwardTo", "user@james@org")
                .build();
        expectedException.expect(MessagingException.class);

        forward.init(mailetConfig);
    }

    @Test
    public void initShouldThrowWhenForwardToIsEmpty() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("isStatic", "true")
                .setProperty("forwardTo", "")
                .build();
        expectedException.expect(MessagingException.class);

        forward.init(mailetConfig);
    }

    @Test
    public void getToShouldReturnEmpty() throws Exception {
        assertThat(forward.getTo()).isEmpty();
    }

    @Test
    public void getReplyToShouldReturnNull() throws Exception {
        assertThat(forward.getReplyTo()).isEmpty();
    }

    @Test
    public void getReversePathShouldReturnAbsent() throws Exception {
        assertThat(forward.getReversePath()).isEmpty();
    }

    @Test
    public void getSenderShouldReturnAbsent() throws Exception {
        assertThat(forward.getSender()).isEmpty();
    }

    @Test
    public void getRecipientsShouldReturnRecipientsWhenForwardtoParameters() throws Exception {
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
    public void getRecipientsShouldReturnRecipientsWhenForwardToParameters() throws Exception {
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
    public void getRecipientsShouldReturnSpecialAddressWhenForwardToIsMatchingOne() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName(MAILET_NAME)
                .mailetContext(fakeMailContext)
                .setProperty("forwardTo", "postmaster")
                .build();
        forward.init(mailetConfig);

        assertThat(forward.getRecipients()).containsOnly(postmaster);
    }

    @Test
    public void forwardShouldNotModifySubject() throws Exception {
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
