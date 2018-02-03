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

import org.apache.james.core.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SenderIsRegexTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    public static final String SENDER_NAME = "test@james.apache.org";
    private SenderIsRegex matcher;
    private MailAddress recipient;

    @Before
    public void setUp() throws Exception {
        matcher = new SenderIsRegex();
        recipient = new MailAddress("recipient@apache.org");
    }

    @Test
    public void shouldMatchOnMatchingPattern() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderIsRegex")
                .condition(".*@.*")
                .build());

        FakeMail fakeMail = FakeMail.builder()
            .sender(SENDER_NAME)
            .recipient(recipient)
            .build();

        assertThat(matcher.match(fakeMail)).containsExactly(recipient);
    }

    @Test
    public void shouldNotMatchSubParts() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderIsRegex")
                .condition("test")
                .build());

        FakeMail fakeMail = FakeMail.builder()
            .sender(SENDER_NAME)
            .recipient(recipient)
            .build();

        assertThat(matcher.match(fakeMail)).isNull();
    }

    @Test
    public void shouldNotMatchWhenNullSender() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderIsRegex")
                .condition(".*@.*")
                .build());

        FakeMail fakeMail = FakeMail.builder()
            .recipient(recipient)
            .build();

        assertThat(matcher.match(fakeMail)).isNull();
    }

    @Test
    public void shouldNotMatchOnNonMatchingPattern() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderIsRegex")
                .condition("^\\.")
                .build());

        FakeMail fakeMail = FakeMail.builder()
            .sender(SENDER_NAME)
            .recipient(recipient)
            .build();

        assertThat(matcher.match(fakeMail)).isNull();
    }

    @Test
    public void initShouldThrowWhenEmptyCondition() throws MessagingException {
        expectedException.expect(MessagingException.class);
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderIsRegex")
                .build());
    }

    @Test
    public void initShouldThrowWhenNoConditions() throws MessagingException {
        expectedException.expect(MessagingException.class);
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderIsRegex")
                .build());
    }

    @Test
    public void initShouldThrowWhenInvalidPattern() throws MessagingException {
        expectedException.expect(MessagingException.class);
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderIsRegex")
                .condition("(.")
                .build());
    }
}
