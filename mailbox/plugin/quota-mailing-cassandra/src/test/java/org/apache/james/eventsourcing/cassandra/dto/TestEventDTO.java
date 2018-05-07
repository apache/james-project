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

package org.apache.james.eventsourcing.cassandra.dto;

import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.TestAggregateId;
import org.apache.james.eventsourcing.TestEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TestEventDTO implements EventDTO {
    private final String type;
    private final String data;
    private final int eventId;
    private final int aggregate;

    @JsonCreator
    public TestEventDTO(
            @JsonProperty("type") String type,
            @JsonProperty("data") String data,
            @JsonProperty("eventId") int eventId,
            @JsonProperty("aggregate") int aggregate) {
        this.type = type;
        this.data = data;
        this.eventId = eventId;
        this.aggregate = aggregate;
    }

    public String getType() {
        return type;
    }

    public String getData() {
        return data;
    }

    public long getEventId() {
        return eventId;
    }

    public int getAggregate() {
        return aggregate;
    }

    @JsonIgnore
    @Override
    public Event toEvent() {
        return new TestEvent(
            EventId.fromSerialized(eventId),
            TestAggregateId.testId(aggregate),
            data);
    }
}
