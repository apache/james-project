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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.mock.MockDNSService;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.Test;

class InSpammerBlacklistTest {
    private static final String BLACKLIST = "my.black.list.";
    private static final StringBuffer LISTED_HOST = new StringBuffer("111.222.111.222");

    private InSpammerBlacklist matcher;

    private DNSService setUpDNSServer() {
        return new MockDNSService() {

            @Override
            public InetAddress getByName(String name) throws UnknownHostException {
                if (name.equals(LISTED_HOST.reverse() + "." + BLACKLIST)) {
                    return null;
                } else {
                    throw new UnknownHostException("Not listed");
                }
            }
        };
    }

    private Mail createMail(String remoteAddr) throws MessagingException {
        return FakeMail.builder()
                .name("name")
                .remoteAddr(remoteAddr)
                .recipient("test@email")
                .build();

    }

    private void setupMatcher(String blacklist) throws MessagingException {
        matcher = new InSpammerBlacklist();
        matcher.setDNSService(setUpDNSServer());
        FakeMatcherConfig mci = FakeMatcherConfig.builder()
                .matcherName("InSpammerBlacklist")
                .condition(blacklist)
                .build();

        matcher.init(mci);
    }

    @Test
    void testInBlackList() throws MessagingException {
        Mail mail = createMail(LISTED_HOST.toString());
        setupMatcher(BLACKLIST);

        Collection<MailAddress> matchedRecipients = matcher.match(mail);

        assertThat(matchedRecipients).isNotNull();
        assertThat(matchedRecipients.size()).isEqualTo(mail.getRecipients().size());
    }

    @Test
    void testNotInBlackList() throws MessagingException {
        Mail mail = createMail("212.12.14.1");
        setupMatcher(BLACKLIST);

        Collection<MailAddress> matchedRecipients = matcher.match(mail);

        assertThat(matchedRecipients).isNull();
    }
}
