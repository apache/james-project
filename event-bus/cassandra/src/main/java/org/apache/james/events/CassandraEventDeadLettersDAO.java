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

package org.apache.james.events;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.events.tables.CassandraEventDeadLettersTable.EVENT;
import static org.apache.james.events.tables.CassandraEventDeadLettersTable.GROUP;
import static org.apache.james.events.tables.CassandraEventDeadLettersTable.INSERTION_ID;
import static org.apache.james.events.tables.CassandraEventDeadLettersTable.TABLE_NAME;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraEventDeadLettersDAO {
    private final CassandraAsyncExecutor executor;
    private final EventSerializer eventSerializer;
    private final PreparedStatement insertStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement deleteAllEventsOfAGroupStatement;
    private final PreparedStatement selectEventStatement;
    private final PreparedStatement selectEventIdsWithGroupStatement;
    private final PreparedStatement containEventsStatement;

    @Inject
    public CassandraEventDeadLettersDAO(CqlSession session, EventSerializer eventSerializer) {
        this.executor = new CassandraAsyncExecutor(session);
        this.eventSerializer = eventSerializer;
        this.insertStatement = prepareInsertStatement(session);
        this.deleteStatement = prepareDeleteStatement(session);
        this.deleteAllEventsOfAGroupStatement = prepareDeleteAllEventsOfAGroupStatement(session);
        this.selectEventStatement = prepareSelectEventStatement(session);
        this.selectEventIdsWithGroupStatement = prepareSelectInsertionIdsWithGroupStatement(session);
        this.containEventsStatement = prepareContainEventStatement(session);
    }

    private PreparedStatement prepareInsertStatement(CqlSession session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(GROUP, bindMarker(GROUP))
            .value(INSERTION_ID, bindMarker(INSERTION_ID))
            .value(EVENT, bindMarker(EVENT))
            .build());
    }

    private PreparedStatement prepareDeleteStatement(CqlSession session) {
        return session.prepare(deleteFrom(TABLE_NAME)
            .whereColumn(GROUP).isEqualTo(bindMarker(GROUP))
            .whereColumn(INSERTION_ID).isEqualTo(bindMarker(INSERTION_ID))
            .build());
    }

    private PreparedStatement prepareDeleteAllEventsOfAGroupStatement(CqlSession session) {
        return session.prepare(deleteFrom(TABLE_NAME)
            .whereColumn(GROUP).isEqualTo(bindMarker(GROUP))
            .build());
    }

    private PreparedStatement prepareSelectEventStatement(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .column(EVENT)
            .whereColumn(GROUP).isEqualTo(bindMarker(GROUP))
            .whereColumn(INSERTION_ID).isEqualTo(bindMarker(INSERTION_ID))
            .build());
    }

    private PreparedStatement prepareSelectInsertionIdsWithGroupStatement(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .column(INSERTION_ID)
            .whereColumn(GROUP).isEqualTo(bindMarker(GROUP))
            .build());
    }

    private PreparedStatement prepareContainEventStatement(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .column(EVENT)
            .limit(1)
            .build());
    }

    Mono<Void> store(Group group, Event failedEvent, EventDeadLetters.InsertionId insertionId) {
        return executor.executeVoid(insertStatement.bind()
                .setString(GROUP, group.asString())
                .setUuid(INSERTION_ID, insertionId.getId())
                .setString(EVENT, eventSerializer.toJson(failedEvent)));
    }

    Mono<Void> removeEvent(Group group, EventDeadLetters.InsertionId failedInsertionId) {
        return executor.executeVoid(deleteStatement.bind()
                .setString(GROUP, group.asString())
                .setUuid(INSERTION_ID, failedInsertionId.getId()));
    }

    Mono<Void> removeEvents(Group group) {
        return executor.executeVoid(deleteAllEventsOfAGroupStatement.bind()
            .setString(GROUP, group.asString()));
    }

    Mono<Event> retrieveFailedEvent(Group group, EventDeadLetters.InsertionId insertionId) {
        return executor.executeSingleRow(selectEventStatement.bind()
                .setString(GROUP, group.asString())
                .setUuid(INSERTION_ID, insertionId.getId()))
            .map(row -> deserializeEvent(row.getString(EVENT)));
    }

    Flux<EventDeadLetters.InsertionId> retrieveInsertionIdsWithGroup(Group group) {
        return executor.executeRows(selectEventIdsWithGroupStatement.bind()
                .setString(GROUP, group.asString()))
            .map(row -> EventDeadLetters.InsertionId.of(row.getUuid(INSERTION_ID)));
    }

    Mono<Boolean> containEvents() {
        return executor.executeReturnExists(containEventsStatement.bind());
    }

    private Event deserializeEvent(String serializedEvent) {
        return eventSerializer.asEvent(serializedEvent);
    }
}
