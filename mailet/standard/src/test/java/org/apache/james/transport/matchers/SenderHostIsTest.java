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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Collection;

import jakarta.mail.MessagingException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SenderHostIsTest {

    private SenderHostIs matcher;
    private MailetContext mailContext;

    @BeforeEach
    void setUp() {
        mailContext = mock(MailetContext.class);
        matcher = new SenderHostIs();

    }

    @Test
    void shouldMatchWhenSenderHostIsKnown() throws MessagingException {
        //Given
        FakeMatcherConfig mci = FakeMatcherConfig.builder()
                .matcherName("SenderHostIs")
                .mailetContext(mailContext)
                .condition("james.apache.org, james3.apache.org, james2.apache.org, james4.apache.org, james5.apache.org")
                .build();

        matcher.init(mci);

        Mail mail = FakeMail.builder()
                .name("mail")
                .sender(ANY_AT_JAMES2)
                .recipient(ANY_AT_JAMES2)
                .build();
        //When
        Collection<MailAddress> actual = matcher.match(mail);
        //Then
        assertThat(actual).containsExactly(ANY_AT_JAMES2);
    }

    @Test
    void shouldNotMatchWhenSenderHostIsUnknown() throws MessagingException {
        //Given
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderHostIs")
                .mailetContext(mailContext)
                .condition("james.apache.org, james3.apache.org, james4.apache.org")
                .build());

        Mail mail = FakeMail.builder()
                .name("mail")
                .sender(ANY_AT_JAMES2)
                .recipient(ANY_AT_JAMES2)
                .build();
        //When
        Collection<MailAddress> actual = matcher.match(mail);
        //Then
        assertThat(actual).isNull();

    }

    @Test
    void shouldNotMatchWhenEmptyList() throws MessagingException {
        //Given
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderHostIs")
                .mailetContext(mailContext)
                .condition("")
                .build());

        Mail mail = FakeMail.builder()
                .name("mail")
                .sender(ANY_AT_JAMES2)
                .recipient(ANY_AT_JAMES2)
                .build();
        //When
        Collection<MailAddress> actual = matcher.match(mail);
        //Then
        assertThat(actual).isNull();
    }

    @Test
    void shouldNotMatchWhenNullSender() throws MessagingException {
        //Given
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderHostIs")
                .mailetContext(mailContext)
                .condition("domain.tld")
                .build());

        Mail mail = FakeMail.builder()
                .name("mail")
                .sender(ANY_AT_JAMES2)
                .recipient(ANY_AT_JAMES2)
                .build();
        //When
        Collection<MailAddress> actual = matcher.match(mail);
        //Then
        assertThat(actual).isNull();
    }

    @Test
    void shouldNotMatchWhenNoSender() throws MessagingException {
        //Given
        matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderHostIs")
                .mailetContext(mailContext)
                .condition("")
                .build());

        Mail mail = FakeMail.builder()
                .name("mail")
                .recipient(ANY_AT_JAMES2)
                .build();
        //When
        Collection<MailAddress> actual = matcher.match(mail);
        //Then
        assertThat(actual).isNull();
    }

    @Test
    void shouldThrowWhenNullCondition() {
        assertThatThrownBy(() ->
            matcher.init(FakeMatcherConfig.builder()
                .matcherName("SenderHostIs")
                .mailetContext(mailContext)
                .condition(null)
                .build()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void parseDomainsListShouldParseWhenOnlyOneDomain() {
        //When
        Collection<Domain> senderHosts = matcher.parseDomainsList("james.apache.org");
        //Then
        assertThat(senderHosts).containsOnly(Domain.of("james.apache.org"));
    }

    @Test
    void parseDomainsListShouldParseWhenCommaSpacePattern() {
        //When
        Collection<Domain> senderHosts = matcher.parseDomainsList("james.apache.org, james2.apache.org, james3.apache.org, james4.apache.org, james5.apache.org");
        //Then
        assertThat(senderHosts).containsOnly(
            Domain.of("james.apache.org"),
            Domain.of("james2.apache.org"),
            Domain.of("james3.apache.org"),
            Domain.of("james4.apache.org"),
            Domain.of("james5.apache.org"));
    }

    @Test
    void parseDomainsListShouldParseWhenCommaPattern() {
        //When
        Collection<Domain> senderHosts = matcher.parseDomainsList("james.apache.org,james2.apache.org,james3.apache.org,james4.apache.org,james5.apache.org");
        //Then
        assertThat(senderHosts).containsOnly(
            Domain.of("james.apache.org"),
            Domain.of("james2.apache.org"),
            Domain.of("james3.apache.org"),
            Domain.of("james4.apache.org"),
            Domain.of("james5.apache.org"));
    }

    @Test
    void parseDomainsListShouldParseWhenSpacePattern() {
        //When
        Collection<Domain> senderHosts = matcher.parseDomainsList("james.apache.org james2.apache.org james3.apache.org james4.apache.org james5.apache.org");
        //Then
        assertThat(senderHosts).containsOnly(
            Domain.of("james.apache.org"),
            Domain.of("james2.apache.org"),
            Domain.of("james3.apache.org"),
            Domain.of("james4.apache.org"),
            Domain.of("james5.apache.org"));
    }

    @Test
    void parseDomainsListShouldParseWhenMixedPatterns() {
        //When
        Collection<Domain> senderHosts = matcher.parseDomainsList("james.apache.org james2.apache.org,james3.apache.org, james4.apache.org james5.apache.org");
        //Then
        assertThat(senderHosts).containsOnly(
            Domain.of("james.apache.org"),
            Domain.of("james2.apache.org"),
            Domain.of("james3.apache.org"),
            Domain.of("james4.apache.org"),
            Domain.of("james5.apache.org"));
    }

    @Test
    void parseDomainsListShouldIgnoreEmptyDomains() {
        //When
        Collection<Domain> senderHosts = matcher.parseDomainsList("james.apache.org   james2.apache.org james3.apache.org , james4.apache.org,,,james5.apache.org");
        //Then
        assertThat(senderHosts).containsOnly(
            Domain.of("james.apache.org"),
            Domain.of("james2.apache.org"),
            Domain.of("james3.apache.org"),
            Domain.of("james4.apache.org"),
            Domain.of("james5.apache.org"));
    }
}
