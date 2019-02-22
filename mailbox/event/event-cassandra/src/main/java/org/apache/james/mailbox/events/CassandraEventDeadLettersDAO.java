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
import static org.apache.james.mailbox.events.tables.CassandraEventDeadLettersTable.EVENT_ID;
import static org.apache.james.mailbox.events.tables.CassandraEventDeadLettersTable.GROUP;
import static org.apache.james.mailbox.events.tables.CassandraEventDeadLettersTable.TABLE_NAME;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.event.json.EventSerializer;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraEventDeadLettersDAO {
    private final CassandraAsyncExecutor executor;
    private final EventSerializer eventSerializer;
    private final PreparedStatement insertStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement selectAllGroupStatement;
    private final PreparedStatement selectEventStatement;
    private final PreparedStatement selectEventIdsWithGroupStatement;

    @Inject
    CassandraEventDeadLettersDAO(Session session, EventSerializer eventSerializer) {
        this.executor = new CassandraAsyncExecutor(session);
        this.eventSerializer = eventSerializer;
        this.insertStatement = prepareInsertStatement(session);
        this.deleteStatement = prepareDeleteStatement(session);
        this.selectAllGroupStatement = prepareSelectAllGroupStatement(session);
        this.selectEventStatement = prepareSelectEventStatement(session);
        this.selectEventIdsWithGroupStatement = prepareSelectEventIdsWithGroupStatement(session);
    }

    private PreparedStatement prepareInsertStatement(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(GROUP, bindMarker(GROUP))
            .value(EVENT_ID, bindMarker(EVENT_ID))
            .value(EVENT, bindMarker(EVENT)));
    }

    private PreparedStatement prepareDeleteStatement(Session session) {
        return session.prepare(delete()
            .from(TABLE_NAME)
            .where(eq(GROUP, bindMarker(GROUP)))
            .and(eq(EVENT_ID, bindMarker(EVENT_ID))));
    }

    private PreparedStatement prepareSelectAllGroupStatement(Session session) {
        return session.prepare(select(GROUP)
            .from(TABLE_NAME));
    }

    private PreparedStatement prepareSelectEventStatement(Session session) {
        return session.prepare(select(EVENT)
            .from(TABLE_NAME)
            .where(eq(GROUP, bindMarker(GROUP)))
            .and(eq(EVENT_ID, bindMarker(EVENT_ID))));
    }

    private PreparedStatement prepareSelectEventIdsWithGroupStatement(Session session) {
        return session.prepare(select(EVENT_ID)
            .from(TABLE_NAME)
            .where(eq(GROUP, bindMarker(GROUP))));
    }

    Mono<Void> store(Group group, Event failedEvent) {
        return executor.executeVoid(insertStatement.bind()
                .setString(GROUP, group.asString())
                .setUUID(EVENT_ID, failedEvent.getEventId().getId())
                .setString(EVENT, eventSerializer.toJson(failedEvent)));
    }

    Mono<Void> removeEvent(Group group, Event.EventId failedEventId) {
        return executor.executeVoid(deleteStatement.bind()
                .setString(GROUP, group.asString())
                .setUUID(EVENT_ID, failedEventId.getId()));
    }

    Mono<Event> retrieveFailedEvent(Group group, Event.EventId failedEventId) {
        return executor.executeSingleRow(selectEventStatement.bind()
                .setString(GROUP, group.asString())
                .setUUID(EVENT_ID, failedEventId.getId()))
                .map(row -> deserializeEvent(row.getString(EVENT)));
    }

    Flux<Event.EventId> retrieveEventIdsWithGroup(Group group) {
        return executor.executeRows(selectEventIdsWithGroupStatement.bind()
                .setString(GROUP, group.asString()))
            .map(row -> Event.EventId.of(row.getUUID(EVENT_ID)));
    }

    Flux<Group> retrieveAllGroups() {
        return executor.executeRows(selectAllGroupStatement.bind())
            .map(Throwing.function(row -> Group.deserialize(row.getString(GROUP))))
            .distinct();
    }

    private Event deserializeEvent(String serializedEvent) {
        return eventSerializer.fromJson(serializedEvent).get();
    }
}
