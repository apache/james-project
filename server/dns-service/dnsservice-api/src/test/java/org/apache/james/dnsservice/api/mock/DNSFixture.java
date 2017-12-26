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
package org.apache.james.dnsservice.api.mock;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;

import org.apache.james.dnsservice.api.DNSService;

import com.google.common.collect.ImmutableList;

/**
 * Some DNS Fixtures used by various Test related to DNS and InetNetwork.
 */
public class DNSFixture {

    public static final String LOCALHOST_IP_V4_ADDRESS_0 = "localhost/16";
    public static final String LOCALHOST_IP_V4_ADDRESS_1 = "172.16.0.0/16";
    public static final String LOCALHOST_IP_V4_ADDRESS_2 = "192.168.1.0/255.255.255.0";
    public static final String[] LOCALHOST_IP_V4_ADDRESSES = new String[]{LOCALHOST_IP_V4_ADDRESS_0,
        LOCALHOST_IP_V4_ADDRESS_1, LOCALHOST_IP_V4_ADDRESS_2};
    public static final String[] LOCALHOST_IP_V4_ADDRESSES_DUPLICATE = new String[]{LOCALHOST_IP_V4_ADDRESS_1,
        LOCALHOST_IP_V4_ADDRESS_1, LOCALHOST_IP_V4_ADDRESS_2, LOCALHOST_IP_V4_ADDRESS_2};
    public static final String LOCALHOST_IP_V6_ADDRESS_0 = "00:00:00:00:00:00:00:1";
    public static final String LOCALHOST_IP_V6_ADDRESS_1 = "2781:0db8:1234:8612:45ee:0000:f05e:0001/48";
    public static final String[] LOCALHOST_IP_V6_ADDRESSES = new String[]{LOCALHOST_IP_V6_ADDRESS_0,
        LOCALHOST_IP_V6_ADDRESS_1};
    public static final String[] LOCALHOST_IP_V6_ADDRESSES_DUPLICATE = new String[]{LOCALHOST_IP_V6_ADDRESS_0,
        LOCALHOST_IP_V6_ADDRESS_0, LOCALHOST_IP_V6_ADDRESS_1, LOCALHOST_IP_V6_ADDRESS_1};
    /**
     * A Mock DNS Server that handles IPv4-only InetAddress.
     */
    public static final DNSService DNS_SERVER_IPV4_MOCK = new MockDNSService() {

        @Override
        public String getHostName(InetAddress inet) {
            return "localhost";
        }

        @Override
        public Collection<InetAddress> getAllByName(String name) throws UnknownHostException {
            return ImmutableList.of(InetAddress.getByName("127.0.0.1"));
        }

        @Override
        public InetAddress getLocalHost() throws UnknownHostException {
            return InetAddress.getLocalHost();
        }

        @Override
        public InetAddress getByName(String host) throws UnknownHostException {
            return InetAddress.getByName(host);
        }
    };
    /**
     * A Mock DNS Server that handles IPv6-only InetAddress.
     */
    public static final DNSService DNS_SERVER_IPV6_MOCK = new MockDNSService() {

        @Override
        public String getHostName(InetAddress inet) {
            return "localhost";
        }

        @Override
        public Collection<InetAddress> getAllByName(String name) throws UnknownHostException {
            return ImmutableList.of(InetAddress.getByName("::1"));
        }

        @Override
        public InetAddress getLocalHost() throws UnknownHostException {
            return InetAddress.getLocalHost();
        }

        @Override
        public InetAddress getByName(String host) throws UnknownHostException {
            return InetAddress.getByName(host);
        }
    };
}
