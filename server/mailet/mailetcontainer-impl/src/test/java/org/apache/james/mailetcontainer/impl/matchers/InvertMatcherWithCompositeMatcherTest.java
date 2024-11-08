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

import java.util.List;

import org.apache.james.core.MailAddress;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Mail;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.MatcherInverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InvertMatcherWithCompositeMatcherTest {
    private And andTestee;
    private Not notTestee;
    private Or orTestee;
    private Matcher matcher1;
    private Matcher matcher2;
    private Mail mail;
    private MailAddress recipient1;
    private MailAddress recipient2;

    @BeforeEach
    void setUp() throws Exception {
        matcher1 = mock(Matcher.class);
        matcher2 = mock(Matcher.class);

        andTestee = new And();
        notTestee = new Not();
        orTestee = new Or();

        recipient1 = new MailAddress("1@apahe.org");
        recipient2 = new MailAddress("2@apache.org");
        mail = MailImpl.builder().name("name")
            .addRecipients(recipient1, recipient2)
            .build();
    }

    @Test
    void invertMatchOfAnAndCompositeMatcher() throws Exception {
        //  <matcher name="invert-of-an-and-matcher" notmatch="org.apache.james.mailetcontainer.impl.matchers.And">
        //     <matcher match="matcher1"/>
        //     <matcher match="matcher2"/>
        //  </matcher>

        when(matcher1.match(mail)).thenReturn(List.of(recipient1, recipient2));
        when(matcher2.match(mail)).thenReturn(List.of(recipient1, recipient2));
        andTestee.add(matcher1);
        andTestee.add(matcher2);
        MatcherInverter inverterMatcher = new MatcherInverter(andTestee);

        assertThat(inverterMatcher.match(mail)).isNull();
    }

    @Test
    void invertMatchOfAnAndCompositeMatcherTwoNonMatchCase() throws Exception {
        //  <matcher name="invert-of-an-and-matcher" notmatch="org.apache.james.mailetcontainer.impl.matchers.And">
        //     <matcher match="matcher1"/>
        //     <matcher match="matcher2"/>
        //  </matcher>

        when(matcher1.match(mail)).thenReturn(List.of());
        when(matcher2.match(mail)).thenReturn(List.of());
        andTestee.add(matcher1);
        andTestee.add(matcher2);
        MatcherInverter inverterMatcher = new MatcherInverter(andTestee);

        assertThat(inverterMatcher.match(mail)).containsExactlyInAnyOrder(recipient1, recipient2);
    }

    @Test
    void invertMatchOfANotCompositeMatcher() throws Exception {
        //  <matcher name="invert-of-a-not-matcher" notmatch="org.apache.james.mailetcontainer.impl.matchers.Not">
        //     <matcher match="matcher1"/>
        //     <matcher match="matcher2"/>
        //  </matcher>

        when(matcher1.match(mail)).thenReturn(List.of(recipient1));
        when(matcher2.match(mail)).thenReturn(List.of(recipient2));
        notTestee.add(matcher1);
        notTestee.add(matcher2);
        MatcherInverter inverterMatcher = new MatcherInverter(notTestee);

        assertThat(inverterMatcher.match(mail)).containsExactlyInAnyOrder(recipient1, recipient2);
    }

    @Test
    void invertMatchOfANotCompositeMatcherTwoNonMatchCase() throws Exception {
        //  <matcher name="invert-of-a-not-matcher" notmatch="org.apache.james.mailetcontainer.impl.matchers.Not">
        //     <matcher match="matcher1"/>
        //     <matcher match="matcher2"/>
        //  </matcher>

        when(matcher1.match(mail)).thenReturn(List.of());
        when(matcher2.match(mail)).thenReturn(List.of());
        notTestee.add(matcher1);
        notTestee.add(matcher2);
        MatcherInverter inverterMatcher = new MatcherInverter(notTestee);

        assertThat(inverterMatcher.match(mail)).isNull();
    }

    @Test
    void invertMatchOfAnOrCompositeMatcher() throws Exception {
        //  <matcher name="invert-of-a-or-matcher" notmatch="org.apache.james.mailetcontainer.impl.matchers.Or">
        //     <matcher match="matcher1"/>
        //     <matcher match="matcher2"/>
        //  </matcher>

        when(matcher1.match(mail)).thenReturn(List.of(recipient1));
        when(matcher2.match(mail)).thenReturn(List.of(recipient2));
        orTestee.add(matcher1);
        orTestee.add(matcher2);
        MatcherInverter inverterMatcher = new MatcherInverter(orTestee);

        assertThat(inverterMatcher.match(mail)).isNull();
    }

    @Test
    void invertMatchOfAnOrCompositeMatcherTwoNonMatchCase() throws Exception {
        //  <matcher name="invert-of-a-or-matcher" notmatch="org.apache.james.mailetcontainer.impl.matchers.Or">
        //     <matcher match="matcher1"/>
        //     <matcher match="matcher2"/>
        //  </matcher>

        when(matcher1.match(mail)).thenReturn(List.of());
        when(matcher2.match(mail)).thenReturn(List.of());
        orTestee.add(matcher1);
        orTestee.add(matcher2);
        MatcherInverter inverterMatcher = new MatcherInverter(orTestee);

        assertThat(inverterMatcher.match(mail)).containsExactlyInAnyOrder(recipient1, recipient2);
    }

}
