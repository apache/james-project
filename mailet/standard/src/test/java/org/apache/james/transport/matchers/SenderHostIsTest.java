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

import static org.apache.mailet.base.MailAddressFixture.ANY_AT_JAMES2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Collection;

import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.Before;
import org.junit.Test;

public class SenderHostIsTest {

    private SenderHostIs matcher;
    private MailetContext mailContext;

    @Before
    public void setUp() throws MessagingException {
        mailContext = mock(MailetContext.class);
        matcher = new SenderHostIs();

    }

    @Test
    public void shouldMatchWhenSenderHostIsKnown() throws MessagingException {
        //Given
        FakeMatcherConfig mci = FakeMatcherConfig.builder()
                .matcherName("SenderHostIs")
                .mailetContext(mailContext)
                .condition("james.apache.org, james3.apache.org, james2.apache.org, james4.apache.org, james5.apache.org")
                .build();

        matcher.init(mci);

        Mail mail = FakeMail.builder()
                .sender(ANY_AT_JAMES2)
                .recipient(ANY_AT_JAMES2)
                .build();
        //When
        Collection<MailAddress> actual = matcher.match(mail);
        //Then
        assertThat(actual).containsExactly(ANY_AT_JAMES2);
    }

    @Test
    public void shouldNotMatchWhenSenderHostIsUnknown() throws MessagingException {
        //Given
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderHostIs")
                .mailetContext(mailContext)
                .condition("james.apache.org, james3.apache.org, james4.apache.org")
                .build());

        Mail mail = FakeMail.builder()
                .sender(ANY_AT_JAMES2)
                .recipient(ANY_AT_JAMES2)
                .build();
        //When
        Collection<MailAddress> actual = matcher.match(mail);
        //Then
        assertThat(actual).isNull();

    }

    @Test
    public void shouldNotMatchWhenEmptyList() throws MessagingException {
        //Given
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderHostIs")
                .mailetContext(mailContext)
                .condition("")
                .build());

        Mail mail = FakeMail.builder()
                .sender(ANY_AT_JAMES2)
                .recipient(ANY_AT_JAMES2)
                .build();
        //When
        Collection<MailAddress> actual = matcher.match(mail);
        //Then
        assertThat(actual).isNull();
    }

    @Test
    public void shouldNotMatchWhenNullSender() throws MessagingException {
        //Given
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderHostIs")
                .mailetContext(mailContext)
                .condition("")
                .build());

        Mail mail = FakeMail.builder()
                .recipient(ANY_AT_JAMES2)
                .build();
        //When
        Collection<MailAddress> actual = matcher.match(mail);
        //Then
        assertThat(actual).isNull();
    }

    @Test(expected = NullPointerException.class)
    public void shouldThrowWhenNullCondition() throws Exception {
        //When
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderHostIs")
                .mailetContext(mailContext)
                .condition(null)
                .build());
    }

    @Test
    public void parseDomainsListShouldParseWhenOnlyOneDomain() {
        //When
        Collection<String> senderHosts = matcher.parseDomainsList("james.apache.org");
        //Then
        assertThat(senderHosts).containsOnly("james.apache.org");
    }

    @Test
    public void parseDomainsListShouldParseWhenCommaSpacePattern() {
        //When
        Collection<String> senderHosts = matcher.parseDomainsList("james.apache.org, james2.apache.org, james3.apache.org, james4.apache.org, james5.apache.org");
        //Then
        assertThat(senderHosts).containsOnly("james.apache.org", "james2.apache.org", "james3.apache.org", "james4.apache.org", "james5.apache.org");
    }

    @Test
    public void parseDomainsListShouldParseWhenCommaPattern() {
        //When
        Collection<String> senderHosts = matcher.parseDomainsList("james.apache.org,james2.apache.org,james3.apache.org,james4.apache.org,james5.apache.org");
        //Then
        assertThat(senderHosts).containsOnly("james.apache.org", "james2.apache.org", "james3.apache.org", "james4.apache.org", "james5.apache.org");
    }

    @Test
    public void parseDomainsListShouldParseWhenSpacePattern() {
        //When
        Collection<String> senderHosts = matcher.parseDomainsList("james.apache.org james2.apache.org james3.apache.org james4.apache.org james5.apache.org");
        //Then
        assertThat(senderHosts).containsOnly("james.apache.org", "james2.apache.org", "james3.apache.org", "james4.apache.org", "james5.apache.org");
    }

    @Test
    public void parseDomainsListShouldParseWhenMixedPatterns() {
        //When
        Collection<String> senderHosts = matcher.parseDomainsList("james.apache.org james2.apache.org,james3.apache.org, james4.apache.org james5.apache.org");
        //Then
        assertThat(senderHosts).containsOnly("james.apache.org", "james2.apache.org", "james3.apache.org", "james4.apache.org", "james5.apache.org");
    }

    @Test
    public void parseDomainsListShouldIgnoreEmptyDomains() {
        //When
        Collection<String> senderHosts = matcher.parseDomainsList("james.apache.org   james2.apache.org james3.apache.org , james4.apache.org,,,james5.apache.org");
        //Then
        assertThat(senderHosts).containsOnly("james.apache.org", "james2.apache.org", "james3.apache.org", "james4.apache.org", "james5.apache.org");
    }
}
