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

import static org.apache.mailet.base.MailAddressFixture.MAIL_ADDRESS_1;
import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
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
            .recipient(MAIL_ADDRESS_1)
            .build();

        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=1m", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);

        assertThat(matcher.match(mail)).containsExactly(MAIL_ADDRESS_1);
    }

    @Test
    public void matchShouldNotMatchWhenMailUnderSize() throws MessagingException {
        Mail mail = FakeMail.builder()
            .size(200000)
            .recipient(MAIL_ADDRESS_1)
            .build();

        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=1m", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);

        assertThat(matcher.match(mail)).isNull();
    }

    @Test
    public void matchShouldNotMatchMailsWithSpecifiedSize() throws MessagingException {
        Mail mail = FakeMail.builder()
            .size(1024)
            .recipient(MAIL_ADDRESS_1)
            .build();

        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=1k", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);

        assertThat(matcher.match(mail)).isNull();
    }

    @Test
    public void matchShouldMatchMailsWithSizeSuperiorToSpecifiedSize() throws MessagingException {
        Mail mail = FakeMail.builder()
            .size(1025)
            .recipient(MAIL_ADDRESS_1)
            .build();

        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=1k", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);

        assertThat(matcher.match(mail)).containsExactly(MAIL_ADDRESS_1);
    }

    @Test
    public void matchShouldReturnNullWhenUnderLimitNoUnit() throws MessagingException {
        Mail mail = FakeMail.builder()
            .size(4)
            .recipient(MAIL_ADDRESS_1)
            .build();

        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=4", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);

        assertThat(matcher.match(mail)).isNull();
    }

    @Test
    public void matchShouldMatchOverLimitWhenNoUnit() throws MessagingException {
        Mail mail = FakeMail.builder()
            .size(5)
            .recipient(MAIL_ADDRESS_1)
            .build();

        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=4", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);

        assertThat(matcher.match(mail)).containsExactly(MAIL_ADDRESS_1);
    }

    @Test
    public void initShouldThrowOnInvalidUnits() throws Exception {
        expectedException.expect(MessagingException.class);
        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=1mb", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);
    }

    @Test(expected = MessagingException.class)
    public void initShouldThrowOnNullSize() throws Exception {
        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=0", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);
    }

    @Test(expected = MessagingException.class)
    public void initShouldThrowOnNegativeSize() throws Exception {
        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=-1", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);
    }
}
