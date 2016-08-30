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
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class SizeGreaterThanTest {

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
    public void testSizeGreater() throws MessagingException {
        fakeMail.setMessageSize(2000000);
        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=1m", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);

        assertThat(matcher.match(fakeMail)).containsExactly(mailAddress);
    }

    @Test
    public void testSizeNotGreater() throws MessagingException {
        fakeMail.setMessageSize(200000);
        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=1m", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);

        assertThat(matcher.match(fakeMail)).isNull();
    }

    @Test
    public void matchShouldNotMatchMailsWithSpecifiedSize() throws MessagingException {
        fakeMail.setMessageSize(1024);
        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=1k", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);

        assertThat(matcher.match(fakeMail)).isNull();
    }

    @Test
    public void matchShouldMatchMailsWithSizeSuperiorToSpecifiedSize() throws MessagingException {
        fakeMail.setMessageSize(1025);
        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=1k", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);

        assertThat(matcher.match(fakeMail)).containsExactly(mailAddress);
    }

    @Test
    public void matchShouldReturnNullWhenUnderLimitNoUnit() throws MessagingException {
        fakeMail.setMessageSize(4);
        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=4", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);

        assertThat(matcher.match(fakeMail)).isNull();
    }

    @Test
    public void matchShouldMatchOverLimitWhenNoUnit() throws MessagingException {
        fakeMail.setMessageSize(5);
        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=4", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);

        assertThat(matcher.match(fakeMail)).containsExactly(mailAddress);
    }

    @Test(expected = MessagingException.class)
    public void testThrowExceptionOnInvalidAmount() throws Exception {
        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=1mb", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);
    }

    @Test(expected = MessagingException.class)
    public void amountShouldNotBeNull() throws Exception {
        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=0", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);
    }

    @Test(expected = MessagingException.class)
    public void amountShouldNotBeNegative() throws Exception {
        FakeMatcherConfig matcherConfiguration = new FakeMatcherConfig("SizeGreaterThan=-1", FakeMailContext.defaultContext());
        matcher.init(matcherConfiguration);
    }
}
