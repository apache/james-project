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

package org.apache.james;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;

import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DomainAutodetectionTest {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
        .server(configuration -> MemoryJamesServerMain.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> binder.bind(DomainListConfiguration.class)
                .toInstance(DomainListConfiguration.builder()
                    .autoDetect(true)
                    .autoDetectIp(false)
                    .build())))
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    @Test
    void hostnameShouldBeRetrievedWhenRestarting(GuiceJamesServer jamesServer) throws Exception {
        jamesServer.stop();
        jamesServer.start();
        String expectedDefaultDomain = InetAddress.getLocalHost().getHostName();

        assertThat(jamesServer.getProbe(DataProbeImpl.class).getDefaultDomain()).isEqualTo(expectedDefaultDomain);
    }

    @Test
    void hostnameShouldBeUsedAsDefaultDomain(GuiceJamesServer jamesServer) throws Exception {
        String expectedDefaultDomain = InetAddress.getLocalHost().getHostName();

        assertThat(jamesServer.getProbe(DataProbeImpl.class).getDefaultDomain()).isEqualTo(expectedDefaultDomain);
    }
}
