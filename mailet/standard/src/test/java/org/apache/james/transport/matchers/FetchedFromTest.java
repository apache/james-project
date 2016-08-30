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
import org.apache.mailet.base.test.MailUtil;
import org.junit.Before;
import org.junit.Test;

public class FetchedFromTest {
    private static final String EXPECTED_HEADER_VALUE = "james-user";
    private static final String WRONG_HEADER_VALUE = "defaultHeaderValue";

    private Matcher matcher;
    private MailAddress mailAddress1;
    private MailAddress mailAddress2;

    @Before
    public void setUp() throws MessagingException {
        matcher = new FetchedFrom();
        FakeMatcherConfig matcherConfig = new FakeMatcherConfig("FetchedFrom=" + EXPECTED_HEADER_VALUE, FakeMailContext.defaultContext());
        matcher.init(matcherConfig);

        mailAddress1 = new MailAddress("me@apache.org");
        mailAddress2 = new MailAddress("you@apache.org");
    }

    @Test
    public void matchShouldReturnMatchWhenFetchFromHeaderHoldsRightValue() throws MessagingException {
        FakeMail fakeMail = FakeMail.builder()
            .recipients(mailAddress1, mailAddress2)
            .mimeMessage(MailUtil.createMimeMessage(FetchedFrom.X_FETCHED_FROM, EXPECTED_HEADER_VALUE))
            .build();

        assertThat(matcher.match(fakeMail)).containsExactly(mailAddress1, mailAddress2);
    }

    @Test
    public void matchShouldReturnNotMatchWhenFetchFromHeaderHoldsWrongValue() throws MessagingException {
        FakeMail fakeMail = FakeMail.builder()
            .recipients(mailAddress1, mailAddress2)
            .mimeMessage(MailUtil.createMimeMessage(FetchedFrom.X_FETCHED_FROM, WRONG_HEADER_VALUE))
            .build();

        assertThat(matcher.match(fakeMail)).isNull();
    }

    @Test
    public void matchShouldRemoveMatchingHeaders() throws MessagingException {
        FakeMail fakeMail = FakeMail.builder()
            .recipients(mailAddress1, mailAddress2)
            .mimeMessage(MailUtil.createMimeMessage(FetchedFrom.X_FETCHED_FROM, EXPECTED_HEADER_VALUE))
            .build();

        matcher.match(fakeMail);

        assertThat(fakeMail.getMessage().getHeader(FetchedFrom.X_FETCHED_FROM)).isNull();
    }

    @Test
    public void matchShouldNotRemoveNonMatchingHeaders() throws MessagingException {
        FakeMail fakeMail = FakeMail.builder()
            .recipients(mailAddress1, mailAddress2)
            .mimeMessage(MailUtil.createMimeMessage(FetchedFrom.X_FETCHED_FROM, WRONG_HEADER_VALUE))
            .build();

        matcher.match(fakeMail);

        assertThat(fakeMail.getMessage().getHeader(FetchedFrom.X_FETCHED_FROM)).containsExactly(WRONG_HEADER_VALUE);
    }
}
