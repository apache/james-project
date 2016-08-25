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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;

import javax.mail.MessagingException;
import javax.mail.internet.ParseException;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.mock.MockDNSService;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.Test;

public class InSpammerBlacklistTest {

    private FakeMail mockedMail;
    private InSpammerBlacklist matcher;
    private final static String BLACKLIST = "my.black.list.";
    private final static StringBuffer LISTED_HOST = new StringBuffer("111.222.111.222");

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

    private void setupMockedMail(String remoteAddr) throws ParseException {
        mockedMail = new FakeMail();
        mockedMail.setRemoteAddr(remoteAddr);
        mockedMail.setRecipients(Arrays.asList(new MailAddress("test@email")));

    }

    private void setupMatcher(String blacklist) throws MessagingException {
        matcher = new InSpammerBlacklist();
        matcher.setDNSService(setUpDNSServer());
        FakeMailContext context = FakeMailContext.defaultContext();
        FakeMatcherConfig mci = new FakeMatcherConfig("InSpammerBlacklist=" + blacklist, context);
        matcher.init(mci);
    }

    @Test
    public void testInBlackList() throws MessagingException {
        setupMockedMail(LISTED_HOST.toString());
        setupMatcher(BLACKLIST);

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNotNull(matchedRecipients);
        assertEquals(matchedRecipients.size(), mockedMail.getRecipients().size());
    }

    @Test
    public void testNotInBlackList() throws MessagingException {
        setupMockedMail("212.12.14.1");
        setupMatcher(BLACKLIST);

        Collection<MailAddress> matchedRecipients = matcher.match(mockedMail);

        assertNull(matchedRecipients);
    }
}
