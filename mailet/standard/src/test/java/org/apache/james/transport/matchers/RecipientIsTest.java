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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.MessagingException;

import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecipientIsTest {

    private RecipientIs matcher;

    @BeforeEach
    void setUp() {
        matcher = new RecipientIs();
    }

    @Test
    void shouldMatchCorrespondingAddres() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("RecipientIs")
                .condition(ANY_AT_JAMES.toString())
                .build());

        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipient(ANY_AT_JAMES)
            .build();

       assertThat(matcher.match(fakeMail)).containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldOnlyMatchCorrespondingAddress() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("RecipientIs")
                .condition(ANY_AT_JAMES.toString())
                .build());

        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipients(ANY_AT_JAMES, OTHER_AT_JAMES)
            .build();

        assertThat(matcher.match(fakeMail)).containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldNotMatchUnrelatedAddresses() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("RecipientIs")
                .condition(ANY_AT_JAMES.toString())
                .build());

        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipients(OTHER_AT_JAMES, ANY_AT_JAMES2)
            .build();

        assertThat(matcher.match(fakeMail)).isEmpty();
    }

    @Test
    void initShouldThrowOnMissingCondition() {
        assertThatThrownBy(() ->
            matcher.init(FakeMatcherConfig.builder()
                .matcherName("RecipientIs")
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void initShouldThrowOnEmptyCondition() {
        assertThatThrownBy(() ->
            matcher.init(FakeMatcherConfig.builder()
                .matcherName("RecipientIs")
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void shouldBeAbleToMatchSeveralAddresses() throws Exception {
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("RecipientIs")
                .condition(ANY_AT_JAMES + ", " + ANY_AT_JAMES2)
                .build());

        FakeMail fakeMail = FakeMail.builder()
            .name("mail")
            .recipients(ANY_AT_JAMES, OTHER_AT_JAMES, ANY_AT_JAMES2)
            .build();

        assertThat(matcher.match(fakeMail)).containsOnly(ANY_AT_JAMES, ANY_AT_JAMES2);
    }
}
