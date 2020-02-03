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
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentV2Table.BLOB_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentV2Table.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentV2Table.ID;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentV2Table.ID_AS_UUID;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentV2Table.SIZE;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentV2Table.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentV2Table.TYPE;

import java.util.Objects;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class CassandraAttachmentDAOV2 {
    public static class DAOAttachment {
        private final AttachmentId attachmentId;
        private final BlobId blobId;
        private final String type;
        private final long size;

        private DAOAttachment(AttachmentId attachmentId, BlobId blobId, String type, long size) {
            this.attachmentId = attachmentId;
            this.blobId = blobId;
            this.type = type;
            this.size = size;
        }

        public AttachmentId getAttachmentId() {
            return attachmentId;
        }

        public BlobId getBlobId() {
            return blobId;
        }

        public String getType() {
            return type;
        }

        public long getSize() {
            return size;
        }

        public Attachment toAttachment(byte[] data) {
            return Attachment.builder()
                .attachmentId(attachmentId)
                .type(type)
                .bytes(data)
                .build();
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof DAOAttachment) {
                DAOAttachment that = (DAOAttachment) o;

                return Objects.equals(this.size, that.size)
                    && Objects.equals(this.attachmentId, that.attachmentId)
                    && Objects.equals(this.blobId, that.blobId)
                    && Objects.equals(this.type, that.type);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(attachmentId, blobId, type, size);
        }
    }

    public static DAOAttachment from(Attachment attachment, BlobId blobId) {
        return new DAOAttachment(
            attachment.getAttachmentId(),
            blobId,
            attachment.getType(),
            attachment.getSize());
    }

    private static DAOAttachment fromRow(Row row, BlobId.Factory blobIfFactory) {
        return new DAOAttachment(
            AttachmentId.from(row.getString(ID)),
            blobIfFactory.from(row.getString(BLOB_ID)),
            row.getString(TYPE),
            row.getLong(SIZE));
    }

    private final BlobId.Factory blobIdFactory;
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insertStatement;
    private final PreparedStatement selectStatement;

    @Inject
    public CassandraAttachmentDAOV2(BlobId.Factory blobIdFactory, Session session) {
        this.blobIdFactory = blobIdFactory;
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);

        this.selectStatement = prepareSelect(session);
        this.insertStatement = prepareInsert(session);
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(
            insertInto(TABLE_NAME)
                .value(ID_AS_UUID, bindMarker(ID_AS_UUID))
                .value(ID, bindMarker(ID))
                .value(BLOB_ID, bindMarker(BLOB_ID))
                .value(TYPE, bindMarker(TYPE))
                .value(SIZE, bindMarker(SIZE)));
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select(FIELDS)
            .from(TABLE_NAME)
            .where(eq(ID_AS_UUID, bindMarker(ID_AS_UUID))));
    }

    public Mono<DAOAttachment> getAttachment(AttachmentId attachmentId) {
        Preconditions.checkArgument(attachmentId != null);
        return cassandraAsyncExecutor.executeSingleRow(
            selectStatement.bind()
                .setUUID(ID_AS_UUID, attachmentId.asUUID()))
            .map(row -> CassandraAttachmentDAOV2.fromRow(row, blobIdFactory));
    }

    public Mono<Void> storeAttachment(DAOAttachment attachment) {
        return cassandraAsyncExecutor.executeVoid(
            insertStatement.bind()
                .setUUID(ID_AS_UUID, attachment.getAttachmentId().asUUID())
                .setString(ID, attachment.getAttachmentId().getId())
                .setLong(SIZE, attachment.getSize())
                .setString(TYPE, attachment.getType())
                .setString(BLOB_ID, attachment.getBlobId().asString()));
    }

}
