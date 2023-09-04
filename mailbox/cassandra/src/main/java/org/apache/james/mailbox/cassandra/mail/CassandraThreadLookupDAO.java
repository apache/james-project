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

import static com.datastax.oss.driver.api.core.type.DataTypes.INT;
import static com.datastax.oss.driver.api.core.type.DataTypes.frozenSetOf;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
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

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class CassandraThreadLookupDAO {
    private static final TypeCodec<Set<Integer>> SET_OF_INTS_CODEC = CodecRegistry.DEFAULT.codecFor(frozenSetOf(INT));

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insert;
    private final PreparedStatement select;
    private final PreparedStatement delete;

    @Inject
    public CassandraThreadLookupDAO(CqlSession session) {
        executor = new CassandraAsyncExecutor(session);

        insert = session.prepare(insertInto(TABLE_NAME)
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(USERNAME, bindMarker(USERNAME))
            .value(MIME_MESSAGE_IDS, bindMarker(MIME_MESSAGE_IDS))
            .build());

        select = session.prepare(selectFrom(TABLE_NAME)
            .columns(USERNAME, MIME_MESSAGE_IDS)
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)))
            .build());

        delete = session.prepare(deleteFrom(TABLE_NAME)
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)))
            .build());
    }

    public Mono<Void> insert(MessageId messageId, Username username, Set<Integer> hashMimeMessageIds) {
        return executor.executeVoid(insert.bind()
            .set(MESSAGE_ID, ((CassandraMessageId) messageId).get(), TypeCodecs.TIMEUUID)
            .set(USERNAME, username.asString(), TypeCodecs.TEXT)
            .set(MIME_MESSAGE_IDS, hashMimeMessageIds, SET_OF_INTS_CODEC));
    }

    public Mono<ThreadTablePartitionKey> selectOneRow(MessageId messageId) {
        return executor.executeSingleRow(
                select.bind().set(MESSAGE_ID, ((CassandraMessageId) messageId).get(), TypeCodecs.TIMEUUID))
            .map(this::readRow);
    }

    public Mono<Void> deleteOneRow(MessageId messageId) {
        return executor.executeVoid(delete.bind()
            .set(MESSAGE_ID, ((CassandraMessageId) messageId).get(), TypeCodecs.TIMEUUID));
    }

    private ThreadTablePartitionKey readRow(Row row) {
        Set<Integer> mimeMessageIds = row.get(MIME_MESSAGE_IDS, SET_OF_INTS_CODEC)
            .stream()
            .collect(ImmutableSet.toImmutableSet());
        return new ThreadTablePartitionKey(Username.of(row.getString(USERNAME)), mimeMessageIds);
    }
}