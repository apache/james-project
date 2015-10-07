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

import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.TestId;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class QuotaCheckerTest {

    public static final QuotaRoot QUOTA_ROOT = QuotaRootImpl.quotaRoot("benwa");
    public static final MailboxPath MAILBOX_PATH = new MailboxPath("#private", "benwa", "INBOX");
    public static final SimpleMailbox<TestId> MAILBOX = new SimpleMailbox<TestId>(MAILBOX_PATH, 10);

    private QuotaRootResolver mockedQuotaRootResolver;
    private QuotaManager mockedQuotaManager;

    @Before
    public void setUp() {
        mockedQuotaManager = mock(QuotaManager.class);
        mockedQuotaRootResolver = mock(QuotaRootResolver.class);
    }

    @Test
    public void quotaCheckerShouldNotThrowOnRegularQuotas() throws MailboxException {
        when(mockedQuotaRootResolver.getQuotaRoot(MAILBOX_PATH)).thenAnswer(new Answer<QuotaRoot>() {
            @Override
            public QuotaRoot answer(InvocationOnMock invocationOnMock) throws Throwable {
                return QUOTA_ROOT;
            }
        });
        when(mockedQuotaManager.getMessageQuota(QUOTA_ROOT)).thenAnswer(new Answer<Quota>() {
            @Override
            public Quota answer(InvocationOnMock invocationOnMock) throws Throwable {
                return QuotaImpl.quota(10, 100);
            }
        });
        when(mockedQuotaManager.getStorageQuota(QUOTA_ROOT)).thenAnswer(new Answer<Quota>() {
            @Override
            public Quota answer(InvocationOnMock invocationOnMock) throws Throwable {
                return QuotaImpl.quota(100, 1000);
            }
        });
        QuotaChecker quotaChecker = new QuotaChecker(mockedQuotaManager, mockedQuotaRootResolver, MAILBOX);
        assertThat(quotaChecker.tryAddition(0, 0)).isTrue();
    }

    @Test
    public void quotaCheckerShouldNotThrowOnRegularModifiedQuotas() throws MailboxException {
        when(mockedQuotaRootResolver.getQuotaRoot(MAILBOX_PATH)).thenAnswer(new Answer<QuotaRoot>() {
            @Override
            public QuotaRoot answer(InvocationOnMock invocationOnMock) throws Throwable {
                return QUOTA_ROOT;
            }
        });
        when(mockedQuotaManager.getMessageQuota(QUOTA_ROOT)).thenAnswer(new Answer<Quota>() {
            @Override
            public Quota answer(InvocationOnMock invocationOnMock) throws Throwable {
                return QuotaImpl.quota(10, 100);
            }
        });
        when(mockedQuotaManager.getStorageQuota(QUOTA_ROOT)).thenAnswer(new Answer<Quota>() {
            @Override
            public Quota answer(InvocationOnMock invocationOnMock) throws Throwable {
                return QuotaImpl.quota(100, 1000);
            }
        });
        QuotaChecker quotaChecker = new QuotaChecker(mockedQuotaManager, mockedQuotaRootResolver, MAILBOX);
        assertThat(quotaChecker.tryAddition(89, 899)).isTrue();
    }

    @Test
    public void quotaCheckerShouldNotThrowOnReachedMaximumQuotas() throws MailboxException {
        when(mockedQuotaRootResolver.getQuotaRoot(MAILBOX_PATH)).thenAnswer(new Answer<QuotaRoot>() {
            @Override
            public QuotaRoot answer(InvocationOnMock invocationOnMock) throws Throwable {
                return QUOTA_ROOT;
            }
        });
        when(mockedQuotaManager.getMessageQuota(QUOTA_ROOT)).thenAnswer(new Answer<Quota>() {
            @Override
            public Quota answer(InvocationOnMock invocationOnMock) throws Throwable {
                return QuotaImpl.quota(10, 100);
            }
        });
        when(mockedQuotaManager.getStorageQuota(QUOTA_ROOT)).thenAnswer(new Answer<Quota>() {
            @Override
            public Quota answer(InvocationOnMock invocationOnMock) throws Throwable {
                return QuotaImpl.quota(100, 1000);
            }
        });
        QuotaChecker quotaChecker = new QuotaChecker(mockedQuotaManager, mockedQuotaRootResolver, MAILBOX);
        assertThat(quotaChecker.tryAddition(90, 900)).isTrue();
    }

    @Test(expected = OverQuotaException.class)
    public void quotaCheckerShouldThrowOnExceededMessages() throws MailboxException {
        QuotaChecker quotaChecker;
        try {
            when(mockedQuotaRootResolver.getQuotaRoot(MAILBOX_PATH)).thenAnswer(new Answer<QuotaRoot>() {
                @Override
                public QuotaRoot answer(InvocationOnMock invocationOnMock) throws Throwable {
                    return QUOTA_ROOT;
                }
            });
            when(mockedQuotaManager.getMessageQuota(QUOTA_ROOT)).thenAnswer(new Answer<Quota>() {
                @Override
                public Quota answer(InvocationOnMock invocationOnMock) throws Throwable {
                    return QuotaImpl.quota(10, 100);
                }
            });
            when(mockedQuotaManager.getStorageQuota(QUOTA_ROOT)).thenAnswer(new Answer<Quota>() {
                @Override
                public Quota answer(InvocationOnMock invocationOnMock) throws Throwable {
                    return QuotaImpl.quota(100, 1000);
                }
            });
            quotaChecker = new QuotaChecker(mockedQuotaManager, mockedQuotaRootResolver, MAILBOX);
        } catch(Exception e) {
            fail("Exception caught : ", e);
            return;
        }
        quotaChecker.tryAddition(91, 899);
    }

    @Test(expected = OverQuotaException.class)
    public void quotaCheckerShouldThrowOnExceededStorage() throws MailboxException {
        QuotaChecker quotaChecker;
        try {
            when(mockedQuotaRootResolver.getQuotaRoot(MAILBOX_PATH)).thenAnswer(new Answer<QuotaRoot>() {
                @Override
                public QuotaRoot answer(InvocationOnMock invocationOnMock) throws Throwable {
                    return QUOTA_ROOT;
                }
            });
            when(mockedQuotaManager.getMessageQuota(QUOTA_ROOT)).thenAnswer(new Answer<Quota>() {
                @Override
                public Quota answer(InvocationOnMock invocationOnMock) throws Throwable {
                    return QuotaImpl.quota(10, 100);
                }
            });
            when(mockedQuotaManager.getStorageQuota(QUOTA_ROOT)).thenAnswer(new Answer<Quota>() {
                @Override
                public Quota answer(InvocationOnMock invocationOnMock) throws Throwable {
                    return QuotaImpl.quota(100, 1000);
                }
            });
            quotaChecker = new QuotaChecker(mockedQuotaManager, mockedQuotaRootResolver, MAILBOX);
        } catch(Exception e) {
            fail("Exception caught : ", e);
            return;
        }
        quotaChecker.tryAddition(89, 901);
    }

}
