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
package org.apache.james.dnsservice.library;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.TemporaryResolutionException;
import static org.junit.Assert.*;
import org.junit.Test;
import org.slf4j.LoggerFactory;

public class MXHostAddressIteratorTest {

    /**
     * Test case for JAMES-1251
     */
    @Test
    public void testIteratorContainMultipleMX() {
        DNSService dns = new DNSService() {

            @Override
            public InetAddress getLocalHost() throws UnknownHostException {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getHostName(InetAddress addr) {
                throw new UnsupportedOperationException();
            }

            @Override
            public InetAddress getByName(String host) throws UnknownHostException {
                return InetAddress.getLocalHost();
            }

            /**
             * Every time this method is called it will return two InetAddress instances
             */
            @Override
            public InetAddress[] getAllByName(String host) throws UnknownHostException {
                InetAddress addr = InetAddress.getLocalHost();
                return new InetAddress[]{addr, addr};
            }

            @Override
            public Collection<String> findTXTRecords(String hostname) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Collection<String> findMXRecords(String hostname) throws TemporaryResolutionException {
                throw new UnsupportedOperationException();
            }
        };
        MXHostAddressIterator it = new MXHostAddressIterator(Arrays.asList("localhost", "localhost2").iterator(), dns,
                false, LoggerFactory.getLogger(this.getClass()));
        for (int i = 0; i < 4; i++) {
            assertTrue(it.hasNext());
            assertNotNull(it.next());
        }
        assertFalse(it.hasNext());

        it = new MXHostAddressIterator(Arrays.asList("localhost", "localhost2").iterator(), dns, true, LoggerFactory.
                getLogger(this.getClass()));
        for (int i = 0; i < 2; i++) {
            assertTrue(it.hasNext());
            assertNotNull(it.next());
        }
        assertFalse(it.hasNext());
    }

    @Test
    public void testIteratorWithInvalidMX() {
        DNSService dns = new DNSService() {

            @Override
            public InetAddress getLocalHost() throws UnknownHostException {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getHostName(InetAddress addr) {
                throw new UnsupportedOperationException();
            }

            @Override
            public InetAddress getByName(String host) throws UnknownHostException {
                throw new UnknownHostException();
            }

            /**
             * Every time this method is called it will return two InetAddress instances
             */
            @Override
            public InetAddress[] getAllByName(String host) throws UnknownHostException {
                throw new UnknownHostException();
            }

            @Override
            public Collection<String> findTXTRecords(String hostname) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Collection<String> findMXRecords(String hostname) throws TemporaryResolutionException {
                throw new UnsupportedOperationException();
            }
        };

        // See JAMES-1271
        MXHostAddressIterator it = new MXHostAddressIterator(Arrays.asList("localhost").iterator(), dns, false,
                LoggerFactory.getLogger(this.getClass()));
        assertFalse(it.hasNext());
    }
}
