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

import java.util.Collection;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RemoteAddrNotInNetworkTest {
    private RemoteAddrNotInNetwork matcher;
    private FakeMail fakeMail;
    private MailAddress testRecipient;
    private FakeMatcherConfig matcherConfig;

    @BeforeEach
    void setup() throws Exception {
        DNSService dnsServer = new InMemoryDNSService()
            .registerMxRecord("192.168.0.1", "192.168.0.1")
            .registerMxRecord("192.168.200.1", "192.168.200.1")
            .registerMxRecord("192.168.200.0", "192.168.200.0")
            .registerMxRecord("255.255.255.0", "255.255.255.0");
        matcherConfig = FakeMatcherConfig.builder()
                .matcherName("AllowedNetworkIs")
                .condition("192.168.200.0/24")
                .build();

        matcher = new RemoteAddrNotInNetwork();
        matcher.setDNSService(dnsServer);
        matcher.init(matcherConfig);
        testRecipient = new MailAddress("test@james.apache.org");
    }

    @Test
    void shouldMatchWhenOnDifferentNetwork() throws MessagingException {
        fakeMail = FakeMail.builder()
                .name("name")
                .recipient(testRecipient)
                .remoteAddr("192.168.0.1")
                .build();

        Collection<MailAddress> actual = matcher.match(fakeMail);

        assertThat(actual).containsOnly(testRecipient);
    }

    @Test
    void shouldNotMatchWhenOnSameNetwork() throws MessagingException {
        fakeMail = FakeMail.builder()
                .name("name")
                .recipient(testRecipient)
                .remoteAddr("192.168.200.1")
                .build();

        Collection<MailAddress> actual = matcher.match(fakeMail);

        assertThat(actual).isNull();
    }

    @Test
    void shouldMatchWhenNoCondition() throws MessagingException {
        matcherConfig = FakeMatcherConfig.builder()
                .matcherName("")
                .build();

        matcher = new RemoteAddrNotInNetwork();
        matcher.init(matcherConfig);

        fakeMail = FakeMail.builder()
                .name("name")
                .recipient(testRecipient)
                .build();

        Collection<MailAddress> actual = matcher.match(fakeMail);

        assertThat(actual).containsOnly(testRecipient);
    }

    @Test
    void shouldMatchWhenInvalidAddress() throws MessagingException {
        fakeMail = FakeMail.builder()
                .name("name")
                .recipient(testRecipient)
                .remoteAddr("invalid")
                .build();

        Collection<MailAddress> actual = matcher.match(fakeMail);

        assertThat(actual).containsOnly(testRecipient);
    }
}
