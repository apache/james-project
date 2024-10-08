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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.indexer.ReIndexer.RunningOptions;
import org.apache.james.mailbox.inmemory.InMemoryId;
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

import reactor.core.publisher.Mono;

public class ReIndexerImplTest {

    private static final Username USERNAME = Username.of("benwa@apache.org");
    public static final MailboxPath INBOX = MailboxPath.inbox(USERNAME);
    private InMemoryMailboxManager mailboxManager;
    private ListeningMessageSearchIndex messageSearchIndex;

    private ReIndexer reIndexer;

    @BeforeEach
    void setUp() {
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        MailboxSessionMapperFactory mailboxSessionMapperFactory = mailboxManager.getMapperFactory();
        messageSearchIndex = mock(ListeningMessageSearchIndex.class);
        when(messageSearchIndex.add(any(), any(), any())).thenReturn(Mono.empty());
        when(messageSearchIndex.deleteAll(any(), any())).thenReturn(Mono.empty());
        reIndexer = new ReIndexerImpl(new ReIndexerPerformer(mailboxManager, messageSearchIndex, mailboxSessionMapperFactory),
            mailboxManager, mailboxSessionMapperFactory);
    }

    @Test
    void reIndexMailboxPathShouldCallMessageSearchIndex() throws Exception {
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
        MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
        ComposedMessageId createdMessage = mailboxManager.getMailbox(INBOX, systemSession)
            .appendMessage(
                MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                systemSession).getId();

        reIndexer.reIndex(INBOX, RunningOptions.DEFAULT).run();

        ArgumentCaptor<MailboxMessage> messageCaptor = ArgumentCaptor.forClass(MailboxMessage.class);
        ArgumentCaptor<MailboxId> mailboxCaptor1 = ArgumentCaptor.forClass(MailboxId.class);
        ArgumentCaptor<Mailbox> mailboxCaptor2 = ArgumentCaptor.forClass(Mailbox.class);

        verify(messageSearchIndex).deleteAll(any(MailboxSession.class), mailboxCaptor1.capture());
        verify(messageSearchIndex).add(any(MailboxSession.class), mailboxCaptor2.capture(), messageCaptor.capture());
        verify(messageSearchIndex).postReindexing();
        verifyNoMoreInteractions(messageSearchIndex);

        assertThat(mailboxCaptor1.getValue()).satisfies(capturedMailboxId -> assertThat(capturedMailboxId).isEqualTo(mailboxId));
        assertThat(mailboxCaptor2.getValue()).satisfies(mailbox -> assertThat(mailbox.getMailboxId()).isEqualTo(mailboxId));
        assertThat(messageCaptor.getValue()).satisfies(message -> {
            assertThat(message.getMailboxId()).isEqualTo(mailboxId);
            assertThat(message.getUid()).isEqualTo(createdMessage.getUid());
        });
    }

    @Test
    void reIndexAllShouldCallMessageSearchIndex() throws Exception {
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
        MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
        ComposedMessageId createdMessage = mailboxManager.getMailbox(INBOX, systemSession)
            .appendMessage(
                MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                systemSession).getId();

        reIndexer.reIndex(RunningOptions.DEFAULT).run();
        ArgumentCaptor<MailboxMessage> messageCaptor = ArgumentCaptor.forClass(MailboxMessage.class);
        ArgumentCaptor<MailboxId> mailboxCaptor1 = ArgumentCaptor.forClass(MailboxId.class);
        ArgumentCaptor<Mailbox> mailboxCaptor2 = ArgumentCaptor.forClass(Mailbox.class);

        verify(messageSearchIndex).deleteAll(any(MailboxSession.class), mailboxCaptor1.capture());
        verify(messageSearchIndex).add(any(MailboxSession.class), mailboxCaptor2.capture(), messageCaptor.capture());
        verify(messageSearchIndex).postReindexing();
        verifyNoMoreInteractions(messageSearchIndex);

        assertThat(mailboxCaptor1.getValue()).satisfies(capturedMailboxId -> assertThat(capturedMailboxId).isEqualTo(mailboxId));
        assertThat(mailboxCaptor2.getValue()).satisfies(mailbox -> assertThat(mailbox.getMailboxId()).isEqualTo(mailboxId));
        assertThat(messageCaptor.getValue()).satisfies(message -> {
            assertThat(message.getMailboxId()).isEqualTo(mailboxId);
            assertThat(message.getUid()).isEqualTo(createdMessage.getUid());
        });
    }

