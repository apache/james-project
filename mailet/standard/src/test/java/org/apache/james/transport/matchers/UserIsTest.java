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
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class UserIsTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private UserIs matcher;
    private MailAddress recipient1;
    private MailAddress recipient2;
    private MailAddress recipient3;

    @Before
    public void setUp() throws Exception {
        matcher = new UserIs();
        recipient1 = new MailAddress("test@james.apache.org");
        recipient2 = new MailAddress("test@other.apache.org");
        recipient3 = new MailAddress("other@james.apache.org");
    }

    @Test
    public void shouldMatchCorrespondingUser() throws MessagingException {
        matcher.init(new FakeMatcherConfig("UserIs=test", FakeMailContext.defaultContext()));

        FakeMail fakeMail = FakeMail.builder()
            .recipient(recipient1)
            .build();

        assertThat(matcher.match(fakeMail)).containsExactly(recipient1);
    }

    @Test
    public void shouldMatchCorrespondingUserAccrossDomains() throws MessagingException {
        matcher.init(new FakeMatcherConfig("UserIs=test", FakeMailContext.defaultContext()));

        FakeMail fakeMail = FakeMail.builder()
            .recipients(recipient1, recipient2)
            .build();

        assertThat(matcher.match(fakeMail)).containsExactly(recipient1, recipient2);
    }

    @Test
    public void shouldNotMatchNonSpecifiedUsersButPreserveSpecifiedUsers() throws MessagingException {
        matcher.init(new FakeMatcherConfig("UserIs=test", FakeMailContext.defaultContext()));

        FakeMail fakeMail = FakeMail.builder()
            .recipients(recipient1, recipient3)
            .build();

        assertThat(matcher.match(fakeMail)).containsExactly(recipient1);
    }

    @Test
    public void shouldNotMatchNonSpecifiedUsers() throws MessagingException {
        matcher.init(new FakeMatcherConfig("UserIs=test", FakeMailContext.defaultContext()));

        FakeMail fakeMail = FakeMail.builder()
            .recipients(recipient3)
            .build();

        assertThat(matcher.match(fakeMail)).isEmpty();
    }

    @Test
    public void initShouldThrowOnMissingCondition() throws Exception {
        expectedException.expect(MessagingException.class);
        matcher.init(new FakeMatcherConfig("UserIs", FakeMailContext.defaultContext()));
    }

    @Test
    public void initShouldThrowOnEmptyCondition() throws Exception {
        expectedException.expect(MessagingException.class);
        matcher.init(new FakeMatcherConfig("UserIs=", FakeMailContext.defaultContext()));
    }
}
