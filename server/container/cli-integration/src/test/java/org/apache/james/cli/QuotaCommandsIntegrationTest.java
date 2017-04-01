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
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.store.quota.QuotaRootImpl;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.modules.QuotaProbesImpl;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.utils.MailboxManagerDefinition;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.google.inject.Inject;
import com.google.inject.multibindings.Multibinder;

public class QuotaCommandsIntegrationTest {
    public static final String USER = "user";
    public static final QuotaRoot QUOTA_ROOT = QuotaRootImpl.quotaRoot("#private&" + USER);

    @Singleton
    private static class MemoryMailboxManagerDefinition extends MailboxManagerDefinition {
        @Inject
        private MemoryMailboxManagerDefinition(InMemoryMailboxManager manager) {
            super("memory-mailboxmanager", manager);
        }
    }

    @Rule
    public MemoryJmapTestRule memoryJmap = new MemoryJmapTestRule();
    private GuiceJamesServer guiceJamesServer;
    private QuotaProbesImpl quotaProbe;

    @Before
    public void setUp() throws Exception {
        guiceJamesServer = memoryJmap.jmapServer(new JMXServerModule(),
            binder -> binder.bind(ListeningMessageSearchIndex.class).toInstance(mock(ListeningMessageSearchIndex.class)),
            binder -> Multibinder.newSetBinder(binder, MailboxManagerDefinition.class)
                .addBinding()
                .to(MemoryMailboxManagerDefinition.class));
        guiceJamesServer.start();
        quotaProbe = guiceJamesServer.getProbe(QuotaProbesImpl.class);
    }

    @After
    public void tearDown() {
        guiceJamesServer.stop();
    }

    @Test
    public void setDefaultMaxStorageShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setdefaultmaxstoragequota", "36"});

        assertThat(quotaProbe.getDefaultMaxStorage()).isEqualTo(36);
    }

    @Test
    public void setDefaultMaxMessageCountShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setdefaultmaxmessagecountquota", "36"});

        assertThat(quotaProbe.getDefaultMaxMessageCount()).isEqualTo(36);
    }

    @Test
    public void setMaxStorageShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setmaxstoragequota", QUOTA_ROOT.getValue(), "36"});

        assertThat(quotaProbe.getMaxStorage(QUOTA_ROOT.getValue())).isEqualTo(36);
    }

    @Test
    public void setMaxMessageCountShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setmaxmessagecountquota", QUOTA_ROOT.getValue(), "36"});

        assertThat(quotaProbe.getMaxMessageCount(QUOTA_ROOT.getValue())).isEqualTo(36);
    }

}
