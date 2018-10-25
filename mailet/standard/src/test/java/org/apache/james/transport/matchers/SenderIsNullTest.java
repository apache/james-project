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
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SenderIsNullTest {

    private SenderIsNull matcher;

    @BeforeEach
    void setUp() throws Exception {
        matcher = new SenderIsNull();
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderIsNull")
                .build());
    }

    @Test
    void shouldMatchWhenNoSender() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .recipient(ANY_AT_JAMES)
            .build();

        assertThat(matcher.match(fakeMail)).containsExactly(ANY_AT_JAMES);
    }

    @Test
    void shouldMatchWhenNullSender() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .recipient(ANY_AT_JAMES)
            .sender(MailAddress.nullSender())
            .build();

        assertThat(matcher.match(fakeMail)).containsExactly(ANY_AT_JAMES);
    }

    @Test
    void shouldNotMatchWhenSenderIsPresent() throws Exception {
        FakeMail fakeMail = FakeMail.builder()
            .recipient(ANY_AT_JAMES)
            .sender("other@james.apache.org")
            .build();

        assertThat(matcher.match(fakeMail)).isNull();
    }
}
