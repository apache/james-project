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

package org.apache.james.mailbox.quota.mailing.subscribers;

import static org.apache.james.mailbox.quota.model.HistoryEvolution.HighestThresholdRecentness.AlreadyReachedDuringGracePeriod;
import static org.apache.james.mailbox.quota.model.HistoryEvolution.HighestThresholdRecentness.NotAlreadyReachedDuringGracePeriod;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture.TestConstants.NOW;
import static org.apache.james.mailbox.quota.model.QuotaThresholdFixture._80;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;
import org.apache.james.mailbox.quota.model.HistoryEvolution;
import org.apache.james.mailbox.quota.model.QuotaThresholdChange;
import org.apache.james.mailbox.quota.model.QuotaThresholdFixture.Quotas.Counts;
import org.apache.james.mailbox.quota.model.QuotaThresholdFixture.Quotas.Sizes;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class QuotaThresholdNoticeTest {

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(QuotaThresholdNotice.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    void buildShouldReturnEmptyWhenNoThresholds() {
        assertThat(QuotaThresholdNotice.builder()
            .sizeQuota(Sizes._82_PERCENT)
            .countQuota(Counts._82_PERCENT)
            .build())
            .isEmpty();
    }

    @Test
    void buildShouldReturnEmptyWhenNoChanges() {
        assertThat(QuotaThresholdNotice.builder()
            .sizeQuota(Sizes._82_PERCENT)
            .countQuota(Counts._82_PERCENT)
            .sizeThreshold(HistoryEvolution.noChanges())
            .build())
            .isEmpty();
    }

    @Test
    void buildShouldReturnEmptyWhenBelow() {
        assertThat(QuotaThresholdNotice.builder()
            .sizeQuota(Sizes._82_PERCENT)
            .countQuota(Counts._82_PERCENT)
            .sizeThreshold(HistoryEvolution.lowerThresholdReached(new QuotaThresholdChange(_80, NOW)))
            .build())
            .isEmpty();
    }

    @Test
    void buildShouldReturnEmptyWhenAboveButRecentChanges() {
        assertThat(QuotaThresholdNotice.builder()
            .sizeQuota(Sizes._82_PERCENT)
            .countQuota(Counts._82_PERCENT)
            .sizeThreshold(HistoryEvolution.higherThresholdReached(new QuotaThresholdChange(_80, NOW), AlreadyReachedDuringGracePeriod))
            .build())
            .isEmpty();
    }

    @Test
    void buildShouldReturnPresentWhenAbove() {
        Quota<QuotaSize> sizeQuota = Sizes._82_PERCENT;
        Quota<QuotaCount> countQuota = Counts._82_PERCENT;
        QuotaThresholdChange sizeThresholdChange = new QuotaThresholdChange(_80, NOW);

        assertThat(QuotaThresholdNotice.builder()
            .sizeQuota(sizeQuota)
            .countQuota(countQuota)
            .sizeThreshold(HistoryEvolution.higherThresholdReached(sizeThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .build())
            .isNotEmpty()
            .contains(new QuotaThresholdNotice(Optional.empty(), Optional.of(sizeThresholdChange.getQuotaThreshold()), sizeQuota, countQuota));
    }

    @Test
    void buildShouldFilterOutNotInterestingFields() {
        Quota<QuotaSize> sizeQuota = Sizes._82_PERCENT;
        Quota<QuotaCount> countQuota = Counts._82_PERCENT;
        QuotaThresholdChange sizeThresholdChange = new QuotaThresholdChange(_80, NOW);
        QuotaThresholdChange countThresholdChange = new QuotaThresholdChange(_80, NOW);

        assertThat(QuotaThresholdNotice.builder()
            .sizeQuota(sizeQuota)
            .countQuota(countQuota)
            .sizeThreshold(HistoryEvolution.higherThresholdReached(sizeThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .countThreshold(HistoryEvolution.lowerThresholdReached(countThresholdChange))
            .build())
            .isNotEmpty()
            .contains(new QuotaThresholdNotice(Optional.empty(), Optional.of(sizeThresholdChange.getQuotaThreshold()), sizeQuota, countQuota));
    }

    @Test
    void buildShouldKeepAllInterestingFields() {
        Quota<QuotaSize> sizeQuota = Sizes._82_PERCENT;
        Quota<QuotaCount> countQuota = Counts._82_PERCENT;
        QuotaThresholdChange sizeThresholdChange = new QuotaThresholdChange(_80, NOW);
        QuotaThresholdChange countThresholdChange = new QuotaThresholdChange(_80, NOW);

        assertThat(QuotaThresholdNotice.builder()
            .sizeQuota(sizeQuota)
            .countQuota(countQuota)
            .sizeThreshold(HistoryEvolution.higherThresholdReached(sizeThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .countThreshold(HistoryEvolution.higherThresholdReached(countThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .build())
            .isNotEmpty()
            .contains(new QuotaThresholdNotice(Optional.of(countThresholdChange.getQuotaThreshold()), Optional.of(sizeThresholdChange.getQuotaThreshold()), sizeQuota, countQuota));
    }

    @Test
    void generateReportShouldGenerateAHumanReadableMessage() {
        QuotaThresholdChange sizeThresholdChange = new QuotaThresholdChange(_80, NOW);
        QuotaThresholdChange countThresholdChange = new QuotaThresholdChange(_80, NOW);

        assertThat(QuotaThresholdNotice.builder()
            .sizeQuota(Sizes._82_PERCENT)
            .countQuota(Counts._92_PERCENT)
            .sizeThreshold(HistoryEvolution.higherThresholdReached(sizeThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .countThreshold(HistoryEvolution.higherThresholdReached(countThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .build()
            .get()
            .generateReport())
            .isEqualTo("You receive this email because you recently exceeded a threshold related to the quotas of your email account.\n" +
                "\n" +
                "You currently occupy more than 80 % of the total size allocated to you.\n" +
                "You currently occupy 82 bytes on a total of 100 bytes allocated to you.\n" +
                "\n" +
                "You currently occupy more than 80 % of the total message count allocated to you.\n" +
                "You currently have 92 messages on a total of 100 allowed for you.\n" +
                "\n" +
                "You need to be aware that actions leading to exceeded quotas will be denied. This will result in a degraded service.\n" +
                "To mitigate this issue you might reach your administrator in order to increase your configured quota. You might also delete some non important emails.");
    }

    @Test
    void generateReportShouldOmitCountPartWhenNone() {
        QuotaThresholdChange sizeThresholdChange = new QuotaThresholdChange(_80, NOW);

        assertThat(QuotaThresholdNotice.builder()
            .sizeQuota(Sizes._82_PERCENT)
            .countQuota(Counts._72_PERCENT)
            .sizeThreshold(HistoryEvolution.higherThresholdReached(sizeThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .build()
            .get()
            .generateReport())
            .isEqualTo("You receive this email because you recently exceeded a threshold related to the quotas of your email account.\n" +
                "\n" +
                "You currently occupy more than 80 % of the total size allocated to you.\n" +
                "You currently occupy 82 bytes on a total of 100 bytes allocated to you.\n" +
                "\n" +
                "You need to be aware that actions leading to exceeded quotas will be denied. This will result in a degraded service.\n" +
                "To mitigate this issue you might reach your administrator in order to increase your configured quota. You might also delete some non important emails.");
    }

    @Test
    void generateReportShouldOmitSizePartWhenNone() {
        QuotaThresholdChange countThresholdChange = new QuotaThresholdChange(_80, NOW);

        assertThat(QuotaThresholdNotice.builder()
            .sizeQuota(Sizes._82_PERCENT)
            .countQuota(Counts._92_PERCENT)
            .countThreshold(HistoryEvolution.higherThresholdReached(countThresholdChange, NotAlreadyReachedDuringGracePeriod))
            .build()
            .get()
            .generateReport())
            .isEqualTo("You receive this email because you recently exceeded a threshold related to the quotas of your email account.\n" +
                "\n" +
                "You currently occupy more than 80 % of the total message count allocated to you.\n" +
                "You currently have 92 messages on a total of 100 allowed for you.\n" +
                "\n" +
                "You need to be aware that actions leading to exceeded quotas will be denied. This will result in a degraded service.\n" +
                "To mitigate this issue you might reach your administrator in order to increase your configured quota. You might also delete some non important emails.");
    }
}