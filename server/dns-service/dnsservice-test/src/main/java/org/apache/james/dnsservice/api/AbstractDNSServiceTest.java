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
package org.apache.james.dnsservice.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;

import org.apache.james.dnsservice.api.mock.MockDNSService;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.collect.ImmutableList;


/**
 * Basic tests for AbstractDNSServer. The goal is to verify that the interface
 * remains constants and that the built platform has access to the Internet.
 */
public class AbstractDNSServiceTest {

    /**
     * Simple Mock DNSService relaying on InetAddress.
     */
    private static final DNSService DNS_SERVER = new MockDNSService() {

        @Override
        public String getHostName(InetAddress inet) {
            return inet.getCanonicalHostName();
        }

        @Override
        public Collection<InetAddress> getAllByName(String name) throws UnknownHostException {
            return ImmutableList.copyOf(InetAddress.getAllByName(name));
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
     * Simple localhost resolution.
     *
     * @throws UnknownHostException
     */
    @Test
    public void testLocalhost() throws UnknownHostException {

        assertEquals("localhost/127.0.0.1", DNS_SERVER.getByName("localhost").toString());

        String localHost = DNS_SERVER.getHostName(InetAddress.getByName("127.0.0.1"));
        // We only can check if the returned localhost is not empty. Its value
        // depends on the hosts file.
        assertTrue(localHost.length() > 0);
    }

    /**
     * Simple apache.org resolution.
     *
     * @throws UnknownHostException
     */
    @Test
    @Ignore(value = "It requires internet connection!")
    public void testApache() throws UnknownHostException {
        //TODO: move to some sort of Live tests
        assertEquals(true, DNS_SERVER.getByName("www.apache.org").toString().startsWith("www.apache.org"));
    }
}
