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
package org.apache.james.dnsservice.library.inetnetwork;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.UnknownHostException;

import org.apache.james.dnsservice.api.mock.DNSFixture;
import org.apache.james.dnsservice.library.inetnetwork.model.InetNetwork;
import org.junit.jupiter.api.Test;

public class InetNetworkBuilderTest {
    private InetNetworkBuilder inetNetworkBuilder;
    private InetNetwork inetNetwork;

    /**
     * Verify that InetNetworkBuild return correctly initialized Inet4Network.
     */
    @Test
    void testInetNetworkBuilderDnsV4() throws UnknownHostException {

        inetNetworkBuilder = new InetNetworkBuilder(DNSFixture.DNS_SERVER_IPV4_MOCK);

        inetNetwork = inetNetworkBuilder.getFromString(DNSFixture.LOCALHOST_IP_V4_ADDRESS_0);
        assertThat(inetNetwork.toString()).isEqualTo("127.0.0.0/255.255.0.0");

        inetNetwork = inetNetworkBuilder.getFromString(DNSFixture.LOCALHOST_IP_V4_ADDRESS_1);
        assertThat(inetNetwork.toString()).isEqualTo("172.16.0.0/255.255.0.0");

    }

    /**
     * Verify that InetNetworkBuild return correctly initialized Inet6Network.
     */
    @Test
    void testInetNetworkBuilderDnsV6() throws UnknownHostException {

        inetNetworkBuilder = new InetNetworkBuilder(DNSFixture.DNS_SERVER_IPV6_MOCK);

        inetNetwork = inetNetworkBuilder.getFromString(DNSFixture.LOCALHOST_IP_V6_ADDRESS_0);
        assertThat(inetNetwork.toString()).isEqualTo("0:0:0:0:0:0:0:1/32768");

        inetNetwork = inetNetworkBuilder.getFromString(DNSFixture.LOCALHOST_IP_V6_ADDRESS_1);
        assertThat(inetNetwork.toString()).isEqualTo("2781:db8:1234:0:0:0:0:0/48");

    }
}
