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
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

import reactor.core.publisher.Mono;

public class ListeningCurrentQuotaUpdaterTest {

    private static final int SIZE = 45;
    private static final MailboxId MAILBOX_ID = TestId.of(42);
    private static final String BENWA = "benwa";
    private static final Username USERNAME_BENWA = Username.of(BENWA);
    private static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot(BENWA, Optional.empty());

    private StoreCurrentQuotaManager mockedCurrentQuotaManager;
    private QuotaRootResolver mockedQuotaRootResolver;
    private ListeningCurrentQuotaUpdater testee;

    @Before
    public void setUp() throws Exception {
        mockedQuotaRootResolver = mock(QuotaRootResolver.class);
        mockedCurrentQuotaManager = mock(StoreCurrentQuotaManager.class);
        EventBus eventBus = mock(EventBus.class);
        when(eventBus.dispatch(any(Event.class), anySet())).thenReturn(Mono.empty());
        testee = new ListeningCurrentQuotaUpdater(mockedCurrentQuotaManager, mockedQuotaRootResolver,
            eventBus, mock(QuotaManager.class));
    }

    @Test
    public void deserializeListeningCurrentQuotaUpdaterGroup() throws Exception {
        assertThat(Group.deserialize("org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater$ListeningCurrentQuotaUpdaterGroup"))
            .isEqualTo(new ListeningCurrentQuotaUpdater.ListeningCurrentQuotaUpdaterGroup());
    }

    @Test
    public void addedEventShouldIncreaseCurrentQuotaValues() throws Exception {
        MailboxListener.Added added = mock(MailboxListener.Added.class);
        when(added.getMailboxId()).thenReturn(MAILBOX_ID);
        when(added.getMetaData(MessageUid.of(36))).thenReturn(new MessageMetaData(MessageUid.of(36),0,new Flags(), SIZE, new Date(), new DefaultMessageId()));
        when(added.getMetaData(MessageUid.of(38))).thenReturn(new MessageMetaData(MessageUid.of(38),0,new Flags(), SIZE, new Date(), new DefaultMessageId()));
        when(added.getUids()).thenReturn(Lists.newArrayList(MessageUid.of(36), MessageUid.of(38)));
        when(added.getUsername()).thenReturn(USERNAME_BENWA);
        when(mockedQuotaRootResolver.getQuotaRoot(eq(MAILBOX_ID))).thenReturn(QUOTA_ROOT);

        testee.event(added);

        verify(mockedCurrentQuotaManager).increase(QUOTA_ROOT, 2, 2 * SIZE);
    }

    @Test
    public void expungedEventShouldDecreaseCurrentQuotaValues() throws Exception {
        MailboxListener.Expunged expunged = mock(MailboxListener.Expunged.class);
        when(expunged.getMetaData(MessageUid.of(36))).thenReturn(new MessageMetaData(MessageUid.of(36),0,new Flags(), SIZE, new Date(), new DefaultMessageId()));
        when(expunged.getMetaData(MessageUid.of(38))).thenReturn(new MessageMetaData(MessageUid.of(38),0,new Flags(), SIZE, new Date(), new DefaultMessageId()));
        when(expunged.getUids()).thenReturn(Lists.newArrayList(MessageUid.of(36), MessageUid.of(38)));
        when(expunged.getMailboxId()).thenReturn(MAILBOX_ID);
        when(expunged.getUsername()).thenReturn(USERNAME_BENWA);
        when(mockedQuotaRootResolver.getQuotaRoot(eq(MAILBOX_ID))).thenReturn(QUOTA_ROOT);

        testee.event(expunged);

        verify(mockedCurrentQuotaManager).decrease(QUOTA_ROOT, 2, 2 * SIZE);
    }
    
    @Test
    public void emptyExpungedEventShouldNotTriggerDecrease() throws Exception {
        MailboxListener.Expunged expunged = mock(MailboxListener.Expunged.class);
        when(expunged.getUids()).thenReturn(Lists.<MessageUid>newArrayList());
        when(expunged.getMailboxId()).thenReturn(MAILBOX_ID);
        when(expunged.getUsername()).thenReturn(USERNAME_BENWA);
        when(mockedQuotaRootResolver.getQuotaRoot(eq(MAILBOX_ID))).thenReturn(QUOTA_ROOT);

        testee.event(expunged);

        verify(mockedCurrentQuotaManager, never()).decrease(QUOTA_ROOT, 0, 0);
    }

    @Test
    public void emptyAddedEventShouldNotTriggerDecrease() throws Exception {
        MailboxListener.Added added = mock(MailboxListener.Added.class);
        when(added.getUids()).thenReturn(Lists.<MessageUid>newArrayList());
        when(added.getMailboxId()).thenReturn(MAILBOX_ID);
        when(added.getUsername()).thenReturn(USERNAME_BENWA);
        when(mockedQuotaRootResolver.getQuotaRoot(eq(MAILBOX_ID))).thenReturn(QUOTA_ROOT);

        testee.event(added);

        verify(mockedCurrentQuotaManager, never()).increase(QUOTA_ROOT, 0, 0);
    }

    @Test
    public void mailboxDeletionEventShouldDecreaseCurrentQuotaValues() throws Exception {
        MailboxListener.MailboxDeletion deletion = mock(MailboxListener.MailboxDeletion.class);
        when(deletion.getQuotaRoot()).thenReturn(QUOTA_ROOT);
        when(deletion.getDeletedMessageCount()).thenReturn(QuotaCountUsage.count(10));
        when(deletion.getTotalDeletedSize()).thenReturn(QuotaSizeUsage.size(5));
        when(deletion.getMailboxId()).thenReturn(MAILBOX_ID);
        when(deletion.getUsername()).thenReturn(USERNAME_BENWA);
        when(mockedQuotaRootResolver.getQuotaRoot(eq(MAILBOX_ID))).thenReturn(QUOTA_ROOT);

        testee.event(deletion);

        verify(mockedCurrentQuotaManager).decrease(QUOTA_ROOT, 10, 5);
    }

    @Test
    public void mailboxDeletionEventShouldDoNothingWhenEmptyMailbox() throws Exception {
        MailboxListener.MailboxDeletion deletion = mock(MailboxListener.MailboxDeletion.class);
        when(deletion.getQuotaRoot()).thenReturn(QUOTA_ROOT);
        when(deletion.getDeletedMessageCount()).thenReturn(QuotaCountUsage.count(0));
        when(deletion.getTotalDeletedSize()).thenReturn(QuotaSizeUsage.size(0));
        when(deletion.getMailboxId()).thenReturn(MAILBOX_ID);
        when(deletion.getUsername()).thenReturn(USERNAME_BENWA);
        when(mockedQuotaRootResolver.getQuotaRoot(eq(MAILBOX_ID))).thenReturn(QUOTA_ROOT);

        testee.event(deletion);

        verifyZeroInteractions(mockedCurrentQuotaManager);
    }
}
