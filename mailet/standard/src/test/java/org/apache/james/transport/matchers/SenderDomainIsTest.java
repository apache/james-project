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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SenderDomainIsTest {

    private static final String SENDER_NAME = "test@james.apache.org";

    private SenderDomainIs matcher;
    private MailAddress recipient;

    @BeforeEach
    void setUp() throws Exception {
        matcher = new SenderDomainIs();
        recipient = new MailAddress("recipient@james.apache.org");
    }

    @Test
    void shouldMatchOnMatchingSenderDomain() throws Exception {
        matcher.init(
            FakeMatcherConfig.builder()
                .matcherName("SenderDomainIs")
                .condition(
                        "james.apache.org, james3.apache.org, james2.apache.org, james4.apache.org, james5.apache.org")
                .build());

        FakeMail fakeMail = FakeMail.builder()
                .sender(SENDER_NAME)
                .recipient(recipient)
                .build();

        assertThat(matcher.match(fakeMail)).containsExactly(recipient);
    }

    @Test
    void shouldNotMatchWhenWrongSenderDomain() throws Exception {
        matcher.init(
            FakeMatcherConfig.builder()
                .matcherName("SenderDomainIs")
                .condition(
                        "james.apache.org, james3.apache.org, james2.apache.org, james4.apache.org, james5.apache.org")
                .build());

        FakeMail fakeMail = FakeMail.builder()
                .recipient(recipient)
                .sender("other@james7.apache.org")
                .build();

        assertThat(matcher.match(fakeMail)).isEmpty();
    }

    @Test
    void shouldNotMatchWhenNoSenderDomain() throws Exception {
        matcher.init(
            FakeMatcherConfig.builder()
                .matcherName("SenderDomainIs")
                .condition(
                        "james.apache.org, james3.apache.org, james2.apache.org, james4.apache.org, james5.apache.org")
                .build());

        FakeMail fakeMail = FakeMail.builder()
                .recipient(recipient)
                .build();

        assertThat(matcher.match(fakeMail)).isEmpty();
    }

    @Test
    void shouldNotMatchWhenNullSenderDomain() throws Exception {
        matcher.init(
            FakeMatcherConfig.builder()
                .matcherName("SenderDomainIs")
                .condition(
                        "james.apache.org, james3.apache.org james2.apache.org,,,,james4.apache.org, james5.apache.org")
                .build());

        FakeMail fakeMail = FakeMail.builder()
                .sender(MailAddress.nullSender())
                .recipient(recipient)
                .build();

        assertThat(matcher.match(fakeMail)).isEmpty();
    }

    @Test
    void initShouldThrowWhenEmptyCondition() {
        assertThatThrownBy(() -> 
               matcher.init(FakeMatcherConfig.builder()
                       .matcherName("SenderDomainIs").
                       build()))
                .isInstanceOf(NullPointerException.class);
    }
    
}