    @Test
    void userReIndexShouldCallMessageSearchIndex() throws Exception {
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
        MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
        ComposedMessageId createdMessage = mailboxManager.getMailbox(INBOX, systemSession)
            .appendMessage(
                MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                systemSession).getId();

        reIndexer.reIndex(USERNAME, RunningOptions.DEFAULT).run();
        ArgumentCaptor<MailboxMessage> messageCaptor = ArgumentCaptor.forClass(MailboxMessage.class);
        ArgumentCaptor<MailboxId> mailboxCaptor1 = ArgumentCaptor.forClass(MailboxId.class);
        ArgumentCaptor<Mailbox> mailboxCaptor2 = ArgumentCaptor.forClass(Mailbox.class);

        verify(messageSearchIndex).deleteAll(any(MailboxSession.class), mailboxCaptor1.capture());
        verify(messageSearchIndex).add(any(MailboxSession.class), mailboxCaptor2.capture(), messageCaptor.capture());
        verify(messageSearchIndex).postReindexing();
        verifyNoMoreInteractions(messageSearchIndex);

        assertThat(mailboxCaptor1.getValue()).satisfies(capturedMailboxId -> assertThat(capturedMailboxId).isEqualTo(mailboxId));
        assertThat(mailboxCaptor2.getValue()).satisfies(mailbox -> assertThat(mailbox.getMailboxId()).isEqualTo(mailboxId));
        assertThat(messageCaptor.getValue()).satisfies(message -> {
            assertThat(message.getMailboxId()).isEqualTo(mailboxId);
            assertThat(message.getUid()).isEqualTo(createdMessage.getUid());
        });
    }

    @Test
    void messageReIndexShouldCallMessageSearchIndex() throws Exception {
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
        MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
        ComposedMessageId createdMessage = mailboxManager.getMailbox(INBOX, systemSession)
            .appendMessage(
                MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                systemSession).getId();

        reIndexer.reIndex(INBOX, createdMessage.getUid()).run();
        ArgumentCaptor<MailboxMessage> messageCaptor = ArgumentCaptor.forClass(MailboxMessage.class);
        ArgumentCaptor<Mailbox> mailboxCaptor = ArgumentCaptor.forClass(Mailbox.class);

        verify(messageSearchIndex).add(any(MailboxSession.class), mailboxCaptor.capture(), messageCaptor.capture());
        verifyNoMoreInteractions(messageSearchIndex);

        assertThat(mailboxCaptor.getValue()).satisfies(mailbox -> assertThat(mailbox.getMailboxId()).isEqualTo(mailboxId));
        assertThat(messageCaptor.getValue()).satisfies(message -> {
            assertThat(message.getMailboxId()).isEqualTo(mailboxId);
            assertThat(message.getUid()).isEqualTo(createdMessage.getUid());
        });
    }

    @Test
    void messageReIndexUsingMailboxIdShouldCallMessageSearchIndex() throws Exception {
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
        MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
        ComposedMessageId createdMessage = mailboxManager.getMailbox(INBOX, systemSession)
            .appendMessage(
                MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                systemSession).getId();

        reIndexer.reIndex(mailboxId, createdMessage.getUid()).run();
        ArgumentCaptor<MailboxMessage> messageCaptor = ArgumentCaptor.forClass(MailboxMessage.class);
        ArgumentCaptor<Mailbox> mailboxCaptor = ArgumentCaptor.forClass(Mailbox.class);

        verify(messageSearchIndex).add(any(MailboxSession.class), mailboxCaptor.capture(), messageCaptor.capture());
        verifyNoMoreInteractions(messageSearchIndex);

        assertThat(mailboxCaptor.getValue()).satisfies(mailbox -> assertThat(mailbox.getMailboxId()).isEqualTo(mailboxId));
        assertThat(messageCaptor.getValue()).satisfies(message -> {
            assertThat(message.getMailboxId()).isEqualTo(mailboxId);
            assertThat(message.getUid()).isEqualTo(createdMessage.getUid());
        });
    }

    @Test
    void messageReIndexUsingMailboxIdShouldDoNothingWhenUidNotFound() throws Exception {
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
        MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
        MessageUid uid = MessageUid.of(36);

        reIndexer.reIndex(mailboxId, uid).run();

        verifyNoMoreInteractions(messageSearchIndex);
    }

