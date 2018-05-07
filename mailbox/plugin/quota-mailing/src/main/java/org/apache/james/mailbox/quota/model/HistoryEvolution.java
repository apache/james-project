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

import java.util.Objects;
import java.util.Optional;

import com.google.common.base.MoreObjects;

public class HistoryEvolution {

    public static HistoryEvolution noChanges() {
        return new HistoryEvolution(HistoryChangeType.NoChange,
            Optional.empty(),
            Optional.empty());
    }

    public static HistoryEvolution lowerThresholdReached(QuotaThresholdChange currentThreshold) {
        return new HistoryEvolution(HistoryChangeType.LowerThresholdReached,
            Optional.empty(),
            Optional.of(currentThreshold));
    }

    public static HistoryEvolution higherThresholdReached(QuotaThresholdChange currentThreshold, HighestThresholdRecentness recentness) {
        return new HistoryEvolution(HistoryChangeType.HigherThresholdReached,
            Optional.of(recentness),
            Optional.of(currentThreshold));
    }

    public enum HistoryChangeType {
        HigherThresholdReached,
        NoChange,
        LowerThresholdReached
    }

    public enum HighestThresholdRecentness {
        AlreadyReachedDuringGracePriod,
        NotAlreadyReachedDuringGracePeriod
    }

    private final HistoryChangeType thresholdHistoryChange;
    private final Optional<HighestThresholdRecentness> recentness;
    private final Optional<QuotaThresholdChange> thresholdChange;

    public HistoryEvolution(HistoryChangeType thresholdHistoryChange, Optional<HighestThresholdRecentness> recentness, Optional<QuotaThresholdChange> thresholdChange) {
        this.thresholdHistoryChange = thresholdHistoryChange;
        this.recentness = recentness;
        this.thresholdChange = thresholdChange;
    }

    public boolean isChange() {
        return thresholdHistoryChange != HistoryChangeType.NoChange;
    }

    public boolean currentThresholdNotRecentlyReached() {
        return recentness
            .map(value -> value == HighestThresholdRecentness.NotAlreadyReachedDuringGracePeriod)
            .orElse(false);
    }

    public Optional<QuotaThresholdChange> getThresholdChange() {
        return thresholdChange;
    }

    public HistoryChangeType getThresholdHistoryChange() {
        return thresholdHistoryChange;
    }

    public Optional<HighestThresholdRecentness> getRecentness() {
        return recentness;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof HistoryEvolution) {
            HistoryEvolution that = (HistoryEvolution) o;

            return Objects.equals(this.thresholdHistoryChange, that.thresholdHistoryChange)
                && Objects.equals(this.recentness, that.recentness)
                && Objects.equals(this.thresholdChange, that.thresholdChange);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(thresholdHistoryChange, recentness, thresholdChange);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("thresholdHistoryChange", thresholdHistoryChange)
            .add("recentness", recentness)
            .add("thresholdChange", thresholdChange)
            .toString();
    }
}
