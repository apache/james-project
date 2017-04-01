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
import static org.mockito.Mockito.mock;

import javax.inject.Singleton;

import org.apache.james.GuiceJamesServer;
import org.apache.james.MemoryJmapTestRule;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.modules.TestFilesystemModule;
import org.apache.james.modules.protocols.SieveProbeImpl;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;
import org.apache.james.utils.MailboxManagerDefinition;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import com.google.inject.Inject;
import com.google.inject.multibindings.Multibinder;

public class SieveQuotaCommandsIntegrationTest {
    public static final String USER = "user";

    @Singleton
    private static class MemoryMailboxManagerDefinition extends MailboxManagerDefinition {
        @Inject
        private MemoryMailboxManagerDefinition(InMemoryMailboxManager manager) {
            super("memory-mailboxmanager", manager);
        }
    }

    @Rule
    public MemoryJmapTestRule memoryJmap = new MemoryJmapTestRule();
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private GuiceJamesServer guiceJamesServer;
    private SieveProbeImpl sieveProbe;

    @Before
    public void setUp() throws Exception {
        guiceJamesServer = memoryJmap.jmapServer(new JMXServerModule(),
            binder -> binder.bind(ListeningMessageSearchIndex.class).toInstance(mock(ListeningMessageSearchIndex.class)),
            binder -> Multibinder.newSetBinder(binder, MailboxManagerDefinition.class)
                .addBinding()
                .to(MemoryMailboxManagerDefinition.class))
            .overrideWith(new TestFilesystemModule(temporaryFolder));
        guiceJamesServer.start();
        sieveProbe = guiceJamesServer.getProbe(SieveProbeImpl.class);
    }

    @After
    public void tearDown() {
        guiceJamesServer.stop();
    }

    @Test
    public void setSieveUserQuotaShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setsieveuserquota", USER, "36"});

        assertThat(sieveProbe.getSieveQuota(USER)).isEqualTo(36);
    }

    @Test
    public void setSieveQuotaShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setsievequota", "36"});

        assertThat(sieveProbe.getSieveQuota()).isEqualTo(36);
    }


    @Test
    public void removeSieveUserQuotaShouldWork() throws Exception {
        sieveProbe.setSieveQuota(USER, 36);

        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "removesieveuserquota", USER});

        expectedException.expect(QuotaNotFoundException.class);
        sieveProbe.getSieveQuota(USER);
    }

    @Test
    public void removeSieveQuotaShouldWork() throws Exception {
        sieveProbe.setSieveQuota(36);

        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "removesievequota"});

        expectedException.expect(QuotaNotFoundException.class);
        sieveProbe.getSieveQuota();
    }

}
