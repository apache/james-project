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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class MessageIdReIndexerImplTest {
    private static final Username USERNAME = Username.of("benwa@apache.org");
    public static final MailboxPath INBOX = MailboxPath.inbox(USERNAME);

    private InMemoryMailboxManager mailboxManager;
    private ListeningMessageSearchIndex messageSearchIndex;

    private MessageIdReIndexerImpl reIndexer;
    private ReIndexerPerformer reindexerPerformer;

    @BeforeEach
    void setUp() {
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        MailboxSessionMapperFactory mailboxSessionMapperFactory = mailboxManager.getMapperFactory();
        messageSearchIndex = mock(ListeningMessageSearchIndex.class);
        reindexerPerformer = new ReIndexerPerformer(mailboxManager, messageSearchIndex, mailboxSessionMapperFactory);
        reIndexer = new MessageIdReIndexerImpl(reindexerPerformer);
    }

    @Test
    void reIndexShouldBeWellPerformed() throws Exception {
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
        MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
        ComposedMessageId createdMessage = mailboxManager.getMailbox(INBOX, systemSession)
            .appendMessage(
                MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                systemSession).getIds();

        reIndexer.reIndex(createdMessage.getMessageId()).run();

        ArgumentCaptor<MailboxMessage> messageCaptor = ArgumentCaptor.forClass(MailboxMessage.class);
        ArgumentCaptor<Mailbox> mailboxCaptor = ArgumentCaptor.forClass(Mailbox.class);

        verify(messageSearchIndex).add(any(MailboxSession.class), mailboxCaptor.capture(), messageCaptor.capture());
        verifyNoMoreInteractions(messageSearchIndex);

        assertThat(mailboxCaptor.getValue()).matches(mailbox -> mailbox.getMailboxId().equals(mailboxId));
        assertThat(messageCaptor.getValue()).matches(message -> message.getComposedMessageIdWithMetaData().getComposedMessageId().getMessageId()
            .equals(createdMessage.getMessageId()));
    }

}