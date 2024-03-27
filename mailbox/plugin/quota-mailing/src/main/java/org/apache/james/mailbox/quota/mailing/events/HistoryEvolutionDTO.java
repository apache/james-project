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

package org.apache.james.mailbox.quota.mailing.events;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.mailbox.quota.model.HistoryEvolution;
import org.apache.james.mailbox.quota.model.QuotaThreshold;
import org.apache.james.mailbox.quota.model.QuotaThresholdChange;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Booleans;

public record HistoryEvolutionDTO(@JsonProperty("change") HistoryEvolution.HistoryChangeType change,
                                  @JsonProperty("recentness") Optional<HistoryEvolution.HighestThresholdRecentness> recentness,
                                  @JsonProperty("threshold") Optional<Double> threshold,
                                  @JsonProperty("instant") Optional<Long> instant) {

    @JsonIgnore
    public static HistoryEvolutionDTO toDto(HistoryEvolution historyEvolution) {
        return new HistoryEvolutionDTO(
            historyEvolution.getThresholdHistoryChange(),
            historyEvolution.getRecentness(),
            historyEvolution.getThresholdChange()
                .map(QuotaThresholdChange::getQuotaThreshold)
                .map(QuotaThreshold::getQuotaOccupationRatio),
            historyEvolution.getThresholdChange()
                .map(QuotaThresholdChange::getInstant)
                .map(Instant::toEpochMilli));
    }

    @JsonIgnore
    public HistoryEvolution toHistoryEvolution() {
        Preconditions.checkState(Booleans.countTrue(threshold.isPresent(), instant.isPresent()) != 1,
            "threshold and instant needs to be both set, or both unset. Mixed states not allowed.");

        Optional<QuotaThresholdChange> quotaThresholdChange = threshold
            .map(QuotaThreshold::new)
            .map(value -> new QuotaThresholdChange(value, Instant.ofEpochMilli(instant.get())));

        return new HistoryEvolution(
            change,
            recentness,
            quotaThresholdChange);
    }
}