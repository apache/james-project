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

package org.apache.james.mailbox.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.quota.QuotaCount;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class QuotaTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void isOverQuotaShouldReturnFalseWhenQuotaIsNotExceeded() {
        assertThat(Quota.quota(QuotaCount.count(36), QuotaCount.count(360)).isOverQuota()).isFalse();
    }

    @Test
    public void isOverQuotaShouldReturnFalseWhenMaxValueIsUnlimited() {
        assertThat(Quota.quota(QuotaCount.count(36), QuotaCount.unlimited()).isOverQuota()).isFalse();
    }

    @Test
    public void isOverQuotaShouldReturnTrueWhenQuotaIsExceeded() {
        assertThat(Quota.quota(QuotaCount.count(360), QuotaCount.count(36)).isOverQuota()).isTrue();
    }

    @Test
    public void isOverQuotaWithAdditionalValueShouldReturnTrueWhenOverLimit() {
        assertThat(Quota.quota(QuotaCount.count(36), QuotaCount.count(36)).isOverQuotaWithAdditionalValue(1)).isTrue();
    }

    @Test
    public void isOverQuotaWithAdditionalValueShouldReturnTrueWhenUnderLimit() {
        assertThat(Quota.quota(QuotaCount.count(34), QuotaCount.count(36)).isOverQuotaWithAdditionalValue(1)).isFalse();
    }

    @Test
    public void isOverQuotaWithAdditionalValueShouldReturnFalseWhenAtLimit() {
        assertThat(Quota.quota(QuotaCount.count(36), QuotaCount.count(36)).isOverQuotaWithAdditionalValue(0)).isFalse();
    }

    @Test
    public void isOverQuotaWithAdditionalValueShouldThrowOnNegativeValue() {
        expectedException.expect(IllegalArgumentException.class);

        Quota.quota(QuotaCount.count(25), QuotaCount.count(36)).isOverQuotaWithAdditionalValue(-1);
    }

}
