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

import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class QuotaThresholdHistory {

    private final ImmutableList<QuotaThresholdChange> changes;

    public QuotaThresholdHistory() {
        this(ImmutableList.of());
    }

    public QuotaThresholdHistory(QuotaThresholdChange... changes) {
        this(Arrays.asList(changes));
    }

    public QuotaThresholdHistory(List<QuotaThresholdChange> changes) {
        this.changes = changes.stream()
            .sorted(Comparator.comparing(QuotaThresholdChange::getInstant))
            .collect(Guavate.toImmutableList());
    }

    public HistoryEvolution compareWithCurrentThreshold(QuotaThresholdChange thresholdChange, Duration gracePeriod) {
        Optional<QuotaThreshold> lastThreshold = Optional.ofNullable(Iterables.getLast(changes, null))
            .map(QuotaThresholdChange::getQuotaThreshold);

        return compareWithCurrentThreshold(thresholdChange, gracePeriod, lastThreshold.orElse(QuotaThreshold.ZERO));
    }

    private HistoryEvolution compareWithCurrentThreshold(QuotaThresholdChange thresholdChange, Duration gracePeriod, QuotaThreshold lastThreshold) {
        QuotaThreshold quotaThreshold = thresholdChange.getQuotaThreshold();
        int comparisonResult = quotaThreshold.compareTo(lastThreshold);

        if (comparisonResult < 0) {
            return HistoryEvolution.lowerThresholdReached(thresholdChange);
        }
        if (comparisonResult == 0) {
            return HistoryEvolution.noChanges();
        }
        return recentlyExceededQuotaThreshold(thresholdChange, gracePeriod)
                .map(any -> HistoryEvolution.higherThresholdReached(thresholdChange, AlreadyReachedDuringGracePeriod))
                .orElse(HistoryEvolution.higherThresholdReached(thresholdChange, NotAlreadyReachedDuringGracePeriod));
    }

    private Optional<QuotaThresholdChange> recentlyExceededQuotaThreshold(QuotaThresholdChange thresholdChange, Duration gracePeriod) {
        return changes.stream()
            .filter(change -> change.isAfter(thresholdChange.getInstant().minus(gracePeriod)))
            .filter(change -> change.getQuotaThreshold().compareTo(thresholdChange.getQuotaThreshold()) >= 0)
            .findFirst();
    }

    public QuotaThresholdHistory combineWith(QuotaThresholdChange change) {
        return new QuotaThresholdHistory(
            ImmutableList.<QuotaThresholdChange>builder()
                .addAll(changes)
                .add(change)
                .build());
    }

    public ImmutableList<QuotaThresholdChange> getChanges() {
        return changes;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaThresholdHistory) {
            QuotaThresholdHistory that = (QuotaThresholdHistory) o;

            return Objects.equals(this.changes, that.changes);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(changes);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("changes", changes)
            .toString();
    }
}