    @Test
    void messageReIndexUsingMailboxIdShouldFailWhenMailboxNotFound() {
        MailboxId mailboxId = InMemoryId.of(42);
        MessageUid uid = MessageUid.of(36);

        assertThatThrownBy(() -> reIndexer.reIndex(mailboxId, uid))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void mailboxIdReIndexShouldCallMessageSearchIndex() throws Exception {
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
        MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
        ComposedMessageId createdMessage = mailboxManager.getMailbox(INBOX, systemSession)
            .appendMessage(
                MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                systemSession).getId();

        reIndexer.reIndex(mailboxId, RunningOptions.DEFAULT).run();
        ArgumentCaptor<MailboxMessage> messageCaptor = ArgumentCaptor.forClass(MailboxMessage.class);
        ArgumentCaptor<MailboxId> mailboxIdCaptor = ArgumentCaptor.forClass(MailboxId.class);
        ArgumentCaptor<Mailbox> mailboxCaptor = ArgumentCaptor.forClass(Mailbox.class);

        verify(messageSearchIndex).deleteAll(any(MailboxSession.class), mailboxIdCaptor.capture());
        verify(messageSearchIndex).add(any(MailboxSession.class), mailboxCaptor.capture(), messageCaptor.capture());
        verify(messageSearchIndex).postReindexing();
        verifyNoMoreInteractions(messageSearchIndex);

        assertThat(mailboxCaptor.getValue()).satisfies(mailbox -> assertThat(mailbox.getMailboxId()).isEqualTo(mailboxId));
        assertThat(mailboxIdCaptor.getValue()).satisfies(capturedMailboxId -> assertThat(capturedMailboxId).isEqualTo(mailboxId));
        assertThat(messageCaptor.getValue()).satisfies(message -> {
            assertThat(message.getMailboxId()).isEqualTo(mailboxId);
            assertThat(message.getUid()).isEqualTo(createdMessage.getUid());
        });
    }

    @Test
    void deltaReindexShouldUpdateMissingMessages() throws Exception {
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
        MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();
        ComposedMessageId createdMessage = mailboxManager.getMailbox(INBOX, systemSession)
            .appendMessage(
                MessageManager.AppendCommand.builder().build("header: value\r\n\r\nbody"),
                systemSession).getId();
        when(messageSearchIndex.retrieveIndexedFlags(any(), any())).thenReturn(Mono.empty());
        when(messageSearchIndex.delete(any(), any(), any())).thenReturn(Mono.empty());

        reIndexer.reIndex(mailboxId,
            RunningOptions.builder()
                .mode(RunningOptions.Mode.FIX_OUTDATED)
                .build())
            .run();
        ArgumentCaptor<MailboxMessage> messageCaptor = ArgumentCaptor.forClass(MailboxMessage.class);
        ArgumentCaptor<Mailbox> mailboxCaptor = ArgumentCaptor.forClass(Mailbox.class);

        verify(messageSearchIndex).retrieveIndexedFlags(any(), any());
        verify(messageSearchIndex).add(any(MailboxSession.class), mailboxCaptor.capture(), messageCaptor.capture());
        verify(messageSearchIndex).postReindexing();
        verifyNoMoreInteractions(messageSearchIndex);

        assertThat(mailboxCaptor.getValue()).satisfies(mailbox -> assertThat(mailbox.getMailboxId()).isEqualTo(mailboxId));
        assertThat(messageCaptor.getValue()).satisfies(message -> {
            assertThat(message.getMailboxId()).isEqualTo(mailboxId);
            assertThat(message.getUid()).isEqualTo(createdMessage.getUid());
        });
    }

    @Test
    void mailboxIdReIndexShouldOnlyDropSearchIndexWhenEmptyMailbox() throws Exception {
        MailboxSession systemSession = mailboxManager.createSystemSession(USERNAME);
        MailboxId mailboxId = mailboxManager.createMailbox(INBOX, systemSession).get();

        reIndexer.reIndex(mailboxId, RunningOptions.DEFAULT).run();
        ArgumentCaptor<MailboxId> mailboxCaptor = ArgumentCaptor.forClass(MailboxId.class);

        verify(messageSearchIndex).deleteAll(any(MailboxSession.class), mailboxCaptor.capture());
        verify(messageSearchIndex).postReindexing();
        verifyNoMoreInteractions(messageSearchIndex);

        assertThat(mailboxCaptor.getValue()).satisfies(capturedMailboxId -> assertThat(capturedMailboxId).isEqualTo(mailboxId));
    }

    @Test
    void mailboxIdReIndexShouldFailWhenMailboxNotFound() {
        MailboxId mailboxId = InMemoryId.of(42);

        assertThatThrownBy(() -> reIndexer.reIndex(mailboxId, RunningOptions.DEFAULT))
            .isInstanceOf(MailboxNotFoundException.class);
    }
}
