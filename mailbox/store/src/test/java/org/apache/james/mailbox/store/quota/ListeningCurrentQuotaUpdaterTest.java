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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.core.User;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

public class ListeningCurrentQuotaUpdaterTest {

    private static final int SIZE = 45;
    private static final MailboxId MAILBOX_ID = TestId.of(42);
    private static final String BENWA = "benwa";
    private static final User USER_BENWA = User.fromUsername(BENWA);
    private static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot(BENWA, Optional.empty());

    private StoreCurrentQuotaManager mockedCurrentQuotaManager;
    private QuotaRootResolver mockedQuotaRootResolver;
    private ListeningCurrentQuotaUpdater testee;

    @Before
    public void setUp() throws Exception {
        mockedQuotaRootResolver = mock(QuotaRootResolver.class);
        mockedCurrentQuotaManager = mock(StoreCurrentQuotaManager.class);
        testee = new ListeningCurrentQuotaUpdater(mockedCurrentQuotaManager, mockedQuotaRootResolver,
            mock(MailboxEventDispatcher.class), mock(QuotaManager.class));
    }

    @Test
    public void addedEventShouldIncreaseCurrentQuotaValues() throws Exception {
        MailboxListener.Added added = mock(MailboxListener.Added.class);
        when(added.getMailboxId()).thenReturn(MAILBOX_ID);
        when(added.getMetaData(MessageUid.of(36))).thenReturn(new MessageMetaData(MessageUid.of(36),0,new Flags(), SIZE, new Date(), new DefaultMessageId()));
        when(added.getMetaData(MessageUid.of(38))).thenReturn(new MessageMetaData(MessageUid.of(38),0,new Flags(), SIZE, new Date(), new DefaultMessageId()));
        when(added.getUids()).thenReturn(Lists.newArrayList(MessageUid.of(36), MessageUid.of(38)));
        when(added.getUser()).thenReturn(USER_BENWA);
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
        when(expunged.getUser()).thenReturn(USER_BENWA);
        when(mockedQuotaRootResolver.getQuotaRoot(eq(MAILBOX_ID))).thenReturn(QUOTA_ROOT);

        testee.event(expunged);

        verify(mockedCurrentQuotaManager).decrease(QUOTA_ROOT, 2, 2 * SIZE);
    }
    
    @Test
    public void emptyExpungedEventShouldNotTriggerDecrease() throws Exception {
        MailboxListener.Expunged expunged = mock(MailboxListener.Expunged.class);
        when(expunged.getUids()).thenReturn(Lists.<MessageUid>newArrayList());
        when(expunged.getMailboxId()).thenReturn(MAILBOX_ID);
        when(expunged.getUser()).thenReturn(USER_BENWA);
        when(mockedQuotaRootResolver.getQuotaRoot(eq(MAILBOX_ID))).thenReturn(QUOTA_ROOT);

        testee.event(expunged);

        verify(mockedCurrentQuotaManager, never()).decrease(QUOTA_ROOT, 0, 0);
    }

    @Test
    public void emptyAddedEventShouldNotTriggerDecrease() throws Exception {
        MailboxListener.Added added = mock(MailboxListener.Added.class);
        when(added.getUids()).thenReturn(Lists.<MessageUid>newArrayList());
        when(added.getMailboxId()).thenReturn(MAILBOX_ID);
        when(added.getUser()).thenReturn(USER_BENWA);
        when(mockedQuotaRootResolver.getQuotaRoot(eq(MAILBOX_ID))).thenReturn(QUOTA_ROOT);

        testee.event(added);

        verify(mockedCurrentQuotaManager, never()).increase(QUOTA_ROOT, 0, 0);
    }

    @Test
    public void mailboxDeletionEventShouldDecreaseCurrentQuotaValues() throws Exception {
        MailboxListener.MailboxDeletion deletion = mock(MailboxListener.MailboxDeletion.class);
        when(deletion.getQuotaRoot()).thenReturn(QUOTA_ROOT);
        when(deletion.getDeletedMessageCount()).thenReturn(QuotaCount.count(10));
        when(deletion.getTotalDeletedSize()).thenReturn(QuotaSize.size(5));
        when(deletion.getMailboxId()).thenReturn(MAILBOX_ID);
        when(deletion.getUser()).thenReturn(USER_BENWA);
        when(mockedQuotaRootResolver.getQuotaRoot(eq(MAILBOX_ID))).thenReturn(QUOTA_ROOT);

        testee.event(deletion);

        verify(mockedCurrentQuotaManager).decrease(QUOTA_ROOT, 10, 5);
    }

    @Test
    public void mailboxDeletionEventShouldDoNothingWhenEmptyMailbox() throws Exception {
        MailboxListener.MailboxDeletion deletion = mock(MailboxListener.MailboxDeletion.class);
        when(deletion.getQuotaRoot()).thenReturn(QUOTA_ROOT);
        when(deletion.getDeletedMessageCount()).thenReturn(QuotaCount.count(0));
        when(deletion.getTotalDeletedSize()).thenReturn(QuotaSize.size(0));
        when(deletion.getMailboxId()).thenReturn(MAILBOX_ID);
        when(deletion.getUser()).thenReturn(USER_BENWA);
        when(mockedQuotaRootResolver.getQuotaRoot(eq(MAILBOX_ID))).thenReturn(QUOTA_ROOT);

        testee.event(deletion);

        verifyZeroInteractions(mockedCurrentQuotaManager);
    }
}
