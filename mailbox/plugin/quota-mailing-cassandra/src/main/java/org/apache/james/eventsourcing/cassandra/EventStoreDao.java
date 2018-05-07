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

package org.apache.james.eventsourcing.cassandra;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.eventsourcing.cassandra.CassandraEventStoreTable.AGGREGATE_ID;
import static org.apache.james.eventsourcing.cassandra.CassandraEventStoreTable.EVENT;
import static org.apache.james.eventsourcing.cassandra.CassandraEventStoreTable.EVENTS_TABLE;
import static org.apache.james.eventsourcing.cassandra.CassandraEventStoreTable.EVENT_ID;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventStore;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.steveash.guavate.Guavate;

public class EventStoreDao {
    private final CassandraUtils cassandraUtils;
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insert;
    private final PreparedStatement select;
    private final JsonEventSerializer jsonEventSerializer;

    @Inject
    public EventStoreDao(Session session, CassandraUtils cassandraUtils, JsonEventSerializer jsonEventSerializer) {
        this.cassandraUtils = cassandraUtils;
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.jsonEventSerializer = jsonEventSerializer;
        this.insert = prepareInsert(session);
        this.select = prepareSelect(session);
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(EVENTS_TABLE)
            .value(AGGREGATE_ID, bindMarker(AGGREGATE_ID))
            .value(EVENT_ID, bindMarker(EVENT_ID))
            .value(EVENT, bindMarker(EVENT))
            .ifNotExists());
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select()
            .from(EVENTS_TABLE)
            .where(eq(AGGREGATE_ID, bindMarker(AGGREGATE_ID))));
    }

    public CompletableFuture<Boolean> appendAll(List<Event> events) {
        BatchStatement batch = new BatchStatement();
        events.forEach(event -> batch.add(insertEvent(event)));
        return cassandraAsyncExecutor.executeReturnApplied(batch);
    }

    private BoundStatement insertEvent(Event event) {
        try {
            return insert
                .bind()
                .setString(AGGREGATE_ID, event.getAggregateId().asAggregateKey())
                .setInt(EVENT_ID, event.eventId().serialize())
                .setString(EVENT, jsonEventSerializer.serialize(event));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public EventStore.History getEventsOfAggregate(AggregateId aggregateId) {
        return toHistory(
            cassandraAsyncExecutor.execute(
                select.bind()
                    .setString(AGGREGATE_ID, aggregateId.asAggregateKey()))
                .join());
    }

    private EventStore.History toHistory(ResultSet resultSet) {
        List<Event> events = cassandraUtils.convertToStream(resultSet)
            .map(this::toEvent)
            .collect(Guavate.toImmutableList());
        return EventStore.History.of(events);
    }

    private Event toEvent(Row row) {
        try {
            return jsonEventSerializer.deserialize(row.getString(EVENT));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
