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

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;

import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.TemporaryResolutionException;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class SenderHasMXRecordTest {

    private DNSService dnsService;
    private SenderHasMXRecord testee;

    @BeforeEach
    void setUp() {
        dnsService = mock(DNSService.class);
        testee = new SenderHasMXRecord(dnsService);
    }

    @Test
    void initShouldRejectEmptyConfig() {
        assertThatThrownBy(() -> testee.init(FakeMatcherConfig.builder()
            .matcherName("SenderHasMXRecord")
            .condition("")
            .build())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initShouldRejectEmptyDomain() {
        assertThatThrownBy(() -> testee.init(FakeMatcherConfig.builder()
            .matcherName("SenderHasMXRecord")
            .condition("domain.tld,,other.tld")
            .build())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initShouldRejectInvalidDomain() {
        assertThatThrownBy(() -> testee.init(FakeMatcherConfig.builder()
            .matcherName("SenderHasMXRecord")
            .condition("invalid@com")
            .build())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matchShouldReturnEmptyWhenNoDnsEntry() throws Exception {
        testee.init(FakeMatcherConfig.builder()
            .matcherName("SenderHasMXRecord")
            .condition("mx.domain.com")
            .build());
        when(dnsService.findMXRecords(anyString())).thenThrow(new TemporaryResolutionException());

        Collection<MailAddress> result = testee.match(FakeMail.builder()
            .name("anything")
            .sender("semder@domain.com")
            .recipient("recipient1@other.com")
            .build());

        assertThat(result).isEmpty();
    }

    @Test
    void matchShouldReturnEmptyWhenWrongDnsEntry() throws Exception {
        testee.init(FakeMatcherConfig.builder()
            .matcherName("SenderHasMXRecord")
            .condition("mx.domain.com")
            .build());

        when(dnsService.findMXRecords(anyString()))
            .thenReturn(ImmutableList.of("mx.unrelated.com"));

        Collection<MailAddress> result = testee.match(FakeMail.builder()
            .name("anything")
            .sender("semder@domain.com")
            .recipient("recipient1@other.com")
            .build());

        assertThat(result).isEmpty();
    }

    @Test
    void matchShouldReturnEmptyWhenUnrelatedDnsEntry() throws Exception {
        testee.init(FakeMatcherConfig.builder()
            .matcherName("SenderHasMXRecord")
            .condition("mx.domain.com")
            .build());

        when(dnsService.findMXRecords(anyString()))
            .thenReturn(ImmutableList.of("mx.domain.com", "mx.unrelated.com"));

        Collection<MailAddress> result = testee.match(FakeMail.builder()
            .name("anything")
            .sender("semder@domain.com")
            .recipient("recipient1@other.com")
            .build());

        assertThat(result).isEmpty();
    }

    @Test
    void matchShouldReturnMailRecipientsWhenCorrectMX() throws Exception {
        testee.init(FakeMatcherConfig.builder()
            .matcherName("SenderHasMXRecord")
            .condition("mx.domain.com")
            .build());

        when(dnsService.findMXRecords(anyString()))
            .thenReturn(ImmutableList.of("mx.domain.com"));

        Collection<MailAddress> result = testee.match(FakeMail.builder()
            .name("anything")
            .sender("semder@domain.com")
            .recipient("recipient1@other.com")
            .build());

        assertThat(result).isNotEmpty();
    }

    @Test
    void matchShouldSupportMultipleMXRecords() throws Exception {
        testee.init(FakeMatcherConfig.builder()
            .matcherName("SenderHasMXRecord")
            .condition("mx1.domain.com,mx2.domain.com")
            .build());

        when(dnsService.findMXRecords(anyString()))
            .thenReturn(ImmutableList.of("mx1.domain.com", "mx2.domain.com"));

        Collection<MailAddress> result = testee.match(FakeMail.builder()
            .name("anything")
            .sender("semder@domain.com")
            .recipient("recipient1@other.com")
            .build());

        assertThat(result).isNotEmpty();
    }
}
