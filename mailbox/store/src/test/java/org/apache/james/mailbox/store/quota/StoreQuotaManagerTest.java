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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StoreQuotaManagerTest {

    StoreQuotaManager testee;
    CurrentQuotaManager mockedCurrentQuotaManager;
    MaxQuotaManager mockedMaxQuotaManager;
    QuotaRoot quotaRoot;

    @BeforeEach
    void setUp() {
        mockedCurrentQuotaManager = mock(CurrentQuotaManager.class);
        mockedMaxQuotaManager = mock(MaxQuotaManager.class);
        testee = new StoreQuotaManager(mockedCurrentQuotaManager, mockedMaxQuotaManager);
        quotaRoot = QuotaRoot.quotaRoot("benwa", Optional.empty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getMessageQuotaShouldWorkWithNumericValues() throws Exception {
        when(mockedMaxQuotaManager.getMaxMessage(any(Map.class))).thenReturn(Optional.of(QuotaCountLimit.count(360L)));
        when(mockedCurrentQuotaManager.getCurrentMessageCount(quotaRoot)).thenReturn(QuotaCountUsage.count(36L));
        assertThat(testee.getMessageQuota(quotaRoot)).isEqualTo(
            Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(36)).computedLimit(QuotaCountLimit.count(360)).build());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getStorageQuotaShouldWorkWithNumericValues() throws Exception {
        when(mockedMaxQuotaManager.getMaxStorage(any(Map.class))).thenReturn(Optional.of(QuotaSizeLimit.size(360L)));
        when(mockedCurrentQuotaManager.getCurrentStorage(quotaRoot)).thenReturn(QuotaSizeUsage.size(36L));
        assertThat(testee.getStorageQuota(quotaRoot)).isEqualTo(
            Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(36)).computedLimit(QuotaSizeLimit.size(360)).build());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getStorageQuotaShouldCalculateCurrentQuotaWhenUnlimited() throws Exception {
        when(mockedMaxQuotaManager.getMaxStorage(any(Map.class))).thenReturn(Optional.of(QuotaSizeLimit.unlimited()));
        when(mockedCurrentQuotaManager.getCurrentStorage(quotaRoot)).thenReturn(QuotaSizeUsage.size(36L));

        assertThat(testee.getStorageQuota(quotaRoot)).isEqualTo(
            Quota.<QuotaSizeLimit, QuotaSizeUsage>builder().used(QuotaSizeUsage.size(36)).computedLimit(QuotaSizeLimit.unlimited()).build());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getMessageQuotaShouldCalculateCurrentQuotaWhenUnlimited() throws Exception {
        when(mockedMaxQuotaManager.getMaxMessage(any(Map.class))).thenReturn(Optional.of(QuotaCountLimit.unlimited()));
        when(mockedCurrentQuotaManager.getCurrentMessageCount(quotaRoot)).thenReturn(QuotaCountUsage.count(36L));

        assertThat(testee.getMessageQuota(quotaRoot)).isEqualTo(
            Quota.<QuotaCountLimit, QuotaCountUsage>builder().used(QuotaCountUsage.count(36)).computedLimit(QuotaCountLimit.unlimited()).build());
    }

}
