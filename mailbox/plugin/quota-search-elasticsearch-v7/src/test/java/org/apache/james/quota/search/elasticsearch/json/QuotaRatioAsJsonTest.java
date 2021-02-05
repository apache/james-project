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
package org.apache.james.quota.search.elasticsearch.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRatio;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class QuotaRatioAsJsonTest {

    static final Quota<QuotaSizeLimit, QuotaSizeUsage> QUOTA_SIZE = Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
            .used(QuotaSizeUsage.size(15))
            .computedLimit(QuotaSizeLimit.size(60))
            .build();
    static final Quota<QuotaCountLimit, QuotaCountUsage> QUOTA_COUNT = Quota.<QuotaCountLimit, QuotaCountUsage>builder()
            .used(QuotaCountUsage.count(1))
            .computedLimit(QuotaCountLimit.count(2))
            .build();

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(QuotaRatioAsJson.class)
            .verify();
    }

    @Test
    void buildShouldThrownWhenUserIsNull() {
        assertThatThrownBy(() -> QuotaRatioAsJson.builder()
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrownWhenUserIsEmpty() {
        assertThatThrownBy(() -> QuotaRatioAsJson.builder()
                .user("")
                .build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void buildShouldThrownWhenQuotaRatioIsNull() {
        assertThatThrownBy(() -> QuotaRatioAsJson.builder()
                .user("user")
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getDomainShouldReturnEmptyWhenNone() {
        QuotaRatioAsJson quotaRatioAsJson = QuotaRatioAsJson.builder()
            .user("user")
            .quotaRatio(QuotaRatio.from(QUOTA_SIZE, QUOTA_COUNT))
            .build();

        assertThat(quotaRatioAsJson.getDomain()).isEmpty();
    }

    @Test
    void getDomainShouldReturnTheDomainWhenGiven() {
        String domain = "domain";
        QuotaRatioAsJson quotaRatioAsJson = QuotaRatioAsJson.builder()
            .user("user")
            .domain(Optional.of(domain))
            .quotaRatio(QuotaRatio.from(QUOTA_SIZE, QUOTA_COUNT))
            .build();

        assertThat(quotaRatioAsJson.getDomain()).contains(domain);
    }

    @Test
    void getMaxQuotaRatioShouldReturnTheMaxQuotaRatio() {
        String domain = "domain";
        QuotaRatioAsJson quotaRatioAsJson = QuotaRatioAsJson.builder()
            .user("user")
            .domain(Optional.of(domain))
            .quotaRatio(QuotaRatio.from(QUOTA_SIZE, QUOTA_COUNT))
            .build();

        assertThat(quotaRatioAsJson.getMaxQuotaRatio()).isEqualTo(0.5);
    }
}
