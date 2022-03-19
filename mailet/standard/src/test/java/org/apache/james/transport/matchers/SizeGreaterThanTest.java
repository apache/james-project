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

import static org.apache.mailet.base.MailAddressFixture.ANY_AT_JAMES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SizeGreaterThanTest {

    private Matcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new SizeGreaterThan();
    }

    @Test
    void matchShouldMatchWhenMailAboveSize() throws MessagingException {
        Mail mail = FakeMail.builder()
            .name("mail")
            .size(2000000)
            .recipient(ANY_AT_JAMES)
            .build();

        FakeMatcherConfig matcherConfiguration = FakeMatcherConfig.builder()
                .matcherName("SizeGreaterThan")
                .condition("1m")
                .build();

        matcher.init(matcherConfiguration);

        assertThat(matcher.match(mail)).containsExactly(ANY_AT_JAMES);
    }

    @Test
    void matchShouldNotMatchWhenMailUnderSize() throws MessagingException {
        Mail mail = FakeMail.builder()
            .name("mail")
            .size(200000)
            .recipient(ANY_AT_JAMES)
            .build();

        FakeMatcherConfig matcherConfiguration = FakeMatcherConfig.builder()
                .matcherName("SizeGreaterThan")
                .condition("1m")
                .build();

        matcher.init(matcherConfiguration);

        assertThat(matcher.match(mail)).isNull();
    }

    @Test
    void matchShouldNotMatchMailsWithSpecifiedSize() throws MessagingException {
        Mail mail = FakeMail.builder()
            .name("mail")
            .size(1024)
            .recipient(ANY_AT_JAMES)
            .build();

        FakeMatcherConfig matcherConfiguration = FakeMatcherConfig.builder()
                .matcherName("SizeGreaterThan")
                .condition("1k")
                .build();

        matcher.init(matcherConfiguration);

        assertThat(matcher.match(mail)).isNull();
    }

    @Test
    void matchShouldMatchMailsWithSizeSuperiorToSpecifiedSize() throws MessagingException {
        Mail mail = FakeMail.builder()
            .name("mail")
            .size(1025)
            .recipient(ANY_AT_JAMES)
            .build();

        FakeMatcherConfig matcherConfiguration = FakeMatcherConfig.builder()
                .matcherName("SizeGreaterThan")
                .condition("1k")
                .build();

        matcher.init(matcherConfiguration);

        assertThat(matcher.match(mail)).containsExactly(ANY_AT_JAMES);
    }

    @Test
    void matchShouldReturnNullWhenUnderLimitNoUnit() throws MessagingException {
        Mail mail = FakeMail.builder()
            .name("mail")
            .size(4)
            .recipient(ANY_AT_JAMES)
            .build();

        FakeMatcherConfig matcherConfiguration = FakeMatcherConfig.builder()
                .matcherName("SizeGreaterThan")
                .condition("4")
                .build();

        matcher.init(matcherConfiguration);

        assertThat(matcher.match(mail)).isNull();
    }

    @Test
    void matchShouldMatchOverLimitWhenNoUnit() throws MessagingException {
        Mail mail = FakeMail.builder()
            .name("mail")
            .size(5)
            .recipient(ANY_AT_JAMES)
            .build();

        FakeMatcherConfig matcherConfiguration = FakeMatcherConfig.builder()
                .matcherName("SizeGreaterThan")
                .condition("4")
                .build();

        matcher.init(matcherConfiguration);

        assertThat(matcher.match(mail)).containsExactly(ANY_AT_JAMES);
    }

    @Test
    void initShouldThrowOnInvalidUnits() {
        FakeMatcherConfig matcherConfiguration = FakeMatcherConfig.builder()
                .matcherName("SizeGreaterThan")
                .condition("1mb")
                .build();

        assertThatThrownBy(() -> matcher.init(matcherConfiguration))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void initShouldThrowOnNullSize() {
        FakeMatcherConfig matcherConfiguration = FakeMatcherConfig.builder()
                .matcherName("SizeGreaterThan")
                .condition("0")
                .build();

        assertThatThrownBy(() -> matcher.init(matcherConfiguration))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void initShouldThrowOnNegativeSize() {
        FakeMatcherConfig matcherConfiguration = FakeMatcherConfig.builder()
                .matcherName("SizeGreaterThan")
                .condition("-1")
                .build();

        assertThatThrownBy(() -> matcher.init(matcherConfiguration))
            .isInstanceOf(MessagingException.class);
    }
}
