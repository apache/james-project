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
package org.apache.james.eventsourcing.eventstore.jpa;


import static org.apache.james.eventsourcing.eventstore.jpa.model.JPAEvent.DELETE_AGGREGATE_QUERY;
import static org.apache.james.eventsourcing.eventstore.jpa.model.JPAEvent.SELECT_AGGREGATE_QUERY;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;

import org.apache.james.backends.jpa.TransactionRunner;
import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.eventsourcing.eventstore.EventStoreFailedException;
import org.apache.james.eventsourcing.eventstore.History;
import org.apache.james.eventsourcing.eventstore.JsonEventSerializer;
import org.apache.james.eventsourcing.eventstore.jpa.model.JPAEvent;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;
import scala.collection.Iterable;
import scala.collection.JavaConverters;

@PersistenceUnit(unitName = "James")
public class JPAEventStore implements EventStore {

    /**
     * The entity manager to access the database.
     */
    private EntityManagerFactory entityManagerFactory;

    /**
     * The JSON serializer to serialize the event data.
     */
    private JsonEventSerializer jsonEventSerializer;

    /**
     * Constructs a JPAEventStore.
     */
    @Inject
    public JPAEventStore(EntityManagerFactory entityManagerFactory, JsonEventSerializer jsonEventSerializer) {
        this.jsonEventSerializer = jsonEventSerializer;
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public Publisher<Void> appendAll(Iterable<Event> events) {
        if (events.isEmpty()) {
            return Mono.empty();
        }
        Preconditions.checkArgument(Event.belongsToSameAggregate(events));
        AggregateId aggregateId = events.head().getAggregateId();
        return Mono.fromRunnable(() -> new TransactionRunner(entityManagerFactory).runAndHandleException(
            entityManager ->
                JavaConverters.asJava(events).forEach(Throwing.consumer(e -> {
                    JPAEvent jpaEvent = new JPAEvent(aggregateId, e.eventId(), jsonEventSerializer.serialize(e));
                    entityManager.persist(jpaEvent);
            })),
            exception -> {
                EventStoreFailedException esfe = new EventStoreFailedException("Unable to add events");
                esfe.initCause(exception);
                throw esfe;
            }));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Publisher<History> getEventsOfAggregate(AggregateId aggregateId) {
        Preconditions.checkNotNull(aggregateId);
        return Mono.fromSupplier(() -> new TransactionRunner(entityManagerFactory).runAndRetrieveResult(
            entityManager -> History.of(
                (Event[]) entityManager.createNamedQuery(SELECT_AGGREGATE_QUERY)
                .setParameter("aggregateId", aggregateId.asAggregateKey())
                .getResultStream()
                .map(Throwing.function(e -> jsonEventSerializer.deserialize(((JPAEvent) e).getEvent())))
                .toArray(Event[]::new))));
    }

    @Override
    public Publisher<Void> remove(AggregateId aggregateId) {
        return Mono.fromSupplier(() -> new TransactionRunner(entityManagerFactory).runAndRetrieveResult(
            entityManager -> {
                entityManager.createNamedQuery(DELETE_AGGREGATE_QUERY)
                    .setParameter("aggregateId", aggregateId.asAggregateKey())
                    .executeUpdate();
                return null;
            }));
    }

    @VisibleForTesting
    protected void removeAll() {
        new TransactionRunner(entityManagerFactory).runAndRetrieveResult(
            entityManager -> entityManager.createQuery("DELETE FROM JPAEvent").executeUpdate());
    }
}
