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

import org.apache.james.mailbox.model.Quota;
import org.junit.Test;

public class QuotaImplTest {

    @Test
    public void unlimitedQuotaShouldNotBeOverQuota() {
        assertThat(QuotaImpl.unlimited().isOverQuota()).isFalse();
    }

    @Test
    public void isOverQuotaShouldReturnFalseWhenQuotaIsNotExceeded() {
        assertThat(QuotaImpl.quota(36, 360).isOverQuota()).isFalse();
    }

    @Test
    public void isOverQuotaShouldReturnFalseWhenMaxValueIsUnlimited() {
        assertThat(QuotaImpl.quota(36, Quota.UNLIMITED).isOverQuota()).isFalse();
    }

    @Test
    public void isOverQuotaShouldReturnFalseWhenUsedValueIsUnknown() {
        assertThat(QuotaImpl.quota(Quota.UNKNOWN, 36).isOverQuota()).isFalse();
    }

    @Test
    public void isOverQuotaShouldReturnTrueWhenQuotaIsExceeded() {
        assertThat(QuotaImpl.quota(360, 36).isOverQuota()).isTrue();
    }

}
