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

import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.eventstore.dto.EventDTO;
import org.apache.james.mailbox.quota.mailing.aggregates.UserQuotaThresholds;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public record QuotaThresholdChangedEventDTO(@JsonProperty("type") String type,
                                            @JsonProperty("eventId") int eventId,
                                            @JsonProperty("aggregateId") String aggregateId,
                                            @JsonProperty("sizeQuota") QuotaDTO sizeQuota,
                                            @JsonProperty("countQuota") QuotaDTO countQuota,
                                            @JsonProperty("sizeEvolution") HistoryEvolutionDTO sizeEvolution,
                                            @JsonProperty("countEvolution") HistoryEvolutionDTO countEvolution) implements EventDTO {

    @JsonIgnore
    public static QuotaThresholdChangedEventDTO from(QuotaThresholdChangedEvent event, String type) {
        return new QuotaThresholdChangedEventDTO(
            type,
            event.eventId().serialize(),
            event.getAggregateId().asAggregateKey(),
            QuotaDTO.from(event.getSizeQuota()),
            QuotaDTO.from(event.getCountQuota()),
            HistoryEvolutionDTO.toDto(event.getSizeHistoryEvolution()),
            HistoryEvolutionDTO.toDto(event.getCountHistoryEvolution()));
    }

    @JsonIgnore
    public QuotaThresholdChangedEvent toEvent() {
        return new QuotaThresholdChangedEvent(
            EventId.fromSerialized(eventId),
            sizeEvolution.toHistoryEvolution(),
            countEvolution.toHistoryEvolution(),
            sizeQuota.asSizeQuota(),
            countQuota.asCountQuota(),
            UserQuotaThresholds.Id.fromKey(aggregateId));
    }

    @Override
    @JsonIgnore
    public String getType() {
        return type;
    }
}