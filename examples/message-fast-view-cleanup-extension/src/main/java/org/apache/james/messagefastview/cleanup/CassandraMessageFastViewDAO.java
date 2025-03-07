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

package org.apache.james.messagefastview.cleanup;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.jmap.cassandra.projections.table.CassandraMessageFastViewProjectionTable.MESSAGE_ID;
import static org.apache.james.jmap.cassandra.projections.table.CassandraMessageFastViewProjectionTable.TABLE_NAME;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MessageId;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMessageFastViewDAO {
    private final CassandraAsyncExecutor cassandraAsyncExecutor;

    private final PreparedStatement getAllMessageIdsStatement;
    private final PreparedStatement deleteStatement;

    @Inject
    public CassandraMessageFastViewDAO(CqlSession session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);

        SimpleStatement simpleStatement = selectFrom(TABLE_NAME)
            .column(MESSAGE_ID)
            .all()
            .build();
        simpleStatement.setPageSize(1000);
        this.getAllMessageIdsStatement = session.prepare(simpleStatement);

        this.deleteStatement = session.prepare(deleteFrom(TABLE_NAME)
            .whereColumn(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID))
            .build());
    }

    public Flux<MessageId> getAllMessageIds() {
        return cassandraAsyncExecutor.executeRows(getAllMessageIdsStatement.bind())
            .map(row -> row.get(MESSAGE_ID, TypeCodecs.UUID))
            .map(CassandraMessageId.Factory::of);
    }

    public Mono<Void> delete(MessageId messageId) {
        return cassandraAsyncExecutor.executeVoid(deleteStatement.bind()
            .setUuid(MESSAGE_ID, ((CassandraMessageId) messageId).get()));
    }
}
