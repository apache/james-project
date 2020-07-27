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

package org.apache.james.jmap.memory;

import java.io.IOException;

import org.apache.james.GuiceJamesServer;
import org.apache.james.MemoryJmapTestRule;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.jmap.VacationRelayIntegrationTest;
import org.junit.Rule;

public class MemoryVacationRelayIntegrationTest extends VacationRelayIntegrationTest {

    @Rule
    public MemoryJmapTestRule memoryJmap = new MemoryJmapTestRule();

    private final InMemoryDNSService inMemoryDNSService = new InMemoryDNSService();

    @Override
    protected GuiceJamesServer getJmapServer() throws IOException {
        return memoryJmap.jmapServer((binder) -> binder.bind(DNSService.class).toInstance(inMemoryDNSService));
    }

    @Override
    protected InMemoryDNSService getInMemoryDns() {
        return inMemoryDNSService;
    }
}
