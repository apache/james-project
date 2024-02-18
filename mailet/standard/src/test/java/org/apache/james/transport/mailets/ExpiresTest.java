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

import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.DateFormats;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Clock;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExpiresTest {

    private final ZonedDateTime NOW = ZonedDateTime.parse("2021-12-14T16:36:47Z");
    
    private Mailet mailet;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(), NOW.getZone());
        mailet = new Expires(clock);
    }

    @Test
    void shouldThrowWhenNoConfiguration() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .build();
        assertThatThrownBy(() -> mailet.init(mailetConfig))
            .isInstanceOf(MessagingException.class)
            .hasMessage("Please configure at least one of minAge, maxAge, defaultAge");
    }

    @Test
    void shouldThrowWhenMinAgeAfterMaxAge() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("minAge", "3d")
            .setProperty("maxAge", "1h")
            .build();
        assertThatThrownBy(() -> mailet.init(mailetConfig))
            .isInstanceOf(MessagingException.class)
            .hasMessage("minAge must be before maxAge");
    }

    @Test
    void shouldThrowWhenDefaultAgeAfterMaxAge() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("defaultAge", "3d")
            .setProperty("maxAge", "1h")
            .build();
        assertThatThrownBy(() -> mailet.init(mailetConfig))
            .isInstanceOf(MessagingException.class)
            .hasMessage("defaultAge must be before maxAge");
    }

    @Test
    void shouldThrowWhenDefaultAgeBeforeMinAge() {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("defaultAge", "1h")
            .setProperty("minAge", "3d")
            .build();
        assertThatThrownBy(() -> mailet.init(mailetConfig))
            .isInstanceOf(MessagingException.class)
            .hasMessage("minAge must be before defaultAge");
    }

    @Test
    void shouldThrowOnMessagingException() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("defaultAge", "1d")
            .build();
        mailet.init(mailetConfig);

        Mail mail = mock(Mail.class);
        when(mail.getMessage()).thenThrow(new MessagingException());

        assertThatThrownBy(() -> mailet.service(mail))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void shouldSetHeaderOnMessage() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("defaultAge", "1h")
            .build();
        mailet.init(mailetConfig);

        MimeMessage mimeMessage = MailUtil.createMimeMessage();
        Mail mail = MailUtil.createMockMail2Recipients(mimeMessage);

        mailet.service(mail);
        
        assertThat(mail.getMessage().getHeader("Expires"))
            .containsExactly(asDateTime(NOW.plusHours(1)));
    }

    @Test
    void shouldKeepHeaderWhenAlreadyPresent() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("defaultAge", "1h")
            .build();
        mailet.init(mailetConfig);

        MimeMessage mimeMessage = MailUtil.createMimeMessage();
        mimeMessage.addHeader("Expires", asDateTime(NOW.plusHours(2)));
        Mail mail = MailUtil.createMockMail2Recipients(mimeMessage);

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader("Expires"))
            .containsExactly(asDateTime(NOW.plusHours(2)));
    }

    @Test
    void shouldReplaceHeaderWhenBelowMinimum() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("minAge", "3h")
            .build();
        mailet.init(mailetConfig);

        MimeMessage mimeMessage = MailUtil.createMimeMessage();
        mimeMessage.addHeader("Expires", asDateTime(NOW.plusHours(1)));
        Mail mail = MailUtil.createMockMail2Recipients(mimeMessage);

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader("Expires"))
            .containsExactly(asDateTime(NOW.plusHours(3)));
    }

    @Test
    void shouldKeepHeaderWhenAboveMinimum() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("minAge", "1h")
            .build();
        mailet.init(mailetConfig);

        MimeMessage mimeMessage = MailUtil.createMimeMessage();
        mimeMessage.addHeader("Expires", asDateTime(NOW.plusHours(2)));
        Mail mail = MailUtil.createMockMail2Recipients(mimeMessage);

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader("Expires"))
            .containsExactly(asDateTime(NOW.plusHours(2)));
    }
    
    @Test
    void shouldReplaceHeaderWhenAboveMaximum() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("maxAge", "3h")
            .build();
        mailet.init(mailetConfig);

        MimeMessage mimeMessage = MailUtil.createMimeMessage();
        mimeMessage.addHeader("Expires", asDateTime(NOW.plusHours(5)));
        Mail mail = MailUtil.createMockMail2Recipients(mimeMessage);

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader("Expires"))
            .containsExactly(asDateTime(NOW.plusHours(3)));
    }

    @Test
    void shouldKeepHeaderWhenBelowMaximum() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("maxAge", "5h")
            .build();
        mailet.init(mailetConfig);

        MimeMessage mimeMessage = MailUtil.createMimeMessage();
        mimeMessage.addHeader("Expires", asDateTime(NOW.plusHours(3)));
        Mail mail = MailUtil.createMockMail2Recipients(mimeMessage);

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader("Expires"))
            .containsExactly(asDateTime(NOW.plusHours(3)));
    }

    @Test
    void shouldKeepHeaderWhenInRange() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("minAge", "1h")
            .setProperty("maxAge", "5h")
            .build();
        mailet.init(mailetConfig);

        MimeMessage mimeMessage = MailUtil.createMimeMessage();
        mimeMessage.addHeader("Expires", asDateTime(NOW.plusHours(3)));
        Mail mail = MailUtil.createMockMail2Recipients(mimeMessage);

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader("Expires"))
            .containsExactly(asDateTime(NOW.plusHours(3)));
    }

    @Test
    void shouldIgnoreRangeWhenNoHeaderPresent() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("minAge", "1h")
            .setProperty("maxAge", "5h")
            .build();
        mailet.init(mailetConfig);

        MimeMessage mimeMessage = MailUtil.createMimeMessage();
        Mail mail = MailUtil.createMockMail2Recipients(mimeMessage);

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader("Expires")).isNullOrEmpty();
    }

    @Test
    void shouldSetHeaderForFixedRange() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("minAge", "3h")
            .setProperty("defaultAge", "3h")
            .setProperty("maxAge", "3h")
            .build();
        mailet.init(mailetConfig);

        MimeMessage mimeMessage = MailUtil.createMimeMessage();
        Mail mail = MailUtil.createMockMail2Recipients(mimeMessage);

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader("Expires"))
            .containsExactly(asDateTime(NOW.plusHours(3)));
    }

    @Test
    void shouldReplaceHeaderForFixedRange() throws MessagingException {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("Test")
            .setProperty("minAge", "3h")
            .setProperty("defaultAge", "3h")
            .setProperty("maxAge", "3h")
            .build();
        mailet.init(mailetConfig);

        MimeMessage mimeMessage = MailUtil.createMimeMessage();
        mimeMessage.addHeader("Expires", asDateTime(NOW.plusHours(5)));
        Mail mail = MailUtil.createMockMail2Recipients(mimeMessage);

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader("Expires"))
            .containsExactly(asDateTime(NOW.plusHours(3)));
    }

    private static String asDateTime(ZonedDateTime when) {
        return DateFormats.RFC822_DATE_FORMAT.format(when);
    }
}
