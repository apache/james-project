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

package org.apache.james.jmap.rfc8621.memory;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesConfiguration;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.jmap.rfc8621.contract.IdentityProbeModule;
import org.apache.james.jmap.rfc8621.contract.VacationRelayIntegrationTest;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.mock.smtp.server.testing.MockSmtpServerExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.extension.RegisterExtension;

public class MemoryVacationRelayIntegrationTest implements VacationRelayIntegrationTest {
    private static final InMemoryDNSService inMemoryDNSService = new InMemoryDNSService();

    @RegisterExtension
    static MockSmtpServerExtension fakeSmtp = new MockSmtpServerExtension();

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .enableJMAP()
            .build())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule(), new DelegationProbeModule(), new IdentityProbeModule())
            .overrideWith((binder -> binder.bind(DNSService.class).toInstance(inMemoryDNSService))))
        .build();

    @Override
    public MockSmtpServerExtension getFakeSmtp() {
        return fakeSmtp;
    }

    @Override
    public InMemoryDNSService getInMemoryDns() {
        return inMemoryDNSService;
    }
}
