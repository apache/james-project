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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentMessageIdTable.ATTACHMENT_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentMessageIdTable.ATTACHMENT_ID_AS_UUID;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentMessageIdTable.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentMessageIdTable.MESSAGE_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentMessageIdTable.TABLE_NAME;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageId;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraAttachmentMessageIdDAO {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insertStatement;
    private final PreparedStatement selectStatement;
    private final PreparedStatement listStatement;
    private final PreparedStatement deleteStatement;
    private final MessageId.Factory messageIdFactory;

    @Inject
    public CassandraAttachmentMessageIdDAO(CqlSession session, MessageId.Factory messageIdFactory) {
        this.messageIdFactory = messageIdFactory;
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);

        this.selectStatement = prepareSelect(session);
        this.insertStatement = prepareInsert(session);
        this.deleteStatement = prepareDelete(session);
        this.listStatement = prepareList(session);
    }

    private PreparedStatement prepareInsert(CqlSession session) {
        return session.prepare(
            insertInto(TABLE_NAME)
                .value(ATTACHMENT_ID_AS_UUID, bindMarker(ATTACHMENT_ID_AS_UUID))
                .value(ATTACHMENT_ID, bindMarker(ATTACHMENT_ID))
                .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
                .build());
    }

    private PreparedStatement prepareDelete(CqlSession session) {
        return session.prepare(deleteFrom(TABLE_NAME)
            .where(column(ATTACHMENT_ID_AS_UUID).isEqualTo(bindMarker(ATTACHMENT_ID_AS_UUID)),
                column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)))
            .build());
    }

    private PreparedStatement prepareSelect(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .columns(FIELDS)
            .where(column(ATTACHMENT_ID_AS_UUID).isEqualTo(bindMarker(ATTACHMENT_ID_AS_UUID)))
            .build());
    }

    private PreparedStatement prepareList(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .columns(FIELDS)
            .build());
    }

    public Flux<MessageId> getOwnerMessageIds(AttachmentId attachmentId) {
        Preconditions.checkArgument(attachmentId != null);
        return cassandraAsyncExecutor.executeRows(
                selectStatement.bind()
                    .setUuid(ATTACHMENT_ID_AS_UUID, attachmentId.asUUID()))
            .map(this::rowToMessageId);
    }

    private MessageId rowToMessageId(Row row) {
        return messageIdFactory.fromString(row.getString(MESSAGE_ID));
    }

    public Mono<Void> storeAttachmentForMessageId(AttachmentId attachmentId, MessageId ownerMessageId) {
        return cassandraAsyncExecutor.executeVoid(
            insertStatement.bind()
                .setUuid(ATTACHMENT_ID_AS_UUID, attachmentId.asUUID())
                .setString(ATTACHMENT_ID, attachmentId.getId())
                .setString(MESSAGE_ID, ownerMessageId.serialize()));
    }

    public Mono<Void> delete(AttachmentId attachmentId, MessageId ownerMessageId) {
        return cassandraAsyncExecutor.executeVoid(
            deleteStatement.bind()
                .setUuid(ATTACHMENT_ID_AS_UUID, attachmentId.asUUID())
                .setString(MESSAGE_ID, ownerMessageId.serialize()));
    }

    public Flux<Pair<AttachmentId, MessageId>> listAll() {
        return cassandraAsyncExecutor.executeRows(listStatement.bind())
            .map(row -> Pair.of(
                AttachmentId.from(row.getString(ATTACHMENT_ID)),
                messageIdFactory.fromString(row.getString(MESSAGE_ID))));
    }
}
