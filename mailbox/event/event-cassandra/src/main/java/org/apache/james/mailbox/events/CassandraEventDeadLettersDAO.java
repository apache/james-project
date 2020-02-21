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

package org.apache.james.mailbox.events;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.mailbox.events.tables.CassandraEventDeadLettersTable.EVENT;
import static org.apache.james.mailbox.events.tables.CassandraEventDeadLettersTable.GROUP;
import static org.apache.james.mailbox.events.tables.CassandraEventDeadLettersTable.INSERTION_ID;
import static org.apache.james.mailbox.events.tables.CassandraEventDeadLettersTable.TABLE_NAME;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.event.json.EventSerializer;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraEventDeadLettersDAO {
    private final CassandraAsyncExecutor executor;
    private final EventSerializer eventSerializer;
    private final PreparedStatement insertStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement selectEventStatement;
    private final PreparedStatement selectEventIdsWithGroupStatement;
    private final PreparedStatement containEventsStatement;

    @Inject
    CassandraEventDeadLettersDAO(Session session, EventSerializer eventSerializer) {
        this.executor = new CassandraAsyncExecutor(session);
        this.eventSerializer = eventSerializer;
        this.insertStatement = prepareInsertStatement(session);
        this.deleteStatement = prepareDeleteStatement(session);
        this.selectEventStatement = prepareSelectEventStatement(session);
        this.selectEventIdsWithGroupStatement = prepareSelectInsertionIdsWithGroupStatement(session);
        this.containEventsStatement = prepareContainEventStatement(session);
    }

    private PreparedStatement prepareInsertStatement(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(GROUP, bindMarker(GROUP))
            .value(INSERTION_ID, bindMarker(INSERTION_ID))
            .value(EVENT, bindMarker(EVENT)));
    }

    private PreparedStatement prepareDeleteStatement(Session session) {
        return session.prepare(delete()
            .from(TABLE_NAME)
            .where(eq(GROUP, bindMarker(GROUP)))
            .and(eq(INSERTION_ID, bindMarker(INSERTION_ID))));
    }

    private PreparedStatement prepareSelectEventStatement(Session session) {
        return session.prepare(select(EVENT)
            .from(TABLE_NAME)
            .where(eq(GROUP, bindMarker(GROUP)))
            .and(eq(INSERTION_ID, bindMarker(INSERTION_ID))));
    }

    private PreparedStatement prepareSelectInsertionIdsWithGroupStatement(Session session) {
        return session.prepare(select(INSERTION_ID)
            .from(TABLE_NAME)
            .where(eq(GROUP, bindMarker(GROUP))));
    }

    private PreparedStatement prepareContainEventStatement(Session session) {
        return session.prepare(select(EVENT)
            .from(TABLE_NAME)
            .limit(1));
    }

    Mono<Void> store(Group group, Event failedEvent, EventDeadLetters.InsertionId insertionId) {
        return executor.executeVoid(insertStatement.bind()
                .setString(GROUP, group.asString())
                .setUUID(INSERTION_ID, insertionId.getId())
                .setString(EVENT, eventSerializer.toJson(failedEvent)));
    }

    Mono<Void> removeEvent(Group group, EventDeadLetters.InsertionId failedInsertionId) {
        return executor.executeVoid(deleteStatement.bind()
                .setString(GROUP, group.asString())
                .setUUID(INSERTION_ID, failedInsertionId.getId()));
    }

    Mono<Event> retrieveFailedEvent(Group group, EventDeadLetters.InsertionId insertionId) {
        return executor.executeSingleRow(selectEventStatement.bind()
                .setString(GROUP, group.asString())
                .setUUID(INSERTION_ID, insertionId.getId()))
            .map(row -> deserializeEvent(row.getString(EVENT)));
    }

    Flux<EventDeadLetters.InsertionId> retrieveInsertionIdsWithGroup(Group group) {
        return executor.executeRows(selectEventIdsWithGroupStatement.bind()
                .setString(GROUP, group.asString()))
            .map(row -> EventDeadLetters.InsertionId.of(row.getUUID(INSERTION_ID)));
    }

    Mono<Boolean> containEvents() {
        return executor.executeReturnExists(containEventsStatement.bind());
    }

    private Event deserializeEvent(String serializedEvent) {
        return eventSerializer.fromJson(serializedEvent).get();
    }
}
