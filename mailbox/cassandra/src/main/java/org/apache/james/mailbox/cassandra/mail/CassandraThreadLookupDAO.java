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
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MESSAGE_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraThreadLookupTable.MIME_MESSAGE_IDS;
import static org.apache.james.mailbox.cassandra.table.CassandraThreadLookupTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraThreadTable.USERNAME;

import java.util.Set;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class CassandraThreadLookupDAO {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insert;
    private final PreparedStatement select;
    private final PreparedStatement delete;

    @Inject
    public CassandraThreadLookupDAO(Session session) {
        executor = new CassandraAsyncExecutor(session);

        insert = session.prepare(insertInto(TABLE_NAME)
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(USERNAME, bindMarker(USERNAME))
            .value(MIME_MESSAGE_IDS, bindMarker(MIME_MESSAGE_IDS)));

        select = session.prepare(select(USERNAME, MIME_MESSAGE_IDS)
            .from(TABLE_NAME)
            .where(eq(MESSAGE_ID, bindMarker(MESSAGE_ID))));

        delete = session.prepare(delete()
            .from(TABLE_NAME)
            .where(eq(MESSAGE_ID, bindMarker(MESSAGE_ID))));
    }

    public Mono<Void> insert(MessageId messageId, Username username, Set<MimeMessageId> mimeMessageIds) {
        Set<String> mimeMessageIdsString = mimeMessageIds.stream().map(MimeMessageId::getValue).collect(ImmutableSet.toImmutableSet());
        return executor.executeVoid(insert.bind()
            .setUUID(MESSAGE_ID, ((CassandraMessageId) messageId).get())
            .setString(USERNAME, username.asString())
            .setSet(MIME_MESSAGE_IDS, mimeMessageIdsString));
    }

    public Mono<ThreadTablePartitionKey> selectOneRow(MessageId messageId) {
        return executor.executeSingleRow(
            select.bind().setUUID(MESSAGE_ID, ((CassandraMessageId) messageId).get()))
            .map(this::readRow);
    }

    public Mono<Void> deleteOneRow(MessageId messageId) {
        return executor.executeVoid(delete.bind()
            .setUUID(MESSAGE_ID, ((CassandraMessageId) messageId).get()));
    }

    private ThreadTablePartitionKey readRow(Row row) {
        Set<MimeMessageId> mimeMessageIds = row.getSet(MIME_MESSAGE_IDS, String.class)
            .stream()
            .map(MimeMessageId::new)
            .collect(ImmutableSet.toImmutableSet());
        return new ThreadTablePartitionKey(Username.of(row.getString(USERNAME)), mimeMessageIds);
    }
}