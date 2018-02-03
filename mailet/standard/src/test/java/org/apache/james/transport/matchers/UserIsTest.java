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
import static org.apache.mailet.base.MailAddressFixture.ANY_AT_JAMES2;
import static org.apache.mailet.base.MailAddressFixture.OTHER_AT_JAMES;
import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.MessagingException;

import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class UserIsTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private UserIs matcher;

    @Before
    public void setUp() throws Exception {
        matcher = new UserIs();
    }

    @Test
    public void shouldMatchCorrespondingUser() throws MessagingException {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("UserIs")
                .condition("any")
                .build());

        FakeMail fakeMail = FakeMail.builder()
            .recipient(ANY_AT_JAMES)
            .build();

        assertThat(matcher.match(fakeMail)).containsOnly(ANY_AT_JAMES);
    }

    @Test
    public void shouldMatchCorrespondingUserAccrossDomains() throws MessagingException {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("UserIs")
                .condition("any")
                .build());

        FakeMail fakeMail = FakeMail.builder()
            .recipients(ANY_AT_JAMES, ANY_AT_JAMES2)
            .build();

        assertThat(matcher.match(fakeMail)).containsOnly(ANY_AT_JAMES, ANY_AT_JAMES2);
    }

    @Test
    public void shouldNotMatchNonSpecifiedUsersButPreserveSpecifiedUsers() throws MessagingException {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("UserIs")
                .condition("any")
                .build());

        FakeMail fakeMail = FakeMail.builder()
            .recipients(ANY_AT_JAMES, OTHER_AT_JAMES)
            .build();

        assertThat(matcher.match(fakeMail)).containsOnly(ANY_AT_JAMES);
    }

    @Test
    public void shouldNotMatchNonSpecifiedUsers() throws MessagingException {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("UserIs")
                .condition("any")
                .build());

        FakeMail fakeMail = FakeMail.builder()
            .recipients(OTHER_AT_JAMES)
            .build();

        assertThat(matcher.match(fakeMail)).isEmpty();
    }

    @Test
    public void initShouldThrowOnMissingCondition() throws Exception {
        expectedException.expect(MessagingException.class);
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("UserIs")
                .build());
    }

    @Test
    public void initShouldThrowOnEmptyCondition() throws Exception {
        expectedException.expect(MessagingException.class);
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("UserIs")
                .build());
    }
}
