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

import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.apache.mailet.base.MailAddressFixture.SENDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.MessagingException;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HasMimeTypeParameterTest {

    private static final String TEST_CONTENT_TYPE = "multipart/report; report-type=\"disposition-notification\"; boundary=\"=-ac61K8KXSRpaQ/eveStc\"";

    private HasMimeTypeParameter matcher;
    private FakeMail sampleMail;

    @BeforeEach
    void setUp() throws MessagingException {
        matcher = new HasMimeTypeParameter();
        sampleMail = FakeMail.builder()
            .name("mail")
            .mimeMessage(MimeMessageBuilder
                .mimeMessageBuilder()
                .setSubject("Mail read")
                .addHeader("Content-Type", TEST_CONTENT_TYPE)
                .setText("You email has been read by Bart", TEST_CONTENT_TYPE))
            .sender(SENDER)
            .recipient(RECIPIENT1)
            .build();
    }

    @Test
    void shouldThrowWhenConditionHasNoEquals() {
        assertThatThrownBy(() ->
            matcher.init(FakeMatcherConfig.builder()
                .matcherName("HasMimeType")
                .condition("abc")
                .build()))
            .isInstanceOf(MailetException.class);
    }

    @Test
    void shouldThrowWhenConditionHasNoValue() {
        assertThatThrownBy(() ->
            matcher.init(FakeMatcherConfig.builder()
                .matcherName("HasMimeType")
                .condition("abc=")
                .build()))
            .isInstanceOf(MailetException.class);
    }


    @Test
    void shouldThrowWhenConditionHasNoName() {
        assertThatThrownBy(() ->
            matcher.init(FakeMatcherConfig.builder()
                .matcherName("HasMimeType")
                .condition("=abc")
                .build()))
            .isInstanceOf(MailetException.class);
    }

    @Test
    void shouldSupportEqualsInValue() throws MessagingException {
        matcher.init(FakeMatcherConfig.builder()
            .matcherName("HasMimeType")
            .condition("abc=123,def==--")
            .build());
        assertThat(matcher.filteredMimeTypeParameters).contains(Pair.of("def", "=--"));
    }

    @Test
    void shouldMatchNothingWhenNoCondition() throws MessagingException {
        matcher.init(FakeMatcherConfig.builder()
            .matcherName("HasMimeType")
            .build());

        assertThat(matcher.match(sampleMail)).isEmpty();
    }

    @Test
    void shouldNotMatchWhenConditionNotFulfilled() throws MessagingException {
        matcher.init(FakeMatcherConfig.builder()
            .matcherName("HasMimeType")
            .condition("no-entry=for-that")
            .build());

        assertThat(matcher.match(sampleMail)).isEmpty();
    }

    @Test
    void shouldMatchReportType() throws MessagingException {
        matcher.init(FakeMatcherConfig.builder()
            .matcherName("HasMimeType")
            .condition("report-type=\"disposition-notification\"")
            .build());

        assertThat(matcher.match(sampleMail)).contains(RECIPIENT1);
    }

    @Test
    void shouldMatchAnyConfigurationCondition() throws MessagingException {
        matcher.init(FakeMatcherConfig.builder()
            .matcherName("HasMimeType")
            .condition("report-type=\"not found\",boundary=\"=-ac61K8KXSRpaQ/eveStc\"")
            .build());

        assertThat(matcher.match(sampleMail)).contains(RECIPIENT1);
    }

    @Test
    void shouldMatchConditionWithSameKey() throws MessagingException {
        matcher.init(FakeMatcherConfig.builder()
            .matcherName("HasMimeType")
            .condition("report-type=\"not found\",report-type=\"disposition-notification\"")
            .build());

        assertThat(matcher.match(sampleMail)).contains(RECIPIENT1);
    }

    @Test
    void shouldMatchReportTypeEvenWithoutQuotaInConfiguration() throws MessagingException {
        matcher.init(FakeMatcherConfig.builder()
            .matcherName("HasMimeType")
            .condition("report-type=disposition-notification")
            .build());

        assertThat(matcher.match(sampleMail)).contains(RECIPIENT1);
    }

}