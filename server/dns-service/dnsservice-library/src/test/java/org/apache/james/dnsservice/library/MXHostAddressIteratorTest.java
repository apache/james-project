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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.util.Arrays;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public class MXHostAddressIteratorTest {
    /**
     * Test case for JAMES-1251
     */
    @Test
    void testIteratorContainMultipleMX() throws Exception {
        InetAddress address = InetAddress.getLocalHost();
        ImmutableList<String> mxs = ImmutableList.of(address.getHostAddress());
        ImmutableList<String> noTxtRecord = ImmutableList.of();
        ImmutableList<InetAddress> addresses = ImmutableList.of(address, address);
        DNSService dns = new InMemoryDNSService()
            .registerRecord("localhost", addresses, mxs, noTxtRecord)
            .registerRecord("localhost2", addresses, mxs, noTxtRecord);

        MXHostAddressIterator it = new MXHostAddressIterator(Arrays.asList("localhost", "localhost2").iterator(), dns, false);
        for (int i = 0; i < 4; i++) {
            assertThat(it.hasNext()).isTrue();
            assertThat(it.next()).isNotNull();
        }
        assertThat(it.hasNext()).isFalse();

        it = new MXHostAddressIterator(Arrays.asList("localhost", "localhost2").iterator(), dns, true);
        for (int i = 0; i < 2; i++) {
            assertThat(it.hasNext()).isTrue();
            assertThat(it.next()).isNotNull();
        }
        assertThat(it.hasNext()).isFalse();
    }

    @Test
    void testIteratorWithInvalidMX() {

        // See JAMES-1271
        MXHostAddressIterator it = new MXHostAddressIterator(Arrays.asList("localhost").iterator(), new InMemoryDNSService(), false);
        assertThat(it.hasNext()).isFalse();
    }
}
