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

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MimeMessageBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RemoveMimeHeaderTest {

    private static final String HEADER1 = "header1";
    private static final String HEADER2 = "header2";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private GenericMailet mailet;

    @Before
    public void setup() {
        mailet = new RemoveMimeHeader();
    }

    @Test
    public void getMailetInfoShouldReturnValue() {
        assertThat(mailet.getMailetInfo()).isEqualTo("RemoveMimeHeader Mailet");
    }

    @Test
    public void serviceShouldRemoveHeaderWhenOneMatching() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("name", HEADER1)
                .build();
        mailet.init(mailetConfig);

        Mail mail = createMail(MimeMessageBuilder.mimeMessageBuilder()
            .addHeader(HEADER1, "true")
            .addHeader(HEADER2, "true")
            .build());

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(HEADER1)).isNull();
        assertThat(mail.getMessage().getHeader(HEADER2)).isNotNull();
    }

    @Test
    public void serviceShouldRemoveHeadersWhenTwoMatching() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("name", HEADER1 + "," + HEADER2)
                .build();
        mailet.init(mailetConfig);

        Mail mail = createMail(MimeMessageBuilder.mimeMessageBuilder()
            .addHeader(HEADER1, "true")
            .addHeader(HEADER2, "true")
            .build());

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(HEADER1)).isNull();
        assertThat(mail.getMessage().getHeader(HEADER2)).isNull();
    }

    @Test
    public void serviceShouldNotRemoveHeaderWhenNoneMatching() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("name", "other1")
                .setProperty("name", "other2")
                .build();
        mailet.init(mailetConfig);

        Mail mail = createMail(MimeMessageBuilder.mimeMessageBuilder()
            .addHeader(HEADER1, "true")
            .addHeader(HEADER2, "true")
            .build());

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(HEADER1)).isNotNull();
        assertThat(mail.getMessage().getHeader(HEADER2)).isNotNull();
    }

    @Test
    public void serviceShouldNotRemoveHeaderWhenEmptyConfig() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("name", "")
                .build();
        mailet.init(mailetConfig);

        Mail mail = createMail(MimeMessageBuilder.mimeMessageBuilder()
            .addHeader(HEADER1, "true")
            .addHeader(HEADER2, "true")
            .build());

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(HEADER1)).isNotNull();
        assertThat(mail.getMessage().getHeader(HEADER2)).isNotNull();
    }

    @Test
    public void initShouldThrowWhenInvalidConfig() throws MessagingException {
        expectedException.expect(MessagingException.class);
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .build();
        mailet.init(mailetConfig);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void serviceShouldThrowWhenExceptionOccured() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("Test")
                .setProperty("name", "")
                .build();
        mailet.init(mailetConfig);

        Mail mail = mock(Mail.class);
        when(mail.getMessage())
            .thenThrow(MessagingException.class);

        assertThatThrownBy(() -> mailet.service(mail))
            .isInstanceOf(MessagingException.class);
    }

    private Mail createMail(MimeMessage message) throws MessagingException {
        return FakeMail.builder()
                .mimeMessage(message)
                .build();
    }
}
