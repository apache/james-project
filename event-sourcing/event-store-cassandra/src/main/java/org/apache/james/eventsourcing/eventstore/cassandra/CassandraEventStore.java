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

package org.apache.james.eventsourcing.eventstore.cassandra;

import java.util.List;

import javax.inject.Inject;

import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.eventsourcing.eventstore.EventStoreFailedException;
import org.apache.james.eventsourcing.eventstore.History;

import com.google.common.base.Preconditions;

public class CassandraEventStore implements EventStore {

    private final EventStoreDao eventStoreDao;

    @Inject
    public CassandraEventStore(EventStoreDao eventStoreDao) {
        this.eventStoreDao = eventStoreDao;
    }

    @Override
    public void appendAll(List<Event> events) {
        if (events.isEmpty()) {
            return;
        }
        doAppendAll(events);
    }

    public void doAppendAll(List<Event> events) {
        Preconditions.checkArgument(Event.belongsToSameAggregate(events));

        boolean success = eventStoreDao.appendAll(events).block();
        if (!success) {
            throw new EventStoreFailedException("Concurrent update to the EventStore detected");
        }
    }

    @Override
    public History getEventsOfAggregate(AggregateId aggregateId) {
        return eventStoreDao.getEventsOfAggregate(aggregateId);
    }
}
