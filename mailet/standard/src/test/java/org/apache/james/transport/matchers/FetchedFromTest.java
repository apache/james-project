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
import static org.apache.mailet.base.MailAddressFixture.OTHER_AT_JAMES;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.MessagingException;

import org.apache.mailet.Matcher;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.apache.mailet.base.test.MailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FetchedFromTest {
    private static final String EXPECTED_HEADER_VALUE = "james-user";
    private static final String WRONG_HEADER_VALUE = "defaultHeaderValue";

    private Matcher matcher;

    @BeforeEach
    public void setUp() throws MessagingException {
        matcher = new FetchedFrom();
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("FetchedFrom")
                .condition(EXPECTED_HEADER_VALUE)
                .build();

        matcher.init(matcherConfig);
    }

    @Test
    public void matchShouldReturnMatchWhenFetchFromHeaderHoldsRightValue() throws MessagingException {
        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipients(ANY_AT_JAMES, OTHER_AT_JAMES)
            .mimeMessage(MailUtil.createMimeMessage(FetchedFrom.X_FETCHED_FROM, EXPECTED_HEADER_VALUE))
            .build();

        assertThat(matcher.match(fakeMail)).containsExactly(ANY_AT_JAMES, OTHER_AT_JAMES);
    }

    @Test
    public void matchShouldReturnNotMatchWhenFetchFromHeaderHoldsWrongValue() throws MessagingException {
        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipients(ANY_AT_JAMES, OTHER_AT_JAMES)
            .mimeMessage(MailUtil.createMimeMessage(FetchedFrom.X_FETCHED_FROM, WRONG_HEADER_VALUE))
            .build();

        assertThat(matcher.match(fakeMail)).isNull();
    }

    @Test
    public void matchShouldRemoveMatchingHeaders() throws MessagingException {
        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipients(ANY_AT_JAMES, OTHER_AT_JAMES)
            .mimeMessage(MailUtil.createMimeMessage(FetchedFrom.X_FETCHED_FROM, EXPECTED_HEADER_VALUE))
            .build();

        matcher.match(fakeMail);

        assertThat(fakeMail.getMessage().getHeader(FetchedFrom.X_FETCHED_FROM)).isNull();
    }

    @Test
    public void matchShouldNotRemoveNonMatchingHeaders() throws MessagingException {
        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipients(ANY_AT_JAMES, OTHER_AT_JAMES)
            .mimeMessage(MailUtil.createMimeMessage(FetchedFrom.X_FETCHED_FROM, WRONG_HEADER_VALUE))
            .build();

        matcher.match(fakeMail);

        assertThat(fakeMail.getMessage().getHeader(FetchedFrom.X_FETCHED_FROM)).containsExactly(WRONG_HEADER_VALUE);
    }
}
