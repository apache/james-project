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

package org.apache.james.mailbox.quota.model;

import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._50;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._80;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._95;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._99;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.quota.QuotaFixture.Sizes;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

public class QuotaThresholdsTest {

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(QuotaThresholds.class)
            .verify();
    }

    @Test
    public void highestExceededThresholdShouldReturnZeroWhenBelowAllThresholds() {
        assertThat(
            new QuotaThresholds(ImmutableList.of(_50, _80, _95, _99))
                .highestExceededThreshold(Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
                    .used(QuotaSizeUsage.size(40))
                    .computedLimit(QuotaSizeLimit.size(100))
                    .build()))
            .isEqualTo(QuotaThreshold.ZERO);
    }

    @Test
    public void highestExceededThresholdShouldReturnHighestExceededThreshold() {
        assertThat(
            new QuotaThresholds(ImmutableList.of(_50, _80, _95, _99))
                .highestExceededThreshold(Sizes._92_PERCENT))
            .isEqualTo(_80);
    }

    @Test
    public void highestExceededThresholdShouldReturnHighestThresholdWhenAboveAllThresholds() {
        assertThat(
            new QuotaThresholds(ImmutableList.of(_50, _80, _95, _99))
                .highestExceededThreshold(Sizes._992_PERTHOUSAND))
            .isEqualTo(_99);
    }

    @Test
    public void highestExceededThresholdShouldReturnZeroWhenNoThresholds() {
        assertThat(
            new QuotaThresholds(ImmutableList.of())
                .highestExceededThreshold(Sizes._992_PERTHOUSAND))
            .isEqualTo(QuotaThreshold.ZERO);
    }

    @Test
    public void highestExceededThresholdShouldReturnZeroWhenUnlimitedQuota() {
        assertThat(
            new QuotaThresholds(ImmutableList.of(_50, _80, _95, _99))
                .highestExceededThreshold(Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
                    .used(QuotaSizeUsage.size(992))
                    .computedLimit(QuotaSizeLimit.unlimited())
                    .build()))
            .isEqualTo(QuotaThreshold.ZERO);
    }

}