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

package org.apache.james.queue.rabbitmq.view.cassandra.configuration;

import java.time.Duration;
import java.util.Objects;

import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.eventstore.dto.EventDTO;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

class ConfigurationChangedDTO implements EventDTO {

    static ConfigurationChangedDTO from(ConfigurationChanged configurationChanged, String type) {
        Preconditions.checkNotNull(configurationChanged);

        CassandraMailQueueViewConfiguration configuration = configurationChanged.getConfiguration();
        return new ConfigurationChangedDTO(
            configurationChanged.eventId().serialize(),
            configurationChanged.getAggregateId().asAggregateKey(),
            type,
            configuration.getBucketCount(),
            configuration.getUpdateBrowseStartPace(),
            configuration.getSliceWindow());
    }

    static ConfigurationChangedDTO from(ConfigurationChanged configurationChanged) {
        return from(configurationChanged, CassandraMailQueueViewConfigurationModule.TYPE_NAME);
    }

    private final int eventId;
    private final String aggregateKey;
    private final String type;
    private final int bucketCount;
    private final int updateBrowseStartPace;
    private final Duration sliceWindow;

    @JsonCreator
    ConfigurationChangedDTO(
        @JsonProperty("eventId") int eventId,
        @JsonProperty("aggregateKey") String aggregateKey,
        @JsonProperty("type") String type,
        @JsonProperty("bucketCount") int bucketCount,
        @JsonProperty("updateBrowseStartPace") int updateBrowseStartPace,
        @JsonProperty("sliceWindow") Duration sliceWindow) {

        this.eventId = eventId;
        this.aggregateKey = aggregateKey;
        this.type = type;
        this.bucketCount = bucketCount;
        this.updateBrowseStartPace = updateBrowseStartPace;
        this.sliceWindow = sliceWindow;
    }

    @JsonIgnore
    public ConfigurationChanged toEvent() {
        return new ConfigurationChanged(
            () -> aggregateKey,
            EventId.fromSerialized(eventId),
            CassandraMailQueueViewConfiguration.builder()
                .bucketCount(bucketCount)
                .updateBrowseStartPace(updateBrowseStartPace)
                .sliceWindow(sliceWindow)
                .build());
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

    public int getBucketCount() {
        return bucketCount;
    }

    public int getUpdateBrowseStartPace() {
        return updateBrowseStartPace;
    }

    public Duration getSliceWindow() {
        return sliceWindow;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ConfigurationChangedDTO) {
            ConfigurationChangedDTO that = (ConfigurationChangedDTO) o;

            return Objects.equals(this.eventId, that.eventId)
                && Objects.equals(this.aggregateKey, that.aggregateKey)
                && Objects.equals(this.type, that.type)
                && Objects.equals(this.bucketCount, that.bucketCount)
                && Objects.equals(this.updateBrowseStartPace, that.updateBrowseStartPace)
                && Objects.equals(this.sliceWindow, that.sliceWindow);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(eventId, aggregateKey, type, bucketCount, updateBrowseStartPace, sliceWindow);
    }
}
