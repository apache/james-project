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

import com.google.common.collect.Lists;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.mail.Flags;
import java.util.Date;
import java.util.List;

public class ListeningCurrentQuotaUpdaterTest {

    public static final int SIZE = 45;
    public static final MailboxPath MAILBOX_PATH = new MailboxPath("#private", "benwa", "INBOX");
    public static final QuotaRoot QUOTA_ROOT = QuotaRootImpl.quotaRoot("benwa");

    private StoreCurrentQuotaManager mockedCurrentQuotaManager;
    private QuotaRootResolver mockedQuotaRootResolver;
    private ListeningCurrentQuotaUpdater testee;

    @Before
    public void setUp() throws Exception {
        mockedQuotaRootResolver = mock(QuotaRootResolver.class);
        mockedCurrentQuotaManager = mock(StoreCurrentQuotaManager.class);
        testee = new ListeningCurrentQuotaUpdater();
        testee.setCurrentQuotaManager(mockedCurrentQuotaManager);
        testee.setQuotaRootResolver(mockedQuotaRootResolver);
    }

    @Test
    public void addedEventShouldIncreaseCurrentQuotaValues() throws Exception {
        MailboxListener.Added added = mock(MailboxListener.Added.class);
        when(added.getMetaData(36)).thenAnswer(new Answer<MessageMetaData>() {
            @Override
            public MessageMetaData answer(InvocationOnMock invocationOnMock) throws Throwable {
                return new SimpleMessageMetaData(36,0,new Flags(), SIZE, new Date());
            }
        });
        when(added.getMetaData(38)).thenAnswer(new Answer<MessageMetaData>() {
            @Override
            public MessageMetaData answer(InvocationOnMock invocationOnMock) throws Throwable {
                return new SimpleMessageMetaData(38,0,new Flags(), SIZE, new Date());
            }
        });
        when(added.getUids()).thenAnswer(new Answer<List<Long>>() {
            @Override
            public List<Long> answer(InvocationOnMock invocationOnMock) throws Throwable {
                return Lists.newArrayList(36L, 38L);
            }
        });
        when(added.getMailboxPath()).thenAnswer(new Answer<MailboxPath>() {
            @Override
            public MailboxPath answer(InvocationOnMock invocationOnMock) throws Throwable {
                return MAILBOX_PATH;
            }
        });
        when(mockedQuotaRootResolver.getQuotaRoot(MAILBOX_PATH)).thenAnswer(new Answer<QuotaRoot>() {
            @Override
            public QuotaRoot answer(InvocationOnMock invocationOnMock) throws Throwable {
                return QUOTA_ROOT;
            }
        });
        testee.event(added);
        verify(mockedCurrentQuotaManager).increase(QUOTA_ROOT, 2, 2 * SIZE);
    }

    @Test
    public void expungedEventShouldDecreaseCurrentQuotaValues() throws Exception {
        MailboxListener.Expunged expunged = mock(MailboxListener.Expunged.class);
        when(expunged.getMetaData(36)).thenAnswer(new Answer<MessageMetaData>() {
            @Override
            public MessageMetaData answer(InvocationOnMock invocationOnMock) throws Throwable {
                return new SimpleMessageMetaData(36,0,new Flags(), SIZE, new Date());
            }
        });
        when(expunged.getMetaData(38)).thenAnswer(new Answer<MessageMetaData>() {
            @Override
            public MessageMetaData answer(InvocationOnMock invocationOnMock) throws Throwable {
                return new SimpleMessageMetaData(38,0,new Flags(), SIZE, new Date());
            }
        });
        when(expunged.getUids()).thenAnswer(new Answer<List<Long>>() {
            @Override
            public List<Long> answer(InvocationOnMock invocationOnMock) throws Throwable {
                return Lists.newArrayList(36L, 38L);
            }
        });
        when(expunged.getMailboxPath()).thenAnswer(new Answer<MailboxPath>() {
            @Override
            public MailboxPath answer(InvocationOnMock invocationOnMock) throws Throwable {
                return MAILBOX_PATH;
            }
        });
        when(mockedQuotaRootResolver.getQuotaRoot(MAILBOX_PATH)).thenAnswer(new Answer<QuotaRoot>() {
            @Override
            public QuotaRoot answer(InvocationOnMock invocationOnMock) throws Throwable {
                return QUOTA_ROOT;
            }
        });
        testee.event(expunged);
        verify(mockedCurrentQuotaManager).decrease(QUOTA_ROOT, 2, 2 * SIZE);
    }
    @Test
    public void emptyExpungedEventShouldNotTriggerDecrease() throws Exception {
        MailboxListener.Expunged expunged = mock(MailboxListener.Expunged.class);
        when(expunged.getUids()).thenAnswer(new Answer<List<Long>>() {
            @Override
            public List<Long> answer(InvocationOnMock invocationOnMock) throws Throwable {
                return Lists.newArrayList();
            }
        });
        when(expunged.getMailboxPath()).thenAnswer(new Answer<MailboxPath>() {
            @Override
            public MailboxPath answer(InvocationOnMock invocationOnMock) throws Throwable {
                return MAILBOX_PATH;
            }
        });
        when(mockedQuotaRootResolver.getQuotaRoot(MAILBOX_PATH)).thenAnswer(new Answer<QuotaRoot>() {
            @Override
            public QuotaRoot answer(InvocationOnMock invocationOnMock) throws Throwable {
                return QUOTA_ROOT;
            }
        });
        testee.event(expunged);
        verify(mockedCurrentQuotaManager, never()).decrease(QUOTA_ROOT, 0, 0);
    }

    @Test
    public void emptyAddedEventShouldNotTriggerDecrease() throws Exception {
        MailboxListener.Added added = mock(MailboxListener.Added.class);
        when(added.getUids()).thenAnswer(new Answer<List<Long>>() {
            @Override
            public List<Long> answer(InvocationOnMock invocationOnMock) throws Throwable {
                return Lists.newArrayList();
            }
        });
        when(added.getMailboxPath()).thenAnswer(new Answer<MailboxPath>() {
            @Override
            public MailboxPath answer(InvocationOnMock invocationOnMock) throws Throwable {
                return MAILBOX_PATH;
            }
        });
        when(mockedQuotaRootResolver.getQuotaRoot(MAILBOX_PATH)).thenAnswer(new Answer<QuotaRoot>() {
            @Override
            public QuotaRoot answer(InvocationOnMock invocationOnMock) throws Throwable {
                return QUOTA_ROOT;
            }
        });
        testee.event(added);
        verify(mockedCurrentQuotaManager, never()).increase(QUOTA_ROOT, 0, 0);
    }

}
