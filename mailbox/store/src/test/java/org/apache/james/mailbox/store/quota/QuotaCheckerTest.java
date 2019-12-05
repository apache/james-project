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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuotaCheckerTest {

    static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("benwa", Optional.empty());
    static final MailboxPath MAILBOX_PATH = MailboxPath.inbox(Username.of("benwa"));
    static final Mailbox MAILBOX = new Mailbox(MAILBOX_PATH, 10);

    QuotaRootResolver mockedQuotaRootResolver;
    QuotaManager mockedQuotaManager;

    @BeforeEach
    void setUp() {
        mockedQuotaManager = mock(QuotaManager.class);
        mockedQuotaRootResolver = mock(QuotaRootResolver.class);
    }

    @Test
    void quotaCheckerShouldNotThrowOnRegularQuotas() throws MailboxException {
        when(mockedQuotaRootResolver.getQuotaRoot(MAILBOX_PATH)).thenReturn(QUOTA_ROOT);
        when(mockedQuotaManager.getMessageQuota(QUOTA_ROOT)).thenReturn(
            Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(10)).computedLimit(QuotaCountLimit.count(100)).build());
        when(mockedQuotaManager.getStorageQuota(QUOTA_ROOT)).thenReturn(
            Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(100)).computedLimit(QuotaSizeLimit.size(1000)).build());
        QuotaChecker quotaChecker = new QuotaChecker(mockedQuotaManager, mockedQuotaRootResolver, MAILBOX);

        quotaChecker.tryAddition(0, 0);
    }

    @Test
    void quotaCheckerShouldNotThrowOnRegularModifiedQuotas() throws MailboxException {
        when(mockedQuotaRootResolver.getQuotaRoot(MAILBOX_PATH)).thenReturn(QUOTA_ROOT);
        when(mockedQuotaManager.getMessageQuota(QUOTA_ROOT)).thenReturn(
            Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(10)).computedLimit(QuotaCountLimit.count(100)).build());
        when(mockedQuotaManager.getStorageQuota(QUOTA_ROOT)).thenReturn(
            Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(100)).computedLimit(QuotaSizeLimit.size(1000)).build());
        QuotaChecker quotaChecker = new QuotaChecker(mockedQuotaManager, mockedQuotaRootResolver, MAILBOX);

        quotaChecker.tryAddition(89, 899);
    }

    @Test
    void quotaCheckerShouldNotThrowOnReachedMaximumQuotas() throws MailboxException {
        when(mockedQuotaRootResolver.getQuotaRoot(MAILBOX_PATH)).thenReturn(QUOTA_ROOT);
        when(mockedQuotaManager.getMessageQuota(QUOTA_ROOT)).thenReturn(
            Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(10)).computedLimit(QuotaCountLimit.count(100)).build());
        when(mockedQuotaManager.getStorageQuota(QUOTA_ROOT)).thenReturn(
            Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(100)).computedLimit(QuotaSizeLimit.size(1000)).build());
        QuotaChecker quotaChecker = new QuotaChecker(mockedQuotaManager, mockedQuotaRootResolver, MAILBOX);

        quotaChecker.tryAddition(90, 900);
    }

    @Test
    void quotaCheckerShouldThrowOnExceededMessages() throws MailboxException {
        when(mockedQuotaRootResolver.getQuotaRoot(MAILBOX_PATH)).thenReturn(QUOTA_ROOT);
        when(mockedQuotaManager.getMessageQuota(QUOTA_ROOT)).thenReturn(
            Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(10)).computedLimit(QuotaCountLimit.count(100)).build());
        when(mockedQuotaManager.getStorageQuota(QUOTA_ROOT)).thenReturn(
            Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(100)).computedLimit(QuotaSizeLimit.size(1000)).build());
        QuotaChecker quotaChecker = new QuotaChecker(mockedQuotaManager, mockedQuotaRootResolver, MAILBOX);

        assertThatThrownBy(() -> quotaChecker.tryAddition(91, 899))
            .isInstanceOf(OverQuotaException.class);
    }

    @Test
    void quotaCheckerShouldThrowOnExceededStorage() throws MailboxException {
        when(mockedQuotaRootResolver.getQuotaRoot(MAILBOX_PATH)).thenReturn(QUOTA_ROOT);
        when(mockedQuotaManager.getMessageQuota(QUOTA_ROOT)).thenReturn(
            Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(10)).computedLimit(QuotaCountLimit.count(100)).build());
        when(mockedQuotaManager.getStorageQuota(QUOTA_ROOT)).thenReturn(
            Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(100)).computedLimit(QuotaSizeLimit.size(1000)).build());
        QuotaChecker quotaChecker = new QuotaChecker(mockedQuotaManager, mockedQuotaRootResolver, MAILBOX);

        assertThatThrownBy(() -> quotaChecker.tryAddition(89, 901))
            .isInstanceOf(OverQuotaException.class);
    }

}
