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

import javax.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SizeGreaterThanTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private Matcher matcher;

    @Before
    public void setUp() throws Exception {
        matcher = new SizeGreaterThan();
    }

    @Test
    public void matchShouldMatchWhenMailAboveSize() throws MessagingException {
        Mail mail = FakeMail.builder()
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
    public void matchShouldNotMatchWhenMailUnderSize() throws MessagingException {
        Mail mail = FakeMail.builder()
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
    public void matchShouldNotMatchMailsWithSpecifiedSize() throws MessagingException {
        Mail mail = FakeMail.builder()
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
    public void matchShouldMatchMailsWithSizeSuperiorToSpecifiedSize() throws MessagingException {
        Mail mail = FakeMail.builder()
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
    public void matchShouldReturnNullWhenUnderLimitNoUnit() throws MessagingException {
        Mail mail = FakeMail.builder()
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
    public void matchShouldMatchOverLimitWhenNoUnit() throws MessagingException {
        Mail mail = FakeMail.builder()
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
    public void initShouldThrowOnInvalidUnits() throws Exception {
        expectedException.expect(MessagingException.class);
        FakeMatcherConfig matcherConfiguration = FakeMatcherConfig.builder()
                .matcherName("SizeGreaterThan")
                .condition("1mb")
                .build();

        matcher.init(matcherConfiguration);
    }

    @Test(expected = MessagingException.class)
    public void initShouldThrowOnNullSize() throws Exception {
        FakeMatcherConfig matcherConfiguration = FakeMatcherConfig.builder()
                .matcherName("SizeGreaterThan")
                .condition("0")
                .build();

        matcher.init(matcherConfiguration);
    }

    @Test(expected = MessagingException.class)
    public void initShouldThrowOnNegativeSize() throws Exception {
        FakeMatcherConfig matcherConfiguration = FakeMatcherConfig.builder()
                .matcherName("SizeGreaterThan")
                .condition("-1")
                .build();

        matcher.init(matcherConfiguration);
    }
}
