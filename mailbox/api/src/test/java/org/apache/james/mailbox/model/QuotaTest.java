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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.junit.Test;

public class QuotaTest {


    @Test
    public void isOverQuotaShouldReturnFalseWhenQuotaIsNotExceeded() {
        Quota<QuotaCount> quota = Quota.<QuotaCount>builder().used(QuotaCount.count(36)).computedLimit(QuotaCount.count(360)).build();
        assertThat(quota.isOverQuota()).isFalse();
    }

    @Test
    public void isOverQuotaShouldReturnFalseWhenMaxValueIsUnlimited() {
        Quota<QuotaCount> quota = Quota.<QuotaCount>builder().used(QuotaCount.count(36)).computedLimit(QuotaCount.unlimited()).build();
        assertThat(quota.isOverQuota()).isFalse();
    }

    @Test
    public void isOverQuotaShouldReturnTrueWhenQuotaIsExceeded() {
        Quota<QuotaCount> quota = Quota.<QuotaCount>builder().used(QuotaCount.count(360)).computedLimit(QuotaCount.count(36)).build();
        assertThat(quota.isOverQuota()).isTrue();
    }

    @Test
    public void isOverQuotaWithAdditionalValueShouldReturnTrueWhenOverLimit() {
        Quota<QuotaCount> quota = Quota.<QuotaCount>builder().used(QuotaCount.count(36)).computedLimit(QuotaCount.count(36)).build();
        assertThat(quota.isOverQuotaWithAdditionalValue(1)).isTrue();
    }

    @Test
    public void isOverQuotaWithAdditionalValueShouldReturnTrueWhenUnderLimit() {
        Quota<QuotaCount> quota = Quota.<QuotaCount>builder().used(QuotaCount.count(34)).computedLimit(QuotaCount.count(36)).build();
        assertThat(quota.isOverQuotaWithAdditionalValue(1)).isFalse();
    }

    @Test
    public void isOverQuotaWithAdditionalValueShouldReturnFalseWhenAtLimit() {
        Quota<QuotaCount> quota = Quota.<QuotaCount>builder().used(QuotaCount.count(36)).computedLimit(QuotaCount.count(36)).build();
        assertThat(quota.isOverQuotaWithAdditionalValue(0)).isFalse();
    }

    @Test
    public void isOverQuotaWithAdditionalValueShouldThrowOnNegativeValue() {
        Quota<QuotaCount> quota = Quota.<QuotaCount>builder().used(QuotaCount.count(25)).computedLimit(QuotaCount.count(36)).build();
        assertThatThrownBy(() -> quota.isOverQuotaWithAdditionalValue(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void buildShouldThrowOnMissingUsedValue() {
        assertThatThrownBy(
            () -> Quota.<QuotaCount>builder().computedLimit(QuotaCount.count(1)).build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void buildShouldThrowOnMissingComputedLimitValue() {
        assertThatThrownBy(
            () -> Quota.<QuotaCount>builder().used(QuotaCount.count(1)).build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void buildShouldCreateValidObjectGivenMandatoryFields() {
        Quota<QuotaCount> actual = Quota.<QuotaCount>builder()
            .used(QuotaCount.count(1))
            .computedLimit(QuotaCount.count(2))
            .build();
        assertThat(actual).isNotNull();
    }

    @Test
    public void getRatioShouldReturnUsedDividedByLimit() {
        assertThat(
            Quota.<QuotaSize>builder()
                .used(QuotaSize.size(15))
                .computedLimit(QuotaSize.size(60))
                .build()
                .getRatio())
            .isEqualTo(0.25);
    }

    @Test
    public void getRatioShouldReturnZeroWhenUnlimited() {
        assertThat(
            Quota.<QuotaSize>builder()
                .used(QuotaSize.size(15))
                .computedLimit(QuotaSize.unlimited())
                .build()
                .getRatio())
            .isEqualTo(0);
    }

}
