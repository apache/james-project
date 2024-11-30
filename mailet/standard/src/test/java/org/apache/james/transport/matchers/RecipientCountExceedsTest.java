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

class RecipientCountExceedsTest {
    private RecipientCountExceeds testee;
    private MailAddress mailAddress1;
    private MailAddress mailAddress2;
    private MailAddress mailAddress3;

    @BeforeEach
    public void setUp() throws Exception {
        testee = new RecipientCountExceeds();
        testee.init(FakeMatcherConfig.builder()
            .matcherName("RecipientCountExceeds")
            .condition("2")
            .build());
        mailAddress1 = new MailAddress("mail1@domain.com");
        mailAddress2 = new MailAddress("mail2@domain.com");
        mailAddress3 = new MailAddress("mail3@domain.com");
    }

    @Test
    void shouldNotMatchWhenExactlyThreshold() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .recipients(mailAddress1, mailAddress2)
            .build();

        assertThat(testee.match(mail)).isEmpty();
    }

    @Test
    void shouldMatchWhenThresholdExceeded() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("mail")
            .recipients(mailAddress1, mailAddress2, mailAddress3)
            .build();

        assertThat(testee.match(mail)).containsOnly(mailAddress1, mailAddress2, mailAddress3);
    }

    @Test
    void initShouldThrowWhenZero() throws Exception {
        assertThatThrownBy(() -> new RecipientCountExceeds().init(FakeMatcherConfig.builder()
            .condition("0")
            .matcherName("RecipientCountExceeds")
            .build()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initShouldThrowWhenInvalid() throws Exception {
        assertThatThrownBy(() -> new RecipientCountExceeds().init(FakeMatcherConfig.builder()
            .condition("abc")
            .matcherName("RecipientCountExceeds")
            .build()))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void initShouldThrowWhenNegativeNumber() throws Exception {
        assertThatThrownBy(() -> new RecipientCountExceeds().init(FakeMatcherConfig.builder()
            .condition("-1")
            .matcherName("RecipientCountExceeds")
            .build()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}