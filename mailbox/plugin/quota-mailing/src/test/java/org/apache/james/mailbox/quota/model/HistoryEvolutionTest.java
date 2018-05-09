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

import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.NOW;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._75;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class HistoryEvolutionTest {

    private static final QuotaThresholdChange SAMPLE_THRESHOLD = new QuotaThresholdChange(_75, NOW);

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(HistoryEvolution.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    void isModifiedShouldReturnFalseWhenNoChange() {
        assertThat(
            HistoryEvolution.noChanges()
                .isChange())
            .isFalse();
    }

    @Test
    void isModifiedShouldReturnTrueWhenLowerThresholdReached() {
        assertThat(
            HistoryEvolution.lowerThresholdReached(SAMPLE_THRESHOLD)
                .isChange())
            .isTrue();
    }

    @Test
    void isModifiedShouldReturnTrueWhenHigherThresholdAlreadyReachedWithinGracePeriod() {
        assertThat(
            HistoryEvolution.higherThresholdReached(SAMPLE_THRESHOLD, HistoryEvolution.HighestThresholdRecentness.AlreadyReachedDuringGracePeriod)
                .isChange())
            .isTrue();
    }

    @Test
    void isModifiedShouldReturnTrueWhenHigherThresholdReachedNotAlreadyReachedWithinGracePeriod() {
        assertThat(
            HistoryEvolution.higherThresholdReached(SAMPLE_THRESHOLD, HistoryEvolution.HighestThresholdRecentness.NotAlreadyReachedDuringGracePeriod)
                .isChange())
            .isTrue();
    }

    @Test
    void currentThresholdNotRecentlyReachedShouldReturnFalseWhenNoChange() {
        assertThat(
            HistoryEvolution.noChanges()
                .currentThresholdNotRecentlyReached())
            .isFalse();
    }

    @Test
    void currentThresholdNotRecentlyReachedShouldReturnFalseWhenLowerThresholdReached() {
        assertThat(
            HistoryEvolution.lowerThresholdReached(SAMPLE_THRESHOLD)
                .currentThresholdNotRecentlyReached())
            .isFalse();
    }

    @Test
    void currentThresholdNotRecentlyReachedShouldReturnFalseWhenHigherThresholdReachedAlreadyReachedWithinGracePeriod() {
        assertThat(
            HistoryEvolution.higherThresholdReached(SAMPLE_THRESHOLD, HistoryEvolution.HighestThresholdRecentness.AlreadyReachedDuringGracePeriod)
                .currentThresholdNotRecentlyReached())
            .isFalse();
    }

    @Test
    void currentThresholdNotRecentlyReachedShouldReturnTrueWhenHigherThresholdReachedNotAlreadyReachedWithinGracePeriod() {
        assertThat(
            HistoryEvolution.higherThresholdReached(SAMPLE_THRESHOLD, HistoryEvolution.HighestThresholdRecentness.NotAlreadyReachedDuringGracePeriod)
                .currentThresholdNotRecentlyReached())
            .isTrue();
    }
}