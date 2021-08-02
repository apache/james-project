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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
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
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.model.Subject;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

import reactor.core.publisher.Flux;

public class CassandraThreadDAO {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertOne;
    private final PreparedStatement selectOne;
    private final PreparedStatement deleteOne;

    @Inject
    public CassandraThreadDAO(Session session) {
        executor = new CassandraAsyncExecutor(session);

        insertOne = session.prepare(insertInto(TABLE_NAME)
            .value(USERNAME, bindMarker(USERNAME))
            .value(MIME_MESSAGE_ID, bindMarker(MIME_MESSAGE_ID))
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(THREAD_ID, bindMarker(THREAD_ID))
            .value(BASE_SUBJECT, bindMarker(BASE_SUBJECT)));

        selectOne = session.prepare(select(BASE_SUBJECT, THREAD_ID)
            .from(TABLE_NAME)
            .where(eq(USERNAME, bindMarker(USERNAME)))
            .and(eq(MIME_MESSAGE_ID, bindMarker(MIME_MESSAGE_ID))));

        deleteOne = session.prepare(delete().from(TABLE_NAME)
            .where(eq(USERNAME, bindMarker(USERNAME)))
            .and(eq(MIME_MESSAGE_ID, bindMarker(MIME_MESSAGE_ID))));
    }

    public Flux<Void> insertSome(Username username, Set<MimeMessageId> mimeMessageIds, MessageId messageId, ThreadId threadId, Optional<Subject> baseSubject) {
        return Flux.fromIterable(mimeMessageIds)
            .flatMap(mimeMessageId -> executor.executeVoid(insertOne.bind()
                .setString(USERNAME, username.asString())
                .setString(MIME_MESSAGE_ID, mimeMessageId.getValue())
                .setUUID(MESSAGE_ID, ((CassandraMessageId) messageId).get())
                .setUUID(THREAD_ID, ((CassandraMessageId) threadId.getBaseMessageId()).get())
                .setString(BASE_SUBJECT, baseSubject.map(Subject::getValue).orElse(null))), DEFAULT_CONCURRENCY);
    }

    public Flux<Pair<Optional<Subject>, ThreadId>> selectSome(Username username, Set<MimeMessageId> mimeMessageIds) {
        return Flux.fromIterable(mimeMessageIds)
            .flatMap(mimeMessageId -> executor
                .executeSingleRow(selectOne.bind()
                    .setString(USERNAME, username.asString())
                    .setString(MIME_MESSAGE_ID, mimeMessageId.getValue()))
                .map(this::readRow), DEFAULT_CONCURRENCY)
            .distinct();
    }

    public Flux<Void> deleteSome(Username username, Set<MimeMessageId> mimeMessageIds) {
        return Flux.fromIterable(mimeMessageIds)
            .flatMap(mimeMessageId -> executor.executeVoid(deleteOne.bind()
                .setString(USERNAME, username.asString())
                .setString(MIME_MESSAGE_ID, mimeMessageId.getValue())));
    }

    public Pair<Optional<Subject>, ThreadId> readRow(Row row) {
        return Pair.of(Optional.ofNullable(row.getString(BASE_SUBJECT)).map(Subject::new),
            ThreadId.fromBaseMessageId(CassandraMessageId.Factory.of(row.getUUID(THREAD_ID))));
    }

}
