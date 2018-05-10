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

package org.apache.james.eventsourcing.eventstore;

import java.util.List;

import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.eventsourcing.Event;

import com.google.common.collect.ImmutableList;

public interface EventStore {

    default void append(Event event) {
        appendAll(ImmutableList.of(event));
    }

    default void appendAll(Event... events) {
        appendAll(ImmutableList.copyOf(events));
    }

    /**
     * This method should check that no input event has an id already stored and throw otherwise
     * It should also check that all events belong to the same aggregate
     */
    void appendAll(List<Event> events);

    History getEventsOfAggregate(AggregateId aggregateId);

}
