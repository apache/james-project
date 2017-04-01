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
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.store.quota.QuotaRootImpl;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.utils.MailboxManagerDefinition;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.google.inject.Inject;
import com.google.inject.multibindings.Multibinder;

public class MailboxCommandsIntegrationTest {
    public static final String USER = "user";
    public static final String MAILBOX = "mailbox";

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
    private MailboxProbeImpl mailboxProbe;

    @Before
    public void setUp() throws Exception {
        guiceJamesServer = memoryJmap.jmapServer(new JMXServerModule(),
            binder -> binder.bind(ListeningMessageSearchIndex.class).toInstance(mock(ListeningMessageSearchIndex.class)),
            binder -> Multibinder.newSetBinder(binder, MailboxManagerDefinition.class)
                .addBinding()
                .to(MemoryMailboxManagerDefinition.class));
        guiceJamesServer.start();
        mailboxProbe = guiceJamesServer.getProbe(MailboxProbeImpl.class);
    }

    @After
    public void tearDown() {
        guiceJamesServer.stop();
    }

    @Test
    public void createMailboxShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "createmailbox", MailboxConstants.USER_NAMESPACE, USER, MAILBOX});

        assertThat(mailboxProbe.listUserMailboxes(USER)).containsOnly(MAILBOX);
    }

    @Test
    public void deleteUserMailboxesShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "createmailbox", MailboxConstants.USER_NAMESPACE, USER, MAILBOX});

        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "deleteusermailboxes", USER});

        assertThat(mailboxProbe.listUserMailboxes(USER)).isEmpty();
    }

    @Test
    public void deleteMailboxeShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "createmailbox", MailboxConstants.USER_NAMESPACE, USER, MAILBOX});

        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "deletemailbox", MailboxConstants.USER_NAMESPACE, USER, MAILBOX});

        assertThat(mailboxProbe.listUserMailboxes(USER)).isEmpty();
    }
}
