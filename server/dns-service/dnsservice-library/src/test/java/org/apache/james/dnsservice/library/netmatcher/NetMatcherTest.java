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
package org.apache.james.dnsservice.library.netmatcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.UnknownHostException;

import org.apache.james.dnsservice.api.mock.DNSFixture;
import org.junit.Test;

/**
 * Test the NetMatcher class with various IPv4 and IPv6 parameters.
 * 
 */
public class NetMatcherTest {

    private static NetMatcher netMatcher;

    /**
     * Test ability to split expressions even when comma is not followed by a space.
     */
    @Test
    public void testSplitterDoesNotRequireSpaceAfterComma() {
        netMatcher = new NetMatcher("127.0.0.1,192.168.100.14/24,10.*", DNSFixture.DNS_SERVER_IPV4_MOCK);
        assertThat(netMatcher.toString())
                .isEqualTo("[10.0.0.0/255.0.0.0, 127.0.0.1/255.255.255.255, 192.168.100.0/255.255.255.0]");
    }

    /**
     * Test for IPV4 uniqueness.
     */
    @Test
    public void testIpV4NetworksUniqueness() {
        netMatcher = new NetMatcher(DNSFixture.LOCALHOST_IP_V4_ADDRESSES_DUPLICATE, DNSFixture.DNS_SERVER_IPV4_MOCK);
        assertThat(netMatcher.toString()).isEqualTo("[172.16.0.0/255.255.0.0, 192.168.1.0/255.255.255.0]");
    }

    /**
     * Test for IPV6 uniqueness.
     */
    @Test
    public void testIpV6NetworksUniqueness() {
        netMatcher = new NetMatcher(DNSFixture.LOCALHOST_IP_V6_ADDRESSES_DUPLICATE, DNSFixture.DNS_SERVER_IPV6_MOCK);
        assertThat(netMatcher.toString()).isEqualTo("[0:0:0:0:0:0:0:1/32768, 2781:db8:1234:0:0:0:0:0/48]");
    }

    /**
     * Test for IPV4 matcher.
     * @throws UnknownHostException
     */
    @Test
    public void testIpV4Matcher() throws UnknownHostException {

        netMatcher = new NetMatcher(DNSFixture.LOCALHOST_IP_V4_ADDRESSES, DNSFixture.DNS_SERVER_IPV4_MOCK);

        assertThat(netMatcher.matchInetNetwork("127.0.0.1")).isTrue();
        assertThat(netMatcher.matchInetNetwork("localhost")).isTrue();
        assertThat(netMatcher.matchInetNetwork("172.16.15.254")).isTrue();
        assertThat(netMatcher.matchInetNetwork("192.168.1.254")).isTrue();
        assertThat(netMatcher.matchInetNetwork("192.169.1.254")).isFalse();
    }

    /**
     * @throws UnknownHostException
     */
    @Test
    public void testIpV4MatcherWithIpV6() throws UnknownHostException {

        netMatcher = new NetMatcher(DNSFixture.LOCALHOST_IP_V4_ADDRESSES, DNSFixture.DNS_SERVER_IPV4_MOCK);

        assertThat(netMatcher.matchInetNetwork("0:0:0:0:0:0:0:1%0")).isFalse();
        assertThat(netMatcher.matchInetNetwork("00:00:00:00:00:00:00:1")).isFalse();
        assertThat(netMatcher.matchInetNetwork("00:00:00:00:00:00:00:2")).isFalse();
        assertThat(netMatcher.matchInetNetwork("2781:0db8:1234:8612:45ee:ffff:fffe:0001")).isFalse();
        assertThat(netMatcher.matchInetNetwork("2781:0db8:1235:8612:45ee:ffff:fffe:0001")).isFalse();
    }

    /**
     * @throws UnknownHostException
     */
    @Test
    public void testIpV6Matcher() throws UnknownHostException {

        netMatcher = new NetMatcher(DNSFixture.LOCALHOST_IP_V6_ADDRESSES, DNSFixture.DNS_SERVER_IPV6_MOCK);

        assertThat(netMatcher.matchInetNetwork("0:0:0:0:0:0:0:1%0")).isTrue();
        assertThat(netMatcher.matchInetNetwork("00:00:00:00:00:00:00:1")).isTrue();
        assertThat(netMatcher.matchInetNetwork("00:00:00:00:00:00:00:2")).isFalse();
        assertThat(netMatcher.matchInetNetwork("2781:0db8:1234:8612:45ee:ffff:fffe:0001")).isTrue();
        assertThat(netMatcher.matchInetNetwork("2781:0db8:1235:8612:45ee:ffff:fffe:0001")).isFalse();
    }

    /**
     * @throws UnknownHostException
     */
    @Test
    public void testIpV6MatcherWithIpV4() throws UnknownHostException {

        netMatcher = new NetMatcher(DNSFixture.LOCALHOST_IP_V6_ADDRESSES, DNSFixture.DNS_SERVER_IPV6_MOCK);

        assertThat(netMatcher.matchInetNetwork("127.0.0.1")).isFalse();
        assertThat(netMatcher.matchInetNetwork("localhost")).isFalse();
        assertThat(netMatcher.matchInetNetwork("172.16.15.254")).isFalse();
        assertThat(netMatcher.matchInetNetwork("192.168.1.254")).isFalse();
        assertThat(netMatcher.matchInetNetwork("192.169.1.254")).isFalse();
    }
}
