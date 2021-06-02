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
package org.apache.james.mailetcontainer.impl.matchers;

import static org.apache.mailet.base.MailAddressFixture.ANY_AT_JAMES;
import static org.apache.mailet.base.MailAddressFixture.ANY_AT_JAMES2;
import static org.apache.mailet.base.MailAddressFixture.OTHER_AT_JAMES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class AndTest {
    private And testee;
    private Matcher matcher1;
    private Matcher matcher2;
    private Mail mail;

    @BeforeEach
    void setUp() throws Exception {
        matcher1 = mock(Matcher.class);
        matcher2 = mock(Matcher.class);

        testee = new And();

        mail = FakeMail.builder().name("name").recipients(ANY_AT_JAMES, OTHER_AT_JAMES, ANY_AT_JAMES2).build();
    }

    @Test
    void shouldNotMatchWhenNoChild() throws Exception {
        assertThat(testee.match(mail)).isEmpty();
    }

    @Test
    void shouldMatchWhenSingleUnderlyingMatcherMatch() throws Exception {
        when(matcher1.match(mail)).thenReturn(ImmutableList.of(ANY_AT_JAMES));

        testee.add(matcher1);

        assertThat(testee.match(mail)).containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldMatchWhenTwoUnderlyingMatcherMatch() throws Exception {
        when(matcher1.match(mail)).thenReturn(ImmutableList.of(ANY_AT_JAMES, OTHER_AT_JAMES));
        when(matcher2.match(mail)).thenReturn(ImmutableList.of(ANY_AT_JAMES, ANY_AT_JAMES2));

        testee.add(matcher1);
        testee.add(matcher2);

        assertThat(testee.match(mail)).containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldMatchWhenAtLeastOneUnderlyingMatcherDoNotMatch() throws Exception {
        when(matcher1.match(mail)).thenReturn(ImmutableList.of(ANY_AT_JAMES, OTHER_AT_JAMES));
        when(matcher2.match(mail)).thenReturn(ImmutableList.<MailAddress>of());

        testee.add(matcher1);
        testee.add(matcher2);

        assertThat(testee.match(mail)).isEmpty();
    }

    @Test
    void shouldSupportNull() throws Exception {
        when(matcher1.match(mail)).thenReturn(ImmutableList.of(ANY_AT_JAMES, OTHER_AT_JAMES));
        when(matcher2.match(mail)).thenReturn(null);

        testee.add(matcher1);
        testee.add(matcher2);

        assertThat(testee.match(mail)).isEmpty();
    }
}
