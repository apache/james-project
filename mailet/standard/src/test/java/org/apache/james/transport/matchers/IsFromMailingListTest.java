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

import org.apache.mailet.Matcher;
import org.apache.mailet.base.AutomaticallySentMailDetector;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.mail.MessagingException;

import static org.apache.mailet.base.MailAddressFixture.ANY_AT_JAMES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IsFromMailingListTest {

    private Matcher matcher;
    private AutomaticallySentMailDetector automaticallySentMailDetector;

    @BeforeEach
    public void setUp() throws MessagingException {
        automaticallySentMailDetector = mock(AutomaticallySentMailDetector.class);
        matcher = new IsFromMailingList(automaticallySentMailDetector);
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("IsFromMailingList")
                .build();
        matcher.init(matcherConfig);
    }

    @Test
    void matchShouldMatchFromMailingListEmails() throws MessagingException {
        FakeMail fakeMail = FakeMail.builder().name("mail").recipient(ANY_AT_JAMES).build();

        when(automaticallySentMailDetector.isMailingList(fakeMail)).thenReturn(true);

        assertThat(matcher.match(fakeMail)).containsExactly(ANY_AT_JAMES);
    }

    @Test
    void matchShouldNotMatchIfNotFromMailingListEmails() throws MessagingException {
        FakeMail fakeMail = FakeMail.builder().name("mail").recipient(ANY_AT_JAMES).build();

        when(automaticallySentMailDetector.isMailingList(fakeMail)).thenReturn(false);

        assertThat(matcher.match(fakeMail)).isEmpty();
    }

}