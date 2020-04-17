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

import java.util.Optional;

import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.model.CurrentQuotas;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;

public abstract class StoreCurrentQuotaManagerTest {
    private static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot("benwa", Optional.empty());
    private static final CurrentQuotas CURRENT_QUOTAS = new CurrentQuotas(QuotaCountUsage.count(10), QuotaSizeUsage.size(100));
    private static final QuotaOperation RESET_QUOTA_OPERATION = new QuotaOperation(QUOTA_ROOT, QuotaCountUsage.count(10), QuotaSizeUsage.size(100));
    
    protected abstract StoreCurrentQuotaManager provideTestee();
    
    private StoreCurrentQuotaManager testee;

    @BeforeEach
    void setUp() {
        testee = provideTestee();
    }

    @Test
    void getCurrentStorageShouldReturnZeroByDefault() {
        assertThat(Mono.from(testee.getCurrentStorage(QUOTA_ROOT)).block()).isEqualTo(QuotaSizeUsage.size(0));
    }

    @Test
    void getCurrentMessageCountShouldReturnZeroByDefault() {
        assertThat(Mono.from(testee.getCurrentMessageCount(QUOTA_ROOT)).block()).isEqualTo(QuotaCountUsage.count(0));
    }

    @Test
    void getCurrentQuotasShouldReturnZeroByDefault() {
        assertThat(Mono.from(testee.getCurrentQuotas(QUOTA_ROOT)).block()).isEqualTo(CurrentQuotas.emptyQuotas());
    }

    @Test
    void increaseShouldWork() {
        Mono.from(testee.increase(new QuotaOperation(QUOTA_ROOT, QuotaCountUsage.count(10), QuotaSizeUsage.size(100)))).block();

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            softly.assertThat(Mono.from(testee.getCurrentQuotas(QUOTA_ROOT)).block()).isEqualTo(CURRENT_QUOTAS);
            softly.assertThat(Mono.from(testee.getCurrentMessageCount(QUOTA_ROOT)).block()).isEqualTo(QuotaCountUsage.count(10));
            softly.assertThat(Mono.from(testee.getCurrentStorage(QUOTA_ROOT)).block()).isEqualTo(QuotaSizeUsage.size(100));
        }));
    }

    @Test
    void decreaseShouldWork() {
        Mono.from(testee.increase(new QuotaOperation(QUOTA_ROOT, QuotaCountUsage.count(20), QuotaSizeUsage.size(200)))).block();

        Mono.from(testee.decrease(new QuotaOperation(QUOTA_ROOT, QuotaCountUsage.count(10), QuotaSizeUsage.size(100)))).block();

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            softly.assertThat(Mono.from(testee.getCurrentQuotas(QUOTA_ROOT)).block()).isEqualTo(CURRENT_QUOTAS);
            softly.assertThat(Mono.from(testee.getCurrentMessageCount(QUOTA_ROOT)).block()).isEqualTo(QuotaCountUsage.count(10));
            softly.assertThat(Mono.from(testee.getCurrentStorage(QUOTA_ROOT)).block()).isEqualTo(QuotaSizeUsage.size(100));
        }));
    }

    @Test
    void decreaseShouldNotFailWhenItLeadsToNegativeValues() {
        Mono.from(testee.decrease(new QuotaOperation(QUOTA_ROOT, QuotaCountUsage.count(10), QuotaSizeUsage.size(100)))).block();

        SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
            softly.assertThat(Mono.from(testee.getCurrentQuotas(QUOTA_ROOT)).block())
                .isEqualTo(new CurrentQuotas(QuotaCountUsage.count(-10), QuotaSizeUsage.size(-100)));
            softly.assertThat(Mono.from(testee.getCurrentMessageCount(QUOTA_ROOT)).block()).isEqualTo(QuotaCountUsage.count(-10));
            softly.assertThat(Mono.from(testee.getCurrentStorage(QUOTA_ROOT)).block()).isEqualTo(QuotaSizeUsage.size(-100));
        }));
    }

    @Test
    void resetCurrentQuotasShouldNoopWhenZeroAndNoData() {
        QuotaOperation quotaOperation = new QuotaOperation(QUOTA_ROOT, QuotaCountUsage.count(0), QuotaSizeUsage.size(0));

        testee.resetCurrentQuotas(quotaOperation).block();

        assertThat(Mono.from(testee.getCurrentQuotas(QUOTA_ROOT)).block())
            .isEqualTo(CurrentQuotas.emptyQuotas());
    }

    @Test
    void resetCurrentQuotasShouldReInitQuotasWhenNothing() {
        testee.resetCurrentQuotas(RESET_QUOTA_OPERATION).block();

        assertThat(Mono.from(testee.getCurrentQuotas(QUOTA_ROOT)).block())
            .isEqualTo(CURRENT_QUOTAS);
    }

    @Test
    void resetCurrentQuotasShouldReInitQuotasWhenData() {
        Mono.from(testee.increase(new QuotaOperation(QUOTA_ROOT, QuotaCountUsage.count(20), QuotaSizeUsage.size(200)))).block();

        testee.resetCurrentQuotas(RESET_QUOTA_OPERATION).block();

        assertThat(Mono.from(testee.getCurrentQuotas(QUOTA_ROOT)).block())
            .isEqualTo(CURRENT_QUOTAS);
    }

    @Test
    void resetCurrentQuotasShouldBeIdempotent() {
        Mono.from(testee.increase(new QuotaOperation(QUOTA_ROOT, QuotaCountUsage.count(20), QuotaSizeUsage.size(200)))).block();

        testee.resetCurrentQuotas(RESET_QUOTA_OPERATION).block();
        testee.resetCurrentQuotas(RESET_QUOTA_OPERATION).block();

        assertThat(Mono.from(testee.getCurrentQuotas(QUOTA_ROOT)).block())
            .isEqualTo(CURRENT_QUOTAS);
    }
}
