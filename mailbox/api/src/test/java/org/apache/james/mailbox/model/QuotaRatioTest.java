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

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class QuotaRatioTest {

    private static final Quota<QuotaSizeLimit, QuotaSizeUsage> QUOTA_SIZE = Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
            .used(QuotaSizeUsage.size(15))
            .computedLimit(QuotaSizeLimit.size(60))
            .build();
    private static final Quota<QuotaCountLimit, QuotaCountUsage> QUOTA_COUNT = Quota.<QuotaCountLimit, QuotaCountUsage>builder()
            .used(QuotaCountUsage.count(1))
            .computedLimit(QuotaCountLimit.count(2))
            .build();

    @Test
    void shouldMatchBeanContact() {
        EqualsVerifier.forClass(QuotaRatio.class)
            .verify();
    }

    @Test
    void quotaRatioShouldThrowWhenQuotaSizeIsNull() {
        assertThatThrownBy(() -> QuotaRatio.from(null, QUOTA_COUNT))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void quotaRatioShouldThrowWhenQuotaCountIsNull() {
        assertThatThrownBy(() -> QuotaRatio.from(QUOTA_SIZE, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void quotaSizeShouldReturnTheQuotaSize() {
        QuotaRatio quotaRatio = QuotaRatio.from(QUOTA_SIZE, QUOTA_COUNT);
        assertThat(quotaRatio.getQuotaSize()).isEqualTo(QUOTA_SIZE);
    }

    @Test
    void quotaCountShouldReturnTheQuotaCount() {
        QuotaRatio quotaRatio = QuotaRatio.from(QUOTA_SIZE, QUOTA_COUNT);
        assertThat(quotaRatio.getQuotaCount()).isEqualTo(QUOTA_COUNT);
    }

    @Test
    void maxShouldReturnTheMaxRatio() {
        double max = QuotaRatio.from(QUOTA_SIZE, QUOTA_COUNT)
                .max();

        assertThat(max).isEqualTo(0.5);
    }
}
