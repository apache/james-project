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

package org.apache.james.jmap.cassandra.projections;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.jmap.cassandra.projections.table.CassandraMessagePreviewTable.MESSAGE_ID;
import static org.apache.james.jmap.cassandra.projections.table.CassandraMessagePreviewTable.PREVIEW;
import static org.apache.james.jmap.cassandra.projections.table.CassandraMessagePreviewTable.TABLE_NAME;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.jmap.api.preview.Preview;
import org.apache.james.jmap.api.projections.MessagePreviewStore;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.reactivestreams.Publisher;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.base.Preconditions;

public class CassandraMessagePreviewStore implements MessagePreviewStore {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;

    private final PreparedStatement storeStatement;
    private final PreparedStatement retrieveStatement;
    private final PreparedStatement deleteStatement;

    @Inject
    CassandraMessagePreviewStore(Session session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);

        this.deleteStatement = session.prepare(QueryBuilder.delete()
            .from(TABLE_NAME)
            .where(eq(MESSAGE_ID, bindMarker(MESSAGE_ID))));

        this.storeStatement = session.prepare(insertInto(TABLE_NAME)
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(PREVIEW, bindMarker(PREVIEW)));

        this.retrieveStatement = session.prepare(select()
            .from(TABLE_NAME)
            .where(eq(MESSAGE_ID, bindMarker(MESSAGE_ID))));
    }

    @Override
    public Publisher<Void> store(MessageId messageId, Preview preview) {
        checkMessage(messageId);

        return cassandraAsyncExecutor.executeVoid(storeStatement.bind()
            .setUUID(MESSAGE_ID, ((CassandraMessageId) messageId).get())
            .setString(PREVIEW, preview.getValue()));
    }

    @Override
    public Publisher<Preview> retrieve(MessageId messageId) {
        checkMessage(messageId);

        return cassandraAsyncExecutor.executeSingleRow(retrieveStatement.bind()
                .setUUID(MESSAGE_ID, ((CassandraMessageId) messageId).get()))
            .map(row -> row.getString(PREVIEW))
            .map(Preview::from);
    }

    @Override
    public Publisher<Void> delete(MessageId messageId) {
        checkMessage(messageId);

        return cassandraAsyncExecutor.executeVoid(deleteStatement.bind()
            .setUUID(MESSAGE_ID, ((CassandraMessageId) messageId).get()));
    }

    private void checkMessage(MessageId messageId) {
        Preconditions.checkNotNull(messageId);
        Preconditions.checkArgument(messageId instanceof CassandraMessageId,
            "MessageId type is required to be CassandraMessageId");
    }
}
