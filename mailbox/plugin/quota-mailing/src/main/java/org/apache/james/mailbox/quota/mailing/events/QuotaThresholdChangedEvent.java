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

import java.util.Objects;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.quota.mailing.aggregates.UserQuotaThresholds;
import org.apache.james.mailbox.quota.model.HistoryEvolution;

public class QuotaThresholdChangedEvent implements Event {

    private final EventId eventId;
    private final HistoryEvolution sizeHistoryEvolution;
    private final HistoryEvolution countHistoryEvolution;
    private final Quota<QuotaSizeLimit, QuotaSizeUsage> sizeQuota;
    private final Quota<QuotaCountLimit, QuotaCountUsage> countQuota;
    private final UserQuotaThresholds.Id aggregateId;

    public QuotaThresholdChangedEvent(EventId eventId, HistoryEvolution sizeHistoryEvolution, HistoryEvolution countHistoryEvolution, Quota<QuotaSizeLimit, QuotaSizeUsage> sizeQuota, Quota<QuotaCountLimit, QuotaCountUsage> countQuota, UserQuotaThresholds.Id aggregateId) {
        this.eventId = eventId;
        this.sizeHistoryEvolution = sizeHistoryEvolution;
        this.countHistoryEvolution = countHistoryEvolution;
        this.sizeQuota = sizeQuota;
        this.countQuota = countQuota;
        this.aggregateId = aggregateId;
    }

    public HistoryEvolution getSizeHistoryEvolution() {
        return sizeHistoryEvolution;
    }

    public HistoryEvolution getCountHistoryEvolution() {
        return countHistoryEvolution;
    }

    public Quota<QuotaSizeLimit, QuotaSizeUsage> getSizeQuota() {
        return sizeQuota;
    }

    public Quota<QuotaCountLimit, QuotaCountUsage> getCountQuota() {
        return countQuota;
    }

    @Override
    public EventId eventId() {
        return eventId;
    }

    @Override
    public UserQuotaThresholds.Id getAggregateId() {
        return aggregateId;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaThresholdChangedEvent) {
            QuotaThresholdChangedEvent that = (QuotaThresholdChangedEvent) o;

            return Objects.equals(this.eventId, that.eventId)
                && Objects.equals(this.sizeHistoryEvolution, that.sizeHistoryEvolution)
                && Objects.equals(this.countHistoryEvolution, that.countHistoryEvolution)
                && Objects.equals(this.sizeQuota, that.sizeQuota)
                && Objects.equals(this.countQuota, that.countQuota)
                && Objects.equals(this.aggregateId, that.aggregateId);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(eventId, sizeHistoryEvolution, countHistoryEvolution, sizeQuota, countQuota, aggregateId);
    }
}
