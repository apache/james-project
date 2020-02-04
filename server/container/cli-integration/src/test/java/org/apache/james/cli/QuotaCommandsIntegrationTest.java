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

import java.util.Optional;

import org.apache.james.GuiceJamesServer;
import org.apache.james.MemoryJmapTestRule;
import org.apache.james.cli.util.OutputCapture;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.modules.QuotaProbesImpl;
import org.apache.james.modules.server.JMXServerModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class QuotaCommandsIntegrationTest {
    public static final String USER = "user";
    public static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("#private&" + USER, Optional.empty());
    private OutputCapture outputCapture;

    @Rule
    public MemoryJmapTestRule memoryJmap = new MemoryJmapTestRule();
    private GuiceJamesServer guiceJamesServer;
    private QuotaProbesImpl quotaProbe;

    @Before
    public void setUp() throws Exception {
        guiceJamesServer = memoryJmap.jmapServer(new JMXServerModule(),
            binder -> binder.bind(ListeningMessageSearchIndex.class).toInstance(mock(ListeningMessageSearchIndex.class)));
        guiceJamesServer.start();
        quotaProbe = guiceJamesServer.getProbe(QuotaProbesImpl.class);
        outputCapture = new OutputCapture();
    }

    @After
    public void tearDown() {
        guiceJamesServer.stop();
    }

    @Test
    public void setGlobalMaxStorageShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setglobalmaxstoragequota", "36"});

        assertThat(quotaProbe.getGlobalMaxStorage().map(QuotaSizeLimit::asLong)).contains(36L);
    }

    @Test
    public void getGlobalMaxStorageShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setglobalmaxstoragequota", "36M"});

        ServerCmd.executeAndOutputToStream(new String[] {"-h", "127.0.0.1", "-p", "9999", "getglobalmaxstoragequota"},
            outputCapture.getPrintStream());

        assertThat(outputCapture.getContent())
            .containsOnlyOnce("Global Maximum Storage Quota: 36 MiB");
    }

    @Test
    public void setGlobalMaxMessageCountShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setglobalmaxmessagecountquota", "36"});

        assertThat(quotaProbe.getGlobalMaxMessageCount().map(QuotaCountLimit::asLong)).contains(36L);
    }

    @Test
    public void getGlobalMaxMessageCountShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setglobalmaxmessagecountquota", "36"});

        ServerCmd.executeAndOutputToStream(new String[] {"-h", "127.0.0.1", "-p", "9999", "getglobalmaxmessagecountquota"},
            outputCapture.getPrintStream());

        assertThat(outputCapture.getContent())
            .containsOnlyOnce("Global Maximum message count Quota: 36");
    }

    @Test
    public void setMaxStorageShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setmaxstoragequota", QUOTA_ROOT.getValue(), "36"});

        assertThat(quotaProbe.getMaxStorage(QUOTA_ROOT.getValue()).map(QuotaSizeLimit::asLong)).contains(36L);
    }

    @Test
    public void getMaxStorageShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setmaxstoragequota", QUOTA_ROOT.getValue(), "1g"});

        ServerCmd.executeAndOutputToStream(new String[] {"-h", "127.0.0.1", "-p", "9999", "getmaxstoragequota", QUOTA_ROOT.getValue()},
            outputCapture.getPrintStream());

        assertThat(outputCapture.getContent())
            .containsOnlyOnce("Storage space allowed for Quota Root #private&user: 1 GiB");
    }

    @Test
    public void setMaxMessageCountShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setmaxmessagecountquota", QUOTA_ROOT.getValue(), "36"});

        assertThat(quotaProbe.getMaxMessageCount(QUOTA_ROOT.getValue()).map(QuotaCountLimit::asLong)).contains(36L);
    }

    @Test
    public void getMaxMessageCountShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setmaxmessagecountquota", QUOTA_ROOT.getValue(), "36"});

        ServerCmd.executeAndOutputToStream(new String[] {"-h", "127.0.0.1", "-p", "9999", "getmaxmessagecountquota", QUOTA_ROOT.getValue()},
            outputCapture.getPrintStream());

        assertThat(outputCapture.getContent())
            .containsOnlyOnce("MailboxMessage count allowed for Quota Root #private&user: 36");
    }

    @Test
    public void getStorageQuotaShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setmaxstoragequota", QUOTA_ROOT.getValue(), "36"});

        ServerCmd.executeAndOutputToStream(new String[] {"-h", "127.0.0.1", "-p", "9999", "getstoragequota", QUOTA_ROOT.getValue()},
            outputCapture.getPrintStream());

        assertThat(outputCapture.getContent())
            .containsOnlyOnce("Storage quota for #private&user is: 0 bytes / 36 bytes");
    }

    @Test
    public void getMessageCountQuotaShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "setmaxmessagecountquota", QUOTA_ROOT.getValue(), "36"});

        ServerCmd.executeAndOutputToStream(new String[] {"-h", "127.0.0.1", "-p", "9999", "getmessagecountquota", QUOTA_ROOT.getValue()},
            outputCapture.getPrintStream());

        assertThat(outputCapture.getContent())
            .containsOnlyOnce("MailboxMessage count quota for #private&user is: 0 / 36");
    }
}
