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

package org.apache.james.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.cli.util.OutputCapture;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.modules.protocols.SieveProbeImpl;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SieveQuotaCommandsIntegrationTest {
    public static final String USER = "user";

    @RegisterExtension
    JamesServerExtension memoryJmap = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
        .server(conf -> MemoryJamesServerMain.createServer(conf)
            .overrideWith(new JMXServerModule(),
                binder -> binder.bind(ListeningMessageSearchIndex.class).toInstance(mock(ListeningMessageSearchIndex.class))))
        .build();
    private SieveProbeImpl sieveProbe;
    private OutputCapture outputCapture;

    @BeforeEach
    void setUp(GuiceJamesServer guiceJamesServer) {
        outputCapture = new OutputCapture();
        sieveProbe = guiceJamesServer.getProbe(SieveProbeImpl.class);
    }

    @Test
    void setSieveUserQuotaShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setsieveuserquota", USER, "36"});

        assertThat(sieveProbe.getSieveQuota(USER)).isEqualTo(36);
    }

    @Test
    void getSieveUserQuotaShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setsieveuserquota", USER, "36"});

        ServerCmd.executeAndOutputToStream(new String[] {"-h", "127.0.0.1", "-p", "9999", "getsieveuserquota", USER},
            outputCapture.getPrintStream());

        assertThat(outputCapture.getContent())
            .containsOnlyOnce("Storage space allowed for user Sieve scripts: 36 bytes");
    }

    @Test
    void setSieveQuotaShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setsievequota", "36"});

        assertThat(sieveProbe.getSieveQuota()).isEqualTo(36);
    }

    @Test
    void getSieveQuotaShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setsievequota", "36"});

        ServerCmd.executeAndOutputToStream(new String[] {"-h", "127.0.0.1", "-p", "9999", "getsievequota"},
            outputCapture.getPrintStream());

        assertThat(outputCapture.getContent())
            .containsOnlyOnce("Storage space allowed for Sieve scripts by default: 36 bytes");
    }

    @Test
    void removeSieveUserQuotaShouldWork() throws Exception {
        sieveProbe.setSieveQuota(USER, 36);

        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "removesieveuserquota", USER});

        assertThatThrownBy(() -> sieveProbe.getSieveQuota(USER))
            .isInstanceOf(QuotaNotFoundException.class);
    }

    @Test
    void removeSieveQuotaShouldWork() throws Exception {
        sieveProbe.setSieveQuota(36);

        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "removesievequota"});

        assertThatThrownBy(() -> sieveProbe.getSieveQuota())
            .isInstanceOf(QuotaNotFoundException.class);
    }
}
