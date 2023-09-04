/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.mailbox.cassandra.mail;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIdTable.THREAD_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MESSAGE_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraThreadTable.BASE_SUBJECT;
import static org.apache.james.mailbox.cassandra.table.CassandraThreadTable.MIME_MESSAGE_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraThreadTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraThreadTable.USERNAME;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;

import reactor.core.publisher.Flux;

public class CassandraThreadDAO {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertOne;
    private final PreparedStatement selectOne;
    private final PreparedStatement deleteOne;

    @Inject
    public CassandraThreadDAO(CqlSession session) {
        executor = new CassandraAsyncExecutor(session);

        insertOne = session.prepare(insertInto(TABLE_NAME)
            .value(USERNAME, bindMarker(USERNAME))
            .value(MIME_MESSAGE_ID, bindMarker(MIME_MESSAGE_ID))
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(THREAD_ID, bindMarker(THREAD_ID))
            .value(BASE_SUBJECT, bindMarker(BASE_SUBJECT))
            .build());

        selectOne = session.prepare(selectFrom(TABLE_NAME)
            .columns(BASE_SUBJECT, THREAD_ID)
            .where(column(USERNAME).isEqualTo(bindMarker(USERNAME)),
                column(MIME_MESSAGE_ID).isEqualTo(bindMarker(MIME_MESSAGE_ID)))
            .build());

        deleteOne = session.prepare(deleteFrom(TABLE_NAME)
            .where(column(USERNAME).isEqualTo(bindMarker(USERNAME)),
                column(MIME_MESSAGE_ID).isEqualTo(bindMarker(MIME_MESSAGE_ID)))
            .build());
    }

    public Flux<Void> insertSome(Username username, Set<Integer> hashMimeMessageIds, MessageId messageId, ThreadId threadId, Optional<Integer> hashBaseSubject) {
        return Flux.fromIterable(hashMimeMessageIds)
            .flatMap(mimeMessageId -> executor.executeVoid(insertOne.bind()
                .set(USERNAME, username.asString(), TypeCodecs.TEXT)
                .set(MIME_MESSAGE_ID, mimeMessageId, TypeCodecs.INT)
                .set(MESSAGE_ID, ((CassandraMessageId) messageId).get(), TypeCodecs.TIMEUUID)
                .set(THREAD_ID, ((CassandraMessageId) threadId.getBaseMessageId()).get(), TypeCodecs.TIMEUUID)
                .set(BASE_SUBJECT, hashBaseSubject.orElse(null), TypeCodecs.INT)), DEFAULT_CONCURRENCY);
    }

    public Flux<Pair<Optional<Integer>, ThreadId>> selectSome(Username username, Set<Integer> hashMimeMessageIds) {
        return Flux.fromIterable(hashMimeMessageIds)
            .flatMap(mimeMessageId -> executor
                .executeSingleRow(selectOne.bind()
                    .set(USERNAME, username.asString(), TypeCodecs.TEXT)
                    .set(MIME_MESSAGE_ID, mimeMessageId, TypeCodecs.INT))
                .map(this::readRow), DEFAULT_CONCURRENCY)
            .distinct();
    }

    public Flux<Void> deleteSome(Username username, Set<Integer> hashMimeMessageIds) {
        return Flux.fromIterable(hashMimeMessageIds)
            .flatMap(mimeMessageId -> executor.executeVoid(deleteOne.bind()
                .set(USERNAME, username.asString(), TypeCodecs.TEXT)
                .set(MIME_MESSAGE_ID, mimeMessageId, TypeCodecs.INT)));
    }

    public Pair<Optional<Integer>, ThreadId> readRow(Row row) {
        return Pair.of(Optional.ofNullable(row.get(BASE_SUBJECT, Integer.class)),
            ThreadId.fromBaseMessageId(CassandraMessageId.Factory.of(row.getUuid(THREAD_ID))));
    }

}
