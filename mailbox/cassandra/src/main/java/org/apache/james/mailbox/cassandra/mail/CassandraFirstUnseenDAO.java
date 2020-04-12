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

package org.apache.james.mailbox.cassandra.mail;

import static com.datastax.driver.core.querybuilder.QueryBuilder.asc;
import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.mailbox.cassandra.table.CassandraFirstUnseenTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraFirstUnseenTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraFirstUnseenTable.UID;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

import reactor.core.publisher.Mono;

public class CassandraFirstUnseenDAO {
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement addStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement deleteAllStatement;
    private final PreparedStatement readStatement;

    @Inject
    public CassandraFirstUnseenDAO(Session session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.addStatement = prepareAddStatement(session);
        this.deleteStatement = prepareDeleteStatement(session);
        this.deleteAllStatement = prepareDeleteAllStatement(session);
        this.readStatement = prepareReadStatement(session);
    }

    private PreparedStatement prepareReadStatement(Session session) {
        return session.prepare(select(UID)
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .orderBy(asc(UID))
            .limit(1));
    }

    private PreparedStatement prepareDeleteStatement(Session session) {
        return session.prepare(delete()
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(eq(UID, bindMarker(UID))));
    }

    private PreparedStatement prepareDeleteAllStatement(Session session) {
        return session.prepare(delete()
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    private PreparedStatement prepareAddStatement(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(UID, bindMarker(UID)));
    }

    public Mono<Void> addUnread(CassandraId cassandraId, MessageUid uid) {
        return cassandraAsyncExecutor.executeVoid(
            addStatement.bind()
                .setUUID(MAILBOX_ID, cassandraId.asUuid())
                .setLong(UID, uid.asLong()));
    }

    public Mono<Void> removeUnread(CassandraId cassandraId, MessageUid uid) {
        return cassandraAsyncExecutor.executeVoid(deleteStatement.bind()
            .setUUID(MAILBOX_ID, cassandraId.asUuid())
            .setLong(UID, uid.asLong()));
    }

    public Mono<Void> removeAll(CassandraId cassandraId) {
        return cassandraAsyncExecutor.executeVoid(deleteAllStatement.bind()
            .setUUID(MAILBOX_ID, cassandraId.asUuid()));
    }

    public Mono<MessageUid> retrieveFirstUnread(CassandraId cassandraId) {
        return cassandraAsyncExecutor.executeSingleRow(
            readStatement.bind()
                .setUUID(MAILBOX_ID, cassandraId.asUuid()))
            .map(row -> MessageUid.of(row.getLong(UID)));
    }

}
