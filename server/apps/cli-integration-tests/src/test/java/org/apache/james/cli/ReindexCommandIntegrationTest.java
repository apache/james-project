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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.core.Username;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.indexer.ReIndexer.RunningOptions;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.task.MemoryReferenceTask;
import org.apache.james.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.name.Names;

class ReindexCommandIntegrationTest {
    private static final String USER = "user";
    private ReIndexer reIndexer = mock(ReIndexer.class);

    @RegisterExtension
    JamesServerExtension memoryJmap = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
        .server(conf -> MemoryJamesServerMain.createServer(conf)
            .overrideWith(new JMXServerModule()).overrideWith(
                binder -> binder.bind(ReIndexer.class).annotatedWith(Names.named("reindexer")).toInstance(reIndexer),
                binder -> binder.bind(ListeningMessageSearchIndex.class).toInstance(mock(ListeningMessageSearchIndex.class))))
        .build();

    @BeforeEach
    void setUp() throws Exception {
        when(reIndexer.reIndex(any(RunningOptions.class))).thenReturn(new MemoryReferenceTask(() -> Task.Result.COMPLETED));
        when(reIndexer.reIndex(any(MailboxPath.class), any(RunningOptions.class))).thenReturn(new MemoryReferenceTask(() -> Task.Result.COMPLETED));
    }

    @Test
    void reindexAllShouldWork() throws Exception {
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "reindexall"});

        verify(reIndexer).reIndex(ReIndexer.RunningOptions.DEFAULT);
    }

    @Test
    void reindexMailboxShouldWork() throws Exception {
        String mailbox = "mailbox";
        ServerCmd.doMain(new String[] {"-h", "127.0.0.1", "-p", "9999", "reindexmailbox", MailboxConstants.USER_NAMESPACE, USER, mailbox});

        verify(reIndexer).reIndex(MailboxPath.forUser(Username.of(USER), mailbox), ReIndexer.RunningOptions.DEFAULT);
    }

}
