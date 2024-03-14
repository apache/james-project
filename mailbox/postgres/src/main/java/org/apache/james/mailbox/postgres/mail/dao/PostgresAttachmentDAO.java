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

package org.apache.james.mailbox.postgres.mail.dao;

import java.util.Collection;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.core.Domain;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.PostgresAttachmentModule.PostgresAttachmentTable;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresAttachmentDAO {

    public static class Factory {
        private final PostgresExecutor.Factory executorFactory;
        private final BlobId.Factory blobIdFactory;

        @Inject
        @Singleton
        public Factory(PostgresExecutor.Factory executorFactory, BlobId.Factory blobIdFactory) {
            this.executorFactory = executorFactory;
            this.blobIdFactory = blobIdFactory;
        }

        public PostgresAttachmentDAO create(Optional<Domain> domain) {
            return new PostgresAttachmentDAO(executorFactory.create(domain), blobIdFactory);
        }
    }

    private final PostgresExecutor postgresExecutor;
    private final BlobId.Factory blobIdFactory;

    public PostgresAttachmentDAO(PostgresExecutor postgresExecutor, BlobId.Factory blobIdFactory) {
        this.postgresExecutor = postgresExecutor;
        this.blobIdFactory = blobIdFactory;
    }

    public Mono<Pair<AttachmentMetadata, BlobId>> getAttachment(AttachmentId attachmentId) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(
                    PostgresAttachmentTable.TYPE,
                    PostgresAttachmentTable.BLOB_ID,
                    PostgresAttachmentTable.MESSAGE_ID,
                    PostgresAttachmentTable.SIZE)
                .from(PostgresAttachmentTable.TABLE_NAME)
                .where(PostgresAttachmentTable.ID.eq(attachmentId.getId()))))
            .map(row -> Pair.of(
                AttachmentMetadata.builder()
                    .attachmentId(attachmentId)
                    .type(row.get(PostgresAttachmentTable.TYPE))
                    .messageId(PostgresMessageId.Factory.of(row.get(PostgresAttachmentTable.MESSAGE_ID)))
                    .size(row.get(PostgresAttachmentTable.SIZE))
                    .build(),
                blobIdFactory.from(row.get(PostgresAttachmentTable.BLOB_ID))));
    }

    public Flux<AttachmentMetadata> getAttachments(Collection<AttachmentId> attachmentIds) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectFrom(PostgresAttachmentTable.TABLE_NAME)
                .where(PostgresAttachmentTable.ID.in(attachmentIds.stream().map(AttachmentId::getId).collect(ImmutableList.toImmutableList())))))
            .map(row -> AttachmentMetadata.builder()
                .attachmentId(AttachmentId.from(row.get(PostgresAttachmentTable.ID)))
                .type(row.get(PostgresAttachmentTable.TYPE))
                .messageId(PostgresMessageId.Factory.of(row.get(PostgresAttachmentTable.MESSAGE_ID)))
                .size(row.get(PostgresAttachmentTable.SIZE))
                .build());
    }

    public Mono<Void> storeAttachment(AttachmentMetadata attachment, BlobId blobId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(PostgresAttachmentTable.TABLE_NAME)
            .set(PostgresAttachmentTable.ID, attachment.getAttachmentId().getId())
            .set(PostgresAttachmentTable.BLOB_ID, blobId.asString())
            .set(PostgresAttachmentTable.TYPE, attachment.getType().asString())
            .set(PostgresAttachmentTable.MESSAGE_ID, ((PostgresMessageId) attachment.getMessageId()).asUuid())
            .set(PostgresAttachmentTable.SIZE, attachment.getSize())));
    }

    public Mono<Void> deleteByMessageId(PostgresMessageId messageId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(PostgresAttachmentTable.TABLE_NAME)
            .where(PostgresAttachmentTable.MESSAGE_ID.eq(messageId.asUuid()))));
    }

    public Flux<BlobId> listBlobsByMessageId(PostgresMessageId messageId) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(PostgresAttachmentTable.BLOB_ID)
                .from(PostgresAttachmentTable.TABLE_NAME)
                .where(PostgresAttachmentTable.MESSAGE_ID.eq(messageId.asUuid()))))
            .map(row -> blobIdFactory.from(row.get(PostgresAttachmentTable.BLOB_ID)));
    }

    public Flux<BlobId> listBlobs() {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(PostgresAttachmentTable.BLOB_ID)
                .from(PostgresAttachmentTable.TABLE_NAME)))
            .map(row -> blobIdFactory.from(row.get(PostgresAttachmentTable.BLOB_ID)));
    }
}