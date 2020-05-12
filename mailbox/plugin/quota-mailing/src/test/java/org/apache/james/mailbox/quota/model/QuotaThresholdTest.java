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

import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._75;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._759;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._90;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.quota.QuotaFixture.Sizes;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class QuotaThresholdTest {

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(QuotaThreshold.class)
            .verify();
    }

    @Test
    public void constructorShouldThrowBelowLowerValue() {
        assertThatThrownBy(() -> new QuotaThreshold(-0.00001))
            .isInstanceOf(IllegalArgumentException.class);
    }


    @Test
    public void constructorShouldThrowAboveUpperValue() {
        assertThatThrownBy(() -> new QuotaThreshold(1.00001))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void constructorShouldNotThrowOnLowerValue() {
        assertThatCode(() -> new QuotaThreshold(0.))
            .doesNotThrowAnyException();
    }

    @Test
    public void constructorShouldNotThrowOnUpperValue() {
        assertThatCode(() -> new QuotaThreshold(1.))
            .doesNotThrowAnyException();
    }

    @Test
    public void isExceededShouldReturnFalseWhenBelowThreshold() {
        assertThat(_75.isExceeded(Sizes._60_PERCENT))
            .isFalse();
    }

    @Test
    public void isExceededShouldReturnTrueWhenAboveThreshold() {
        assertThat(_75.isExceeded(Sizes._82_PERCENT))
            .isTrue();
    }

    @Test
    public void isExceededShouldReturnFalseWhenOnThreshold() {
        assertThat(_75.isExceeded(Sizes._75_PERCENT))
            .isFalse();
    }

    @Test
    public void isExceededShouldReturnFalseWhenUnlimited() {
        Quota<QuotaSizeLimit, QuotaSizeUsage> quota = Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
            .computedLimit(QuotaSizeLimit.unlimited())
            .used(QuotaSizeUsage.size(80))
            .build();

        assertThat(_75.isExceeded(quota))
            .isFalse();
    }

    @Test
    public void nonZeroShouldFilterZero() {
        assertThat(QuotaThreshold.ZERO.nonZero())
            .isEmpty();
    }

    @Test
    public void nonZeroShouldNotFilterNonZeroValues() {
        assertThat(_75.nonZero())
            .contains(_75);
    }

    @Test
    public void getQuotaOccupationRatioAsPercentShouldReturnIntRepresentationOfThreshold() {
        assertThat(_75.getQuotaOccupationRatioAsPercent())
            .isEqualTo(75);
    }

    @Test
    public void getQuotaOccupationRatioAsPercentShouldTruncateValues() {
        assertThat(_759.getQuotaOccupationRatioAsPercent())
            .isEqualTo(75);
    }

    @Test
    public void compareToShouldReturnNegativeWhenLowerThanComparedValue() {
        assertThat(_75.compareTo(_90))
            .isLessThan(0);
    }

    @Test
    public void compareToShouldReturnPositiveWhenHigherThanComparedValue() {
        assertThat(_90.compareTo(_75))
            .isGreaterThan(0);
    }

    @Test
    @SuppressWarnings("SelfComparison")
    public void compareToShouldReturnZeroWhenEquals() {
        assertThat(_75.compareTo(_75))
            .isEqualTo(0);
    }

}