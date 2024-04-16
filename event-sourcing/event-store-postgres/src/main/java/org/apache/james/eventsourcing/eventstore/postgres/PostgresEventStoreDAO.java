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

import static org.apache.james.eventsourcing.eventstore.postgres.PostgresEventStoreModule.PostgresEventStoreTable.AGGREGATE_ID;
import static org.apache.james.eventsourcing.eventstore.postgres.PostgresEventStoreModule.PostgresEventStoreTable.EVENT;
import static org.apache.james.eventsourcing.eventstore.postgres.PostgresEventStoreModule.PostgresEventStoreTable.EVENT_ID;
import static org.apache.james.eventsourcing.eventstore.postgres.PostgresEventStoreModule.PostgresEventStoreTable.SNAPSHOT;
import static org.apache.james.eventsourcing.eventstore.postgres.PostgresEventStoreModule.PostgresEventStoreTable.TABLE_NAME;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.eventstore.History;
import org.apache.james.eventsourcing.eventstore.JsonEventSerializer;
import org.apache.james.util.ReactorUtils;
import org.jooq.JSON;
import org.jooq.Record;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;

public class PostgresEventStoreDAO {
    private PostgresExecutor postgresExecutor;
    private JsonEventSerializer jsonEventSerializer;

    @Inject
    public PostgresEventStoreDAO(PostgresExecutor postgresExecutor, JsonEventSerializer jsonEventSerializer) {
        this.postgresExecutor = postgresExecutor;
        this.jsonEventSerializer = jsonEventSerializer;
    }

    public Mono<Void> appendAll(List<Event> events, Optional<EventId> lastSnapshot) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME, AGGREGATE_ID, EVENT_ID, EVENT)
                .valuesOfRecords(events.stream().map(event -> dslContext.newRecord(AGGREGATE_ID, EVENT_ID, EVENT)
                        .value1(event.getAggregateId().asAggregateKey())
                        .value2(event.eventId().serialize())
                        .value3(convertToJooqJson(event)))
                    .collect(ImmutableList.toImmutableList()))))
            .then(lastSnapshot.map(eventId -> insertSnapshot(events.iterator().next().getAggregateId(), eventId)).orElse(Mono.empty()));
    }

    private Mono<Void> insertSnapshot(AggregateId aggregateId, EventId snapshotId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.update(TABLE_NAME)
            .set(SNAPSHOT, snapshotId.serialize())
            .where(AGGREGATE_ID.eq(aggregateId.asAggregateKey()))));
    }

    private JSON convertToJooqJson(Event event) {
        try {
            return JSON.json(jsonEventSerializer.serialize(event));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public Mono<EventId> getSnapshot(AggregateId aggregateId) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(SNAPSHOT)
            .from(TABLE_NAME)
            .where(AGGREGATE_ID.eq(aggregateId.asAggregateKey()))
            .limit(1)))
            .map(record -> EventId.fromSerialized(Optional.ofNullable(record.get(SNAPSHOT)).orElse(0)));
    }

    public Mono<History> getEventsOfAggregate(AggregateId aggregateId, EventId snapshotId) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectFrom(TABLE_NAME)
                .where(AGGREGATE_ID.eq(aggregateId.asAggregateKey()))
                .and(EVENT_ID.greaterOrEqual(snapshotId.value()))
                .orderBy(EVENT_ID)))
            .concatMap(this::toEvent)
            .collect(ImmutableList.toImmutableList())
            .map(this::asHistory);
    }

    public Mono<History> getEventsOfAggregate(AggregateId aggregateId) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectFrom(TABLE_NAME)
                .where(AGGREGATE_ID.eq(aggregateId.asAggregateKey()))
                .orderBy(EVENT_ID)))
            .concatMap(this::toEvent)
            .collect(ImmutableList.toImmutableList())
            .map(this::asHistory);
    }

    public Mono<Void> delete(AggregateId aggregateId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(AGGREGATE_ID.eq(aggregateId.asAggregateKey()))));
    }

    private History asHistory(List<Event> events) {
        return History.of(CollectionConverters.asScala(events).toList());
    }

    private Mono<Event> toEvent(Record record) {
        return Mono.fromCallable(() -> jsonEventSerializer.deserialize(record.get(EVENT).data()))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
    }
}
