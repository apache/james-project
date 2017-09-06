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
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.ID;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.PAYLOAD;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.SIZE;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.TYPE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.google.common.base.Preconditions;

public class CassandraAttachmentDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraAttachmentMapper.class);
    private static final boolean NO_LOG_IF_EMPTY = false;

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insertStatement;
    private final PreparedStatement selectStatement;

    @Inject
    public CassandraAttachmentDAO(Session session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);

        this.selectStatement = prepareSelect(session);
        this.insertStatement = prepareInsert(session);
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(
            insertInto(TABLE_NAME)
                .value(ID, bindMarker(ID))
                .value(PAYLOAD, bindMarker(PAYLOAD))
                .value(TYPE, bindMarker(TYPE))
                .value(SIZE, bindMarker(SIZE)));
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select(FIELDS)
            .from(TABLE_NAME)
            .where(eq(ID, bindMarker(ID))));
    }

    public CompletableFuture<Optional<Attachment>> getAttachment(AttachmentId attachmentId) {
        return getAttachment(attachmentId, NO_LOG_IF_EMPTY);
    }

    public CompletableFuture<Optional<Attachment>> getAttachment(AttachmentId attachmentId, boolean logIfEmpty) {
        Preconditions.checkArgument(attachmentId != null);
        return cassandraAsyncExecutor.executeSingleRow(
            selectStatement.bind()
                .setString(ID, attachmentId.getId()))
            .thenApply(optional -> optional.map(this::attachment))
            .thenApply(optional -> logNotFound(attachmentId, logIfEmpty, optional));
    }

    private Optional<Attachment> logNotFound(AttachmentId attachmentId, boolean logIfEmpty, Optional<Attachment> optional) {
        if (!optional.isPresent() && logIfEmpty) {
            LOGGER.warn("Failed retrieving attachment {}", attachmentId);
        }
        return optional;
    }

    public CompletableFuture<Void> storeAttachment(Attachment attachment) throws IOException {
        return cassandraAsyncExecutor.executeVoid(
            insertStatement.bind()
                .setString(ID, attachment.getAttachmentId().getId())
                .setLong(SIZE, attachment.getSize())
                .setString(TYPE, attachment.getType())
                .setBytes(PAYLOAD, ByteBuffer.wrap(attachment.getBytes())));
    }

    private Attachment attachment(Row row) {
        return Attachment.builder()
            .attachmentId(AttachmentId.from(row.getString(ID)))
            .bytes(row.getBytes(PAYLOAD).array())
            .type(row.getString(TYPE))
            .build();
    }
}
