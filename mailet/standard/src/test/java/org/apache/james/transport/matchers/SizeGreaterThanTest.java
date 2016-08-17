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

import javax.mail.MessagingException;

import org.apache.mailet.MailAddress;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;

public class SizeGreaterThanTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private FakeMail fakeMail;
    private Matcher matcher;
    private MailAddress mailAddress;

    @Before
    public void setUp() throws Exception {
        matcher = new SizeGreaterThan();

        fakeMail = new FakeMail();
        mailAddress = new MailAddress("test@email");
        fakeMail.setRecipients(ImmutableList.of(mailAddress));
    }

    @Test
    public void matchShouldMatchWhenMailAboveSize() throws MessagingException {
        fakeMail.setMessageSize(2000000);
        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=1m", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);

        assertThat(matcher.match(fakeMail)).containsExactly(mailAddress);
    }

    @Test
    public void matchShouldNotMatchWhenMailUnderSize() throws MessagingException {
        fakeMail.setMessageSize(200000);
        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=1m", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);

        assertThat(matcher.match(fakeMail)).isNull();
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
