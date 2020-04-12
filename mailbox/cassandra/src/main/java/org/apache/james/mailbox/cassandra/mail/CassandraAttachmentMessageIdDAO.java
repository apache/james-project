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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentMessageIdTable.ATTACHMENT_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentMessageIdTable.ATTACHMENT_ID_AS_UUID;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentMessageIdTable.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentMessageIdTable.MESSAGE_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentMessageIdTable.TABLE_NAME;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageId;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraAttachmentMessageIdDAO {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insertStatement;
    private final PreparedStatement selectStatement;
    private final PreparedStatement deleteStatement;
    private final MessageId.Factory messageIdFactory;

    @Inject
    public CassandraAttachmentMessageIdDAO(Session session, MessageId.Factory messageIdFactory) {
        this.messageIdFactory = messageIdFactory;
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);

        this.selectStatement = prepareSelect(session);
        this.insertStatement = prepareInsert(session);
        this.deleteStatement = prepareDelete(session);
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(
            insertInto(TABLE_NAME)
                .value(ATTACHMENT_ID_AS_UUID, bindMarker(ATTACHMENT_ID_AS_UUID))
                .value(ATTACHMENT_ID, bindMarker(ATTACHMENT_ID))
                .value(MESSAGE_ID, bindMarker(MESSAGE_ID)));
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(
            QueryBuilder.delete()
                .from(TABLE_NAME)
                .where(eq(ATTACHMENT_ID_AS_UUID, bindMarker(ATTACHMENT_ID_AS_UUID)))
                .and(eq(MESSAGE_ID, bindMarker(MESSAGE_ID))));
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select(FIELDS)
            .from(TABLE_NAME)
            .where(eq(ATTACHMENT_ID_AS_UUID, bindMarker(ATTACHMENT_ID_AS_UUID))));
    }

    public Flux<MessageId> getOwnerMessageIds(AttachmentId attachmentId) {
        Preconditions.checkArgument(attachmentId != null);
        return cassandraAsyncExecutor.executeRows(
                selectStatement.bind()
                    .setUUID(ATTACHMENT_ID_AS_UUID, attachmentId.asUUID()))
            .map(this::rowToMessageId);
    }

    private MessageId rowToMessageId(Row row) {
        return messageIdFactory.fromString(row.getString(MESSAGE_ID));
    }

    public Mono<Void> storeAttachmentForMessageId(AttachmentId attachmentId, MessageId ownerMessageId) {
        return cassandraAsyncExecutor.executeVoid(
            insertStatement.bind()
                .setUUID(ATTACHMENT_ID_AS_UUID, attachmentId.asUUID())
                .setString(ATTACHMENT_ID, attachmentId.getId())
                .setString(MESSAGE_ID, ownerMessageId.serialize()));
    }

    public Mono<Void> delete(AttachmentId attachmentId, MessageId ownerMessageId) {
        return cassandraAsyncExecutor.executeVoid(
            deleteStatement.bind()
                .setUUID(ATTACHMENT_ID_AS_UUID, attachmentId.asUUID())
                .setString(MESSAGE_ID, ownerMessageId.serialize()));
    }
}
