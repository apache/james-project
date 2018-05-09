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

import static org.apache.james.mailbox.quota.model.HistoryEvolution.HighestThresholdRecentness.AlreadyReachedDuringGracePeriod;
import static org.apache.james.mailbox.quota.model.HistoryEvolution.HighestThresholdRecentness.NotAlreadyReachedDuringGracePeriod;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.GRACE_PERIOD;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.NOW;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._50;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._75;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class QuotaThresholdHistoryTest {

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(QuotaThresholdHistory.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void compareWithCurrentThresholdShouldReturnAboveWhenStrictlyAboveDuringDuration() {
        assertThat(
            new QuotaThresholdHistory(
                    new QuotaThresholdChange(_50, NOW.minus(Duration.ofDays(24))),
                    new QuotaThresholdChange(_75, NOW.minus(Duration.ofDays(12))),
                    new QuotaThresholdChange(_50, NOW.minus(Duration.ofDays(6))))
                .compareWithCurrentThreshold(new QuotaThresholdChange(_75, NOW), GRACE_PERIOD))
            .isEqualTo(HistoryEvolution.higherThresholdReached(new QuotaThresholdChange(_75, NOW), NotAlreadyReachedDuringGracePeriod));
    }

    @Test
    public void compareWithCurrentThresholdShouldReturnBelowWhenLowerThanLastChange() {
        assertThat(
            new QuotaThresholdHistory(
                new QuotaThresholdChange(_50, NOW.minus(Duration.ofDays(24))),
                new QuotaThresholdChange(_75, NOW.minus(Duration.ofDays(12))))
                .compareWithCurrentThreshold(new QuotaThresholdChange(_50, NOW), GRACE_PERIOD))
            .isEqualTo(HistoryEvolution.lowerThresholdReached(new QuotaThresholdChange(_50, NOW)));
    }

    @Test
    public void compareWithCurrentThresholdShouldReturnNoChangeWhenEqualsLastChange() {
        assertThat(
            new QuotaThresholdHistory(
                new QuotaThresholdChange(_50, NOW.minus(Duration.ofDays(24))),
                new QuotaThresholdChange(_75, NOW.minus(Duration.ofDays(12))))
                .compareWithCurrentThreshold(new QuotaThresholdChange(_75, NOW), GRACE_PERIOD))
            .isEqualTo(HistoryEvolution.noChanges());
    }

    @Test
    public void compareWithCurrentThresholdShouldReturnAboveWithRecentChangesWhenThresholdExceededDuringDuration() {
        assertThat(
            new QuotaThresholdHistory(
                    new QuotaThresholdChange(_50, NOW.minus(Duration.ofDays(24))),
                    new QuotaThresholdChange(_75, NOW.minus(Duration.ofHours(12))),
                    new QuotaThresholdChange(_50, NOW.minus(Duration.ofHours(6))))
                .compareWithCurrentThreshold(new QuotaThresholdChange(_75, NOW), GRACE_PERIOD))
            .isEqualTo(HistoryEvolution.higherThresholdReached(new QuotaThresholdChange(_75, NOW), AlreadyReachedDuringGracePeriod));
    }

}