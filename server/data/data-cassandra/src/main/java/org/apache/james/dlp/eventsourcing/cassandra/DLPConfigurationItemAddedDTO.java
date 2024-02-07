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

package org.apache.james.dlp.eventsourcing.cassandra;

import static org.apache.james.dlp.eventsourcing.cassandra.DLPConfigurationItemDTO.fromDTOs;

import java.util.List;
import java.util.Objects;

import org.apache.james.dlp.eventsourcing.aggregates.DLPAggregateId;
import org.apache.james.dlp.eventsourcing.events.ConfigurationItemsAdded;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.eventstore.dto.EventDTO;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

class DLPConfigurationItemAddedDTO implements EventDTO {

    public static DLPConfigurationItemAddedDTO from(ConfigurationItemsAdded event, String type) {
        return new DLPConfigurationItemAddedDTO(
            type,
            event.eventId().serialize(),
            event.getAggregateId().asAggregateKey(),
            DLPConfigurationItemDTO.from(event.getRules()));
    }

    private final String type;
    private final int eventId;
    private final String aggregateId;
    private final List<DLPConfigurationItemDTO> configurationItems;

    @JsonCreator
    private DLPConfigurationItemAddedDTO(
            @JsonProperty("type") String type,
            @JsonProperty("eventId") int eventId,
            @JsonProperty("aggregateId") String aggregateId,
            @JsonProperty("configurationItems") List<DLPConfigurationItemDTO> configurationItems) {
        Preconditions.checkNotNull(type);
        Preconditions.checkNotNull(aggregateId);
        Preconditions.checkNotNull(configurationItems);
        Preconditions.checkArgument(!configurationItems.isEmpty());

        this.type = type;
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.configurationItems = configurationItems;
    }

    public String getType() {
        return type;
    }

    public long getEventId() {
        return eventId;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public List<DLPConfigurationItemDTO> getConfigurationItems() {
        return configurationItems;
    }

    @JsonIgnore
    public ConfigurationItemsAdded toEvent() {
        return new ConfigurationItemsAdded(
            DLPAggregateId.parse(aggregateId),
            EventId.fromSerialized(eventId),
            fromDTOs(configurationItems));
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof DLPConfigurationItemAddedDTO) {
            DLPConfigurationItemAddedDTO that = (DLPConfigurationItemAddedDTO) o;

            return Objects.equals(this.eventId, that.eventId)
                && Objects.equals(this.type, that.type)
                && Objects.equals(this.aggregateId, that.aggregateId)
                && Objects.equals(this.configurationItems, that.configurationItems);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(type, eventId, aggregateId, configurationItems);
    }
}
