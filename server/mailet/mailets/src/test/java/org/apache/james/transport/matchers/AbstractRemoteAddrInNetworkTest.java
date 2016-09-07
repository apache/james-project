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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import javax.mail.MessagingException;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.mock.MockDNSService;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMatcherConfig;

import com.google.common.collect.ImmutableList;

public abstract class AbstractRemoteAddrInNetworkTest {
    protected static List<String> KNOWN_ADDRESSES = ImmutableList.of("192.168.200.0", "255.255.255.0", "192.168.200.1", "192.168.0.1", "192.168.1.1");

    protected Mail fakeMail;
    protected AbstractNetworkMatcher matcher;
    private String remoteAddr;
    private DNSService dnsServer;

    protected void setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }

    protected void setupFakeMail() throws MessagingException {
        fakeMail = FakeMail.builder()
                        .recipient(new MailAddress("test@james.apache.org"))
                        .remoteAddr(remoteAddr)
                        .build();
    }

    protected void setupDNSServer() {
        dnsServer = new MockDNSService() {

            @Override
            public InetAddress getByName(String host) throws UnknownHostException {
                if (KNOWN_ADDRESSES.contains(host)) {
                    // called with an IP it only check formal validity
                    return InetAddress.getByName(host);
                }
                throw new UnsupportedOperationException(
                        "getByName(" + host + ") unimplemented in AbstractRemoteAddrInNetworkTest");
            }
        };
    }

    protected void setupMatcher() throws MessagingException {

        FakeMailContext mmc = FakeMailContext.defaultContext();
        matcher = createMatcher();
        matcher.setDNSService(dnsServer);
        FakeMatcherConfig mci = new FakeMatcherConfig(getConfigOption() + getAllowedNetworks(), mmc);
        matcher.init(mci);
    }

    protected void setupAll() throws MessagingException {
        setupDNSServer();
        setupFakeMail();
        setupMatcher();
    }

    protected abstract String getConfigOption();

    protected abstract String getAllowedNetworks();

    protected abstract AbstractNetworkMatcher createMatcher();
}
