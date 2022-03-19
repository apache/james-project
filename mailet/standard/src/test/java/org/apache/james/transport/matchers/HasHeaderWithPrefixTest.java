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


package org.apache.james.transport.matchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import jakarta.mail.MessagingException;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.Mail;
import org.apache.mailet.Matcher;
import org.apache.mailet.PerRecipientHeaders.Header;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HasHeaderWithPrefixTest {
    private static final String PREFIX = "X-OPENPAAS-";
    private static final String HEADER_NAME_PREFIX_1 = "X-OPENPAAS-FEATURE-A";
    private static final String HEADER_NAME_NO_PREFIX = "X-OTHER-BUSINESS";

    private Matcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new HasHeaderWithPrefix();
    }

    @Test
    void matchShouldReturnAddressesWhenPrefixedHeaderName() throws MessagingException {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("HasHeader")
            .condition(PREFIX)
            .build();

        matcher.init(matcherConfig);

        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader(HEADER_NAME_PREFIX_1, "true"))
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES2)
            .build();

        assertThat(matcher.match(mail)).containsAll(mail.getRecipients());
    }

    @Test
    void matchShouldReturnAddressesWhenPrefixedHeaderNameInSpecific() throws MessagingException {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("HasHeader")
            .condition(PREFIX)
            .build();

        matcher.init(matcherConfig);

        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder())
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES2)
            .build();
        
        mail.addSpecificHeaderForRecipient(Header.builder().name(HEADER_NAME_PREFIX_1).value("true").build(), MailAddressFixture.ANY_AT_JAMES);

        assertThat(matcher.match(mail)).containsAll(Arrays.asList(MailAddressFixture.ANY_AT_JAMES));
    }

    @Test
    void matchShouldReturnAddressesWhenExactlyPrefix() throws MessagingException {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("HasHeader")
            .condition(PREFIX)
            .build();

        matcher.init(matcherConfig);

        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader(PREFIX, "true"))
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES2)
            .build();

        assertThat(matcher.match(mail)).containsAll(mail.getRecipients());
    }

    @Test
    void matchShouldReturnAddressesWhenExactlyPrefixInSpecific() throws MessagingException {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("HasHeader")
                .condition(PREFIX)
                .build();
        
        matcher.init(matcherConfig);
        
        Mail mail = FakeMail.builder()
                .name("mail")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder())
                .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES2)
                .build();
        
        mail.addSpecificHeaderForRecipient(Header.builder().name(PREFIX).value("true").build(), MailAddressFixture.ANY_AT_JAMES);
        
        assertThat(matcher.match(mail)).containsAll(Arrays.asList(MailAddressFixture.ANY_AT_JAMES));
    }

    @Test
    void matchShouldReturnEmptyWhenNoPrefixedHeader() throws MessagingException {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("HasHeader")
            .condition(PREFIX)
            .build();

        matcher.init(matcherConfig);

        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader(HEADER_NAME_NO_PREFIX, "true"))
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES2)
            .build();

        assertThat(matcher.match(mail)).isEmpty();
    }

    @Test
    void matchShouldReturnAddressesWhenAtLeastOneHeaderPrefixed() throws MessagingException {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("HasHeader")
            .condition(PREFIX)
            .build();

        matcher.init(matcherConfig);

        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addHeader(HEADER_NAME_PREFIX_1, "true")
                .addHeader(HEADER_NAME_NO_PREFIX, "true"))
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES2)
            .build();

        assertThat(matcher.match(mail)).containsAll(mail.getRecipients());
    }

    @Test
    void matchShouldReturnAddressesWhenAtLeastOneHeaderPrefixedInSpecific() throws MessagingException {
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("HasHeader")
                .condition(PREFIX)
                .build();
        
        matcher.init(matcherConfig);
        
        Mail mail = FakeMail.builder()
                .name("mail")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder())
                .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES2)
                .build();
        
        mail.addSpecificHeaderForRecipient(Header.builder().name(HEADER_NAME_PREFIX_1).value("true").build(), MailAddressFixture.ANY_AT_JAMES);
        mail.addSpecificHeaderForRecipient(Header.builder().name(HEADER_NAME_NO_PREFIX).value("true").build(), MailAddressFixture.ANY_AT_JAMES);
        mail.addSpecificHeaderForRecipient(Header.builder().name(HEADER_NAME_NO_PREFIX).value("true").build(), MailAddressFixture.OTHER_AT_JAMES2);
        
        assertThat(matcher.match(mail)).containsAll(Arrays.asList(MailAddressFixture.ANY_AT_JAMES));
    }

    @Test
    void initShouldRejectEmptyPrefix() {
        assertThatThrownBy(() ->
            matcher.init(FakeMatcherConfig.builder()
                .matcherName("HasHeader")
                .condition("")
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void initShouldRejectNoCondition() {
        assertThatThrownBy(() ->
            matcher.init(FakeMatcherConfig.builder()
                .matcherName("HasHeader")
                .build()))
            .isInstanceOf(MessagingException.class);
    }

}
