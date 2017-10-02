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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.apache.james.GuiceJamesServer;
import org.apache.james.MemoryJmapTestRule;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.modules.server.JMXServerModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.google.inject.name.Names;

public class ReindexCommandIntegrationTest {
    public static final String USER = "user";
    private ReIndexer reIndexer;

    @Rule
    public MemoryJmapTestRule memoryJmap = new MemoryJmapTestRule();
    private GuiceJamesServer guiceJamesServer;

    @Before
    public void setUp() throws Exception {
        reIndexer = mock(ReIndexer.class);
        guiceJamesServer = memoryJmap.jmapServer(new JMXServerModule(),
            binder -> binder.bind(ListeningMessageSearchIndex.class).toInstance(mock(ListeningMessageSearchIndex.class)))
            .overrideWith(binder -> binder.bind(ReIndexer.class)
                .annotatedWith(Names.named("reindexer")).toInstance(reIndexer));
        guiceJamesServer.start();
    }

    @After
    public void tearDown() {
        guiceJamesServer.stop();
    }

    @Test
    public void reindexAllShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "reindexall"});

        verify(reIndexer).reIndex();
    }

    @Test
    public void reindexMailboxShouldWork() throws Exception {
        String mailbox = "mailbox";
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "reindexmailbox", MailboxConstants.USER_NAMESPACE, USER, mailbox});

        verify(reIndexer).reIndex(MailboxPath.forUser(USER, mailbox));
    }

}
