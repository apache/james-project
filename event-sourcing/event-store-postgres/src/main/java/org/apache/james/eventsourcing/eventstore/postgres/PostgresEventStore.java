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

package org.apache.james.eventsourcing.eventstore.postgres;

import static org.apache.james.backends.postgres.utils.PostgresUtils.UNIQUE_CONSTRAINT_VIOLATION_PREDICATE;

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.eventsourcing.eventstore.EventStoreFailedException;
import org.apache.james.eventsourcing.eventstore.History;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;

public class PostgresEventStore implements EventStore {
    private final PostgresEventStoreDAO eventStoreDAO;

    @Inject
    public PostgresEventStore(PostgresEventStoreDAO eventStoreDAO) {
        this.eventStoreDAO = eventStoreDAO;
    }

    @Override
    public Publisher<Void> appendAll(scala.collection.Iterable<Event> scalaEvents) {
        if (scalaEvents.isEmpty()) {
            return Mono.empty();
        }
        Preconditions.checkArgument(Event.belongsToSameAggregate(scalaEvents));
        List<Event> events = ImmutableList.copyOf(CollectionConverters.asJava(scalaEvents));
        Optional<EventId> snapshotId = events.stream().filter(Event::isASnapshot).map(Event::eventId).findFirst();
        return eventStoreDAO.appendAll(events, snapshotId)
            .onErrorMap(UNIQUE_CONSTRAINT_VIOLATION_PREDICATE,
                e -> new EventStoreFailedException("Concurrent update to the EventStore detected"));
    }

    @Override
    public Publisher<History> getEventsOfAggregate(AggregateId aggregateId) {
        return eventStoreDAO.getSnapshot(aggregateId)
            .flatMap(snapshotId -> eventStoreDAO.getEventsOfAggregate(aggregateId, snapshotId))
            .flatMap(history -> {
                if (history.getEventsJava().isEmpty()) {
                    return Mono.from(eventStoreDAO.getEventsOfAggregate(aggregateId));
                } else {
                    return Mono.just(history);
                }
            }).defaultIfEmpty(History.empty());
    }

    @Override
    public Publisher<Void> remove(AggregateId aggregateId) {
        return eventStoreDAO.delete(aggregateId);
    }
}
