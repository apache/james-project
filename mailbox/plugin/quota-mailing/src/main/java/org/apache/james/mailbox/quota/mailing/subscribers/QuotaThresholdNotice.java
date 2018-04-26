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

import java.util.Objects;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;
import org.apache.james.mailbox.quota.model.HistoryEvolution;
import org.apache.james.mailbox.quota.model.QuotaThreshold;
import org.apache.james.mailbox.quota.model.QuotaThresholdChange;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class QuotaThresholdNotice {

    public static class Builder {
        private Optional<QuotaThreshold> countThreshold;
        private Optional<QuotaThreshold> sizeThreshold;
        private Quota<QuotaSize> sizeQuota;
        private Quota<QuotaCount> countQuota;

        public Builder() {
            countThreshold = Optional.empty();
            sizeThreshold = Optional.empty();
        }

        public Builder sizeQuota(Quota<QuotaSize> sizeQuota) {
            this.sizeQuota = sizeQuota;
            return this;
        }

        public Builder countQuota(Quota<QuotaCount> countQuota) {
            this.countQuota = countQuota;
            return this;
        }

        public Builder countThreshold(HistoryEvolution countHistoryEvolution) {
            this.countThreshold = Optional.of(countHistoryEvolution)
                .filter(this::needsNotification)
                .flatMap(HistoryEvolution::getThresholdChange)
                .map(QuotaThresholdChange::getQuotaThreshold);
            return this;
        }

        public Builder sizeThreshold(HistoryEvolution sizeHistoryEvolution) {
            this.sizeThreshold = Optional.of(sizeHistoryEvolution)
                .filter(this::needsNotification)
                .flatMap(HistoryEvolution::getThresholdChange)
                .map(QuotaThresholdChange::getQuotaThreshold);
            return this;
        }

        boolean needsNotification(HistoryEvolution evolution) {
            return evolution.getThresholdHistoryChange() == HistoryEvolution.HistoryChangeType.HigherThresholdReached
                && evolution.currentThresholdNotRecentlyReached();
        }

        public Optional<QuotaThresholdNotice> build() {
            Preconditions.checkNotNull(sizeQuota);
            Preconditions.checkNotNull(countQuota);

            if (sizeThreshold.isPresent() || countThreshold.isPresent()) {
                return Optional.of(
                    new QuotaThresholdNotice(countThreshold, sizeThreshold, sizeQuota, countQuota));
            }
            return Optional.empty();
        }
    }

    public static class MessageBuilder {
        public static final String PREAMBLE = "You receive this email because you recently exceeded a threshold related " +
            "to the quotas of your email account.\n\n";
        public static final String CONCLUSION = "You need to be aware that actions leading to exceeded quotas will be denied. " +
            "This will result in a degraded service.\n" +
            "To mitigate this issue you might reach your administrator in order to increase your configured quota. " +
            "You might also delete some non important emails.";

        private final StringBuilder stringBuilder;


        public MessageBuilder() {
            this.stringBuilder = new StringBuilder();
        }

        public MessageBuilder appendSizeReport(QuotaThreshold threshold, Quota<QuotaSize> sizeQuota) {
            stringBuilder.append(String.format("You currently occupy more than %d %% of the total size allocated to you.\n" +
                    "You currently occupy %s on a total of %s allocated to you.\n\n",
                threshold.getQuotaOccupationRatioAsPercent(),
                FileUtils.byteCountToDisplaySize(sizeQuota.getUsed().asLong()),
                FileUtils.byteCountToDisplaySize(sizeQuota.getLimit().asLong())));
            return this;
        }

        public MessageBuilder appendCountReport(QuotaThreshold threshold, Quota<QuotaCount> countQuota) {
            stringBuilder.append(String.format("You currently occupy more than %d %% of the total message count allocated to you.\n" +
                    "You currently have %d messages on a total of %d allowed for you.\n\n",
                threshold.getQuotaOccupationRatioAsPercent(),
                countQuota.getUsed().asLong(),
                countQuota.getLimit().asLong()));
            return this;
        }

        public MessageBuilder appendSizeReport(Optional<QuotaThreshold> threshold, Quota<QuotaSize> sizeQuota) {
            if (threshold.isPresent()) {
                return appendSizeReport(threshold.get(), sizeQuota);
            }
            return this;
        }

        public MessageBuilder appendCountReport(Optional<QuotaThreshold> threshold, Quota<QuotaCount> countQuota) {
            if (threshold.isPresent()) {
                return appendCountReport(threshold.get(), countQuota);
            }
            return this;
        }

        public MessageBuilder appendPreamble() {
            stringBuilder.append(PREAMBLE);
            return this;
        }

        public MessageBuilder appendConclusion() {
            stringBuilder.append(CONCLUSION);
            return this;
        }

        public String build() {
            return stringBuilder.toString();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final Optional<QuotaThreshold> countThreshold;
    private final Optional<QuotaThreshold> sizeThreshold;
    private final Quota<QuotaSize> sizeQuota;
    private final Quota<QuotaCount> countQuota;

    @VisibleForTesting
    QuotaThresholdNotice(Optional<QuotaThreshold> countThreshold, Optional<QuotaThreshold> sizeThreshold,
                         Quota<QuotaSize> sizeQuota, Quota<QuotaCount> countQuota) {
        this.countThreshold = countThreshold;
        this.sizeThreshold = sizeThreshold;
        this.sizeQuota = sizeQuota;
        this.countQuota = countQuota;
    }

    public String generateReport() {
        return new MessageBuilder()
            .appendPreamble()
            .appendSizeReport(sizeThreshold, sizeQuota)
            .appendCountReport(countThreshold, countQuota)
            .appendConclusion()
            .build();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaThresholdNotice) {
            QuotaThresholdNotice that = (QuotaThresholdNotice) o;

            return Objects.equals(this.countThreshold, that.countThreshold)
                && Objects.equals(this.sizeThreshold, that.sizeThreshold)
                && Objects.equals(this.sizeQuota, that.sizeQuota)
                && Objects.equals(this.countQuota, that.countQuota);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(countThreshold, sizeThreshold, sizeQuota, countQuota);
    }

}
