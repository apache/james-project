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

package org.apache.james.mailbox.store.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.events.MailboxEvents.MailboxDeletion;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

import reactor.core.publisher.Mono;

class ListeningCurrentQuotaUpdaterTest {

    static final int SIZE = 45;
    static final MailboxId MAILBOX_ID = TestId.of(42);
    static final String BENWA = "benwa";
    static final Username USERNAME_BENWA = Username.of(BENWA);
    static final MailboxPath MAILBOX_PATH = MailboxPath.forUser(USERNAME_BENWA, "path");
    static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot(BENWA, Optional.empty());
    static final QuotaOperation QUOTA = new QuotaOperation(QUOTA_ROOT, QuotaCountUsage.count(2), QuotaSizeUsage.size(2 * SIZE));

    CurrentQuotaManager mockedCurrentQuotaManager;
    QuotaRootResolver mockedQuotaRootResolver;
    ListeningCurrentQuotaUpdater testee;

    @BeforeEach
    void setUp() {
        mockedQuotaRootResolver = mock(QuotaRootResolver.class);
        mockedCurrentQuotaManager = mock(CurrentQuotaManager.class);
        EventBus eventBus = mock(EventBus.class);
        when(eventBus.dispatch(any(Event.class), anySet())).thenReturn(Mono.empty());
        QuotaManager quotaManager = mock(QuotaManager.class);
        when(quotaManager.getQuotasReactive(eq(QUOTA_ROOT))).thenReturn(Mono.empty());
        testee = new ListeningCurrentQuotaUpdater(mockedCurrentQuotaManager, mockedQuotaRootResolver,
            eventBus, quotaManager);
    }

    @Test
    void deserializeListeningCurrentQuotaUpdaterGroup() throws Exception {
        assertThat(Group.deserialize("org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater$ListeningCurrentQuotaUpdaterGroup"))
            .isEqualTo(new ListeningCurrentQuotaUpdater.ListeningCurrentQuotaUpdaterGroup());
    }

    @Test
    void addedEventShouldIncreaseCurrentQuotaValues() throws Exception {
        Added added = mock(Added.class);
        when(added.getMailboxId()).thenReturn(MAILBOX_ID);
        when(added.getMailboxPath()).thenReturn(MAILBOX_PATH);
        when(added.getMetaData(MessageUid.of(36))).thenReturn(new MessageMetaData(MessageUid.of(36), ModSeq.first(),new Flags(), SIZE, new Date(), Optional.empty(), new DefaultMessageId(), ThreadId.fromBaseMessageId(new DefaultMessageId())));
        when(added.getMetaData(MessageUid.of(38))).thenReturn(new MessageMetaData(MessageUid.of(38), ModSeq.first(),new Flags(), SIZE, new Date(), Optional.empty(), new DefaultMessageId(), ThreadId.fromBaseMessageId(new DefaultMessageId())));
        when(added.getUids()).thenReturn(Lists.newArrayList(MessageUid.of(36), MessageUid.of(38)));
        when(added.getUsername()).thenReturn(USERNAME_BENWA);
        when(mockedQuotaRootResolver.getQuotaRootReactive(eq(MAILBOX_ID))).thenReturn(Mono.just(QUOTA_ROOT));
        when(mockedQuotaRootResolver.getQuotaRootReactive(eq(MAILBOX_PATH))).thenReturn(Mono.just(QUOTA_ROOT));
        when(mockedCurrentQuotaManager.increase(QUOTA)).thenAnswer(any -> Mono.empty());

        testee.event(added);

        verify(mockedCurrentQuotaManager).increase(QUOTA);
    }

    @Test
    void expungedEventShouldDecreaseCurrentQuotaValues() throws Exception {
        Expunged expunged = mock(Expunged.class);
        when(expunged.getMetaData(MessageUid.of(36))).thenReturn(new MessageMetaData(MessageUid.of(36), ModSeq.first(), new Flags(), SIZE, new Date(), Optional.empty(), new DefaultMessageId(), ThreadId.fromBaseMessageId(new DefaultMessageId())));
        when(expunged.getMetaData(MessageUid.of(38))).thenReturn(new MessageMetaData(MessageUid.of(38), ModSeq.first(), new Flags(), SIZE, new Date(), Optional.empty(), new DefaultMessageId(), ThreadId.fromBaseMessageId(new DefaultMessageId())));
        when(expunged.getUids()).thenReturn(Lists.newArrayList(MessageUid.of(36), MessageUid.of(38)));
        when(expunged.getMailboxId()).thenReturn(MAILBOX_ID);
        when(expunged.getUsername()).thenReturn(USERNAME_BENWA);
        when(expunged.getMailboxPath()).thenReturn(MAILBOX_PATH);
        when(mockedQuotaRootResolver.getQuotaRootReactive(eq(MAILBOX_PATH))).thenReturn(Mono.just(QUOTA_ROOT));
        when(mockedQuotaRootResolver.getQuotaRootReactive(eq(MAILBOX_ID))).thenReturn(Mono.just(QUOTA_ROOT));
        when(mockedCurrentQuotaManager.decrease(QUOTA)).thenAnswer(any -> Mono.empty());

        testee.event(expunged);

        verify(mockedCurrentQuotaManager).decrease(QUOTA);
    }
    
    @Test
    void emptyExpungedEventShouldNotTriggerDecrease() throws Exception {
        Expunged expunged = mock(Expunged.class);
        when(expunged.getUids()).thenReturn(Lists.<MessageUid>newArrayList());
        when(expunged.getMailboxId()).thenReturn(MAILBOX_ID);
        when(expunged.getUsername()).thenReturn(USERNAME_BENWA);
        when(expunged.getMailboxPath()).thenReturn(MAILBOX_PATH);
        when(mockedQuotaRootResolver.getQuotaRootReactive(eq(MAILBOX_PATH))).thenReturn(Mono.just(QUOTA_ROOT));
        when(mockedQuotaRootResolver.getQuotaRootReactive(eq(MAILBOX_ID))).thenReturn(Mono.just(QUOTA_ROOT));

        testee.event(expunged);

        verify(mockedCurrentQuotaManager, never()).decrease(any());
    }

    @Test
    void emptyAddedEventShouldNotTriggerDecrease() throws Exception {
        Added added = mock(Added.class);
        when(added.getUids()).thenReturn(Lists.<MessageUid>newArrayList());
        when(added.getMailboxId()).thenReturn(MAILBOX_ID);
        when(added.getUsername()).thenReturn(USERNAME_BENWA);
        when(added.getMailboxPath()).thenReturn(MAILBOX_PATH);
        when(mockedQuotaRootResolver.getQuotaRootReactive(eq(MAILBOX_PATH))).thenReturn(Mono.just(QUOTA_ROOT));
        when(mockedQuotaRootResolver.getQuotaRootReactive(eq(MAILBOX_ID))).thenReturn(Mono.just(QUOTA_ROOT));

        testee.event(added);

        verify(mockedCurrentQuotaManager, never()).increase(any());
    }

    @Test
    void mailboxDeletionEventShouldDecreaseCurrentQuotaValues() throws Exception {
        QuotaOperation operation = new QuotaOperation(QUOTA_ROOT, QuotaCountUsage.count(10), QuotaSizeUsage.size(5));

        MailboxDeletion deletion;
        deletion = mock(MailboxDeletion.class);
        when(deletion.getQuotaRoot()).thenReturn(QUOTA_ROOT);
        when(deletion.getDeletedMessageCount()).thenReturn(QuotaCountUsage.count(10));
        when(deletion.getTotalDeletedSize()).thenReturn(QuotaSizeUsage.size(5));
        when(deletion.getMailboxId()).thenReturn(MAILBOX_ID);
        when(deletion.getUsername()).thenReturn(USERNAME_BENWA);
        when(mockedQuotaRootResolver.getQuotaRoot(eq(MAILBOX_ID))).thenReturn(QUOTA_ROOT);
        when(mockedCurrentQuotaManager.decrease(operation)).thenAnswer(any -> Mono.empty());

        testee.event(deletion);

        verify(mockedCurrentQuotaManager).decrease(operation);
    }

    @Test
    void mailboxDeletionEventShouldDoNothingWhenEmptyMailbox() throws Exception {
        MailboxDeletion deletion = mock(MailboxDeletion.class);
        when(deletion.getQuotaRoot()).thenReturn(QUOTA_ROOT);
        when(deletion.getDeletedMessageCount()).thenReturn(QuotaCountUsage.count(0));
        when(deletion.getTotalDeletedSize()).thenReturn(QuotaSizeUsage.size(0));
        when(deletion.getMailboxId()).thenReturn(MAILBOX_ID);
        when(deletion.getUsername()).thenReturn(USERNAME_BENWA);
        when(mockedQuotaRootResolver.getQuotaRoot(eq(MAILBOX_ID))).thenReturn(QUOTA_ROOT);

        testee.event(deletion);

        verifyNoMoreInteractions(mockedCurrentQuotaManager);
    }
}
