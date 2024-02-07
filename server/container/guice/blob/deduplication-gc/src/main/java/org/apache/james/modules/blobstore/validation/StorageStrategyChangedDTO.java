/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.modules.blobstore.validation;

import java.util.Objects;

import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.eventstore.dto.EventDTO;
import org.apache.james.server.blob.deduplication.StorageStrategy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

class StorageStrategyChangedDTO implements EventDTO {

    static StorageStrategyChangedDTO from(StorageStrategyChanged storageStrategyChanged, String type) {
        Preconditions.checkNotNull(storageStrategyChanged);

        StorageStrategy storageStrategy = storageStrategyChanged.getStorageStrategy();
        return new StorageStrategyChangedDTO(
                storageStrategyChanged.eventId().serialize(),
                storageStrategyChanged.getAggregateId().asAggregateKey(),
                type,
                storageStrategy.name());
    }

    static StorageStrategyChangedDTO from(StorageStrategyChanged storageStrategyChanged) {
        return from(storageStrategyChanged, StorageStrategyModule.TYPE_NAME);
    }

    private final int eventId;
    private final String aggregateKey;
    private final String type;
    private final String storageStrategy;

    @JsonCreator
    StorageStrategyChangedDTO(
            @JsonProperty("eventId") int eventId,
            @JsonProperty("aggregateKey") String aggregateKey,
            @JsonProperty("type") String type,
            @JsonProperty("storageStrategy") String storageStrategy) {
        this.eventId = eventId;
        this.aggregateKey = aggregateKey;
        this.type = type;
        this.storageStrategy = storageStrategy;
    }

    @JsonIgnore
    public StorageStrategyChanged toEvent() {
        return new StorageStrategyChanged(
            EventId.fromSerialized(eventId),
            () -> aggregateKey,
            StorageStrategy.valueOf(storageStrategy));
    }

    public int getEventId() {
        return eventId;
    }

    public String getAggregateKey() {
        return aggregateKey;
    }

    public String getType() {
        return type;
    }

    public String getStorageStrategy() {
        return storageStrategy;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof StorageStrategyChangedDTO) {
            StorageStrategyChangedDTO that = (StorageStrategyChangedDTO) o;

            return Objects.equals(this.eventId, that.eventId)
                && Objects.equals(this.aggregateKey, that.aggregateKey)
                && Objects.equals(this.type, that.type)
                && Objects.equals(this.storageStrategy, that.storageStrategy);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(eventId, aggregateKey, type, storageStrategy);
    }
}
