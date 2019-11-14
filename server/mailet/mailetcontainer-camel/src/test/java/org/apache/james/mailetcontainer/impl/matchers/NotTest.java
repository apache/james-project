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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.core.MailAddress;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Mail;
import org.apache.mailet.Matcher;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class NotTest {

    private Not testee;
    private Matcher matcher1;
    private Matcher matcher2;
    private Mail mail;
    private MailAddress recipient1;
    private MailAddress recipient2;
    private MailAddress recipient3;
    private MailAddress recipient4;

    @Before
    public void setUp() throws Exception {
        matcher1 = mock(Matcher.class);
        matcher2 = mock(Matcher.class);

        testee = new Not();

        recipient1 = new MailAddress("any@apahe.org");
        recipient2 = new MailAddress("other@apahe.org");
        recipient3 = new MailAddress("bis@apache.org");
        recipient4 = new MailAddress("yet@apache.org");
        mail = MailImpl.builder().name("name").addRecipients(recipient1, recipient2, recipient3, recipient4).build();
    }

    @Test
    public void shouldReturnAllAddressesWhenNoMatcherSpecified() throws Exception {
        assertThat(testee.match(mail)).containsOnly(recipient1, recipient2, recipient3, recipient4);
    }

    @Test
    public void shouldNegateWhenOneMatcher() throws Exception {
        when(matcher1.match(mail)).thenReturn(ImmutableList.of(recipient1, recipient3));

        testee.add(matcher1);

        assertThat(testee.match(mail)).containsOnly(recipient2, recipient4);
    }

    @Test
    public void shouldNegateUnionWhenTwoMatchers() throws Exception {
        when(matcher1.match(mail)).thenReturn(ImmutableList.of(recipient1, recipient3));
        when(matcher2.match(mail)).thenReturn(ImmutableList.of(recipient1, recipient2));

        testee.add(matcher1);
        testee.add(matcher2);

        assertThat(testee.match(mail)).containsOnly(recipient4);
    }

    @Test
    public void shouldAcceptEmptyResults() throws Exception {
        when(matcher1.match(mail)).thenReturn(ImmutableList.of(recipient1, recipient3));
        when(matcher2.match(mail)).thenReturn(ImmutableList.<MailAddress>of());

        testee.add(matcher1);
        testee.add(matcher2);

        assertThat(testee.match(mail)).containsOnly(recipient2, recipient4);
    }

    @Test
    public void shouldAcceptNullResults() throws Exception {
        when(matcher1.match(mail)).thenReturn(ImmutableList.of(recipient1, recipient3));
        when(matcher2.match(mail)).thenReturn(null);

        testee.add(matcher1);
        testee.add(matcher2);

        assertThat(testee.match(mail)).containsOnly(recipient2, recipient4);
    }
}
