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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

public class ListeningCurrentQuotaUpdaterTest {

    public static final int SIZE = 45;
    public static final MailboxPath MAILBOX_PATH = MailboxPath.forUser("benwa", "INBOX");
    public static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("benwa", Optional.empty());

    private StoreCurrentQuotaManager mockedCurrentQuotaManager;
    private QuotaRootResolver mockedQuotaRootResolver;
    private ListeningCurrentQuotaUpdater testee;

    @Before
    public void setUp() throws Exception {
        mockedQuotaRootResolver = mock(QuotaRootResolver.class);
        mockedCurrentQuotaManager = mock(StoreCurrentQuotaManager.class);
        testee = new ListeningCurrentQuotaUpdater(mockedCurrentQuotaManager, mockedQuotaRootResolver);
    }

    @Test
    public void addedEventShouldIncreaseCurrentQuotaValues() throws Exception {
        MailboxListener.Added added = mock(MailboxListener.Added.class);
        when(added.getMetaData(MessageUid.of(36))).thenReturn(new SimpleMessageMetaData(MessageUid.of(36),0,new Flags(), SIZE, new Date(), new DefaultMessageId()));
        when(added.getMetaData(MessageUid.of(38))).thenReturn(new SimpleMessageMetaData(MessageUid.of(38),0,new Flags(), SIZE, new Date(), new DefaultMessageId()));
        when(added.getUids()).thenReturn(Lists.newArrayList(MessageUid.of(36), MessageUid.of(38)));
        when(added.getMailboxPath()).thenReturn(MAILBOX_PATH);
        when(mockedQuotaRootResolver.getQuotaRoot(MAILBOX_PATH)).thenReturn(QUOTA_ROOT);
        testee.event(added);
        verify(mockedCurrentQuotaManager).increase(QUOTA_ROOT, 2, 2 * SIZE);
    }

    @Test
    public void expungedEventShouldDecreaseCurrentQuotaValues() throws Exception {
        MailboxListener.Expunged expunged = mock(MailboxListener.Expunged.class);
        when(expunged.getMetaData(MessageUid.of(36))).thenReturn(new SimpleMessageMetaData(MessageUid.of(36),0,new Flags(), SIZE, new Date(), new DefaultMessageId()));
        when(expunged.getMetaData(MessageUid.of(38))).thenReturn(new SimpleMessageMetaData(MessageUid.of(38),0,new Flags(), SIZE, new Date(), new DefaultMessageId()));
        when(expunged.getUids()).thenReturn(Lists.newArrayList(MessageUid.of(36), MessageUid.of(38)));
        when(expunged.getMailboxPath()).thenReturn(MAILBOX_PATH);
        when(mockedQuotaRootResolver.getQuotaRoot(MAILBOX_PATH)).thenReturn(QUOTA_ROOT);
        testee.event(expunged);
        verify(mockedCurrentQuotaManager).decrease(QUOTA_ROOT, 2, 2 * SIZE);
    }
    
    @Test
    public void emptyExpungedEventShouldNotTriggerDecrease() throws Exception {
        MailboxListener.Expunged expunged = mock(MailboxListener.Expunged.class);
        when(expunged.getUids()).thenReturn(Lists.<MessageUid>newArrayList());
        when(expunged.getMailboxPath()).thenReturn(MAILBOX_PATH);
        when(mockedQuotaRootResolver.getQuotaRoot(MAILBOX_PATH)).thenReturn(QUOTA_ROOT);
        testee.event(expunged);
        verify(mockedCurrentQuotaManager, never()).decrease(QUOTA_ROOT, 0, 0);
    }

    @Test
    public void emptyAddedEventShouldNotTriggerDecrease() throws Exception {
        MailboxListener.Added added = mock(MailboxListener.Added.class);
        when(added.getUids()).thenReturn(Lists.<MessageUid>newArrayList());
        when(added.getMailboxPath()).thenReturn(MAILBOX_PATH);
        when(mockedQuotaRootResolver.getQuotaRoot(MAILBOX_PATH)).thenReturn(QUOTA_ROOT);
        testee.event(added);
        verify(mockedCurrentQuotaManager, never()).increase(QUOTA_ROOT, 0, 0);
    }

}
