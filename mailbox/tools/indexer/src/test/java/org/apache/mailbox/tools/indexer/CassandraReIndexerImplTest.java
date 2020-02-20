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

package org.apache.mailbox.tools.indexer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxManagerProvider;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.base.Strings;

public class CassandraReIndexerImplTest {
    private static final Username USERNAME = Username.of("benwa@apache.org");
    public static final MailboxPath INBOX = MailboxPath.inbox(USERNAME);
    private CassandraMailboxManager mailboxManager;
    private ListeningMessageSearchIndex messageSearchIndex;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MailboxAggregateModule.MODULE_WITH_QUOTA);

    private ReIndexer reIndexer;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        mailboxManager = CassandraMailboxManagerProvider.provideMailboxManager(cassandra, PreDeletionHooks.NO_PRE_DELETION_HOOK);
        MailboxSessionMapperFactory mailboxSessionMapperFactory = mailboxManager.getMapperFactory();
        messageSearchIndex = mock(ListeningMessageSearchIndex.class);
        reIndexer = new ReIndexerImpl(new ReIndexerPerformer(mailboxManager, messageSearchIndex, mailboxSessionMapperFactory),
            mailboxManager, mailboxSessionMapperFactory);
    }

    @Test
    void reIndexShouldBeWellPerformed() throws Exception {
        // Given a mailbox with 1000 messages * 150 KB
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(INBOX, systemSession);

        byte[] bigBody = (Strings.repeat("header: value\r\n", 10000) + "\r\nbody").getBytes(StandardCharsets.UTF_8);

        int threadCount = 10;
        int operationCount = 100;
        MessageManager mailbox = mailboxManager.getMailbox(INBOX, systemSession);
        ConcurrentTestRunner.builder()
            .operation((a, b) -> mailbox
                .appendMessage(
                    MessageManager.AppendCommand.builder().build(bigBody),
                    systemSession))
            .threadCount(threadCount)
            .operationCount(operationCount)
            .runSuccessfullyWithin(Duration.ofMinutes(10));

        // When We re-index
        reIndexer.reIndex(INBOX).run();

        // The indexer is called for each message
        verify(messageSearchIndex).deleteAll(any(MailboxSession.class), any(MailboxId.class));
        verify(messageSearchIndex, times(threadCount * operationCount))
            .add(any(MailboxSession.class), any(Mailbox.class),any(MailboxMessage.class));
        verifyNoMoreInteractions(messageSearchIndex);
    }
}
