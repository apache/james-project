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
import static org.apache.mailet.base.MailAddressFixture.JAMES_APACHE_ORG;
import static org.apache.mailet.base.MailAddressFixture.OTHER_AT_JAMES;
import static org.apache.mailet.base.MailAddressFixture.OTHER_AT_JAMES2;
import static org.assertj.core.api.Assertions.assertThat;

import javax.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HostIsTest {

   private Matcher matcher;

    @BeforeEach
    public void setUp() throws Exception {
        matcher = new HostIs();
        FakeMatcherConfig mci = FakeMatcherConfig.builder()
                .matcherName("HostIs")
                .condition(JAMES_APACHE_ORG)
                .build();

        matcher.init(mci);
    }

    @Test
    public void shouldMatchWhenRightDomain() throws MessagingException {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipients(ANY_AT_JAMES, OTHER_AT_JAMES)
            .build();

        assertThat(matcher.match(mail)).containsExactly(ANY_AT_JAMES, OTHER_AT_JAMES);
    }

    @Test
    public void shouldMatchOnlyWhenRightDomain() throws MessagingException {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipients(ANY_AT_JAMES, ANY_AT_JAMES2)
            .build();

        assertThat(matcher.match(mail)).containsExactly(ANY_AT_JAMES);
    }

    @Test
    public void shouldNotMatchWhenWrongDomain() throws MessagingException {
        Mail mail = FakeMail.builder()
            .name("mail")
            .recipients(ANY_AT_JAMES2, OTHER_AT_JAMES2)
            .build();

        assertThat(matcher.match(mail)).isEmpty();
    }
}
