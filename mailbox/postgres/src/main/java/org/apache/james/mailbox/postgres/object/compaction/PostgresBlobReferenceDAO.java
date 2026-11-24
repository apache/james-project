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

package org.apache.james.mailbox.postgres.object.compaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.mailbox.postgres.mail.PostgresAttachmentDataDefinition.PostgresAttachmentTable;
import org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;

/** Pages through the PostgreSQL mailbox tables owning blob references, using keyset pagination. */
public class PostgresBlobReferenceDAO {
    /** Exactly one of {@code messageId} and {@code attachmentId} is present. */
    record Reference(BlobId blobId, Optional<UUID> messageId, Optional<String> attachmentId) {
        static Reference message(BlobId blobId, UUID messageId) {
            return new Reference(blobId, Optional.of(messageId), Optional.empty());
        }

        static Reference attachment(BlobId blobId, String attachmentId) {
            return new Reference(blobId, Optional.empty(), Optional.of(attachmentId));
        }
    }

    private static final int PAGE_SIZE = 500;

    private final PostgresExecutor executor;
    private final BlobId.Factory blobIdFactory;
    private final int pageSize;

    @Inject
    public PostgresBlobReferenceDAO(@Named(PostgresExecutor.BY_PASS_RLS_INJECT) PostgresExecutor executor, BlobId.Factory blobIdFactory) {
        this(executor, blobIdFactory, PAGE_SIZE);
    }

    @VisibleForTesting
    PostgresBlobReferenceDAO(PostgresExecutor executor, BlobId.Factory blobIdFactory, int pageSize) {
        this.executor = executor;
        this.blobIdFactory = blobIdFactory;
        this.pageSize = pageSize;
    }

    /**
     * Pages are re-batched for index writes. Paging by primary key keeps each query bounded and releases its connection.
     */
    Flux<List<Reference>> listReferencePages() {
        return Flux.concat(
                references(MessageTable.TABLE_NAME, MessageTable.MESSAGE_ID, MessageTable.BODY_BLOB_ID, Reference::message),
                references(PostgresAttachmentTable.TABLE_NAME, PostgresAttachmentTable.ID, PostgresAttachmentTable.BLOB_ID, Reference::attachment))
            .buffer(pageSize);
    }

    private <K> Flux<Reference> references(Table<Record> table, Field<K> key, Field<String> blobId, BiFunction<BlobId, K, Reference> toReference) {
        return executor.executeRowsPaginated((dsl, lastRecord) -> dsl.select(key, blobId)
                    .from(table)
                    .where(lastRecord.map(record -> key.gt(record.get(key))).orElse(DSL.noCondition()))
                    .orderBy(key),
                pageSize)
            .filter(record -> record.get(blobId) != null)
            .map(record -> toReference.apply(blobIdFactory.parse(record.get(blobId)), record.get(key)));
    }
}
