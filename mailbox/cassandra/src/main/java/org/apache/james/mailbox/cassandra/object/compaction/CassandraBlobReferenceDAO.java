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

package org.apache.james.mailbox.cassandra.object.compaction;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.update;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MESSAGE_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.BODY_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.HEADER_CONTENT;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.mailbox.cassandra.table.CassandraAttachmentV2Table;
import org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.querybuilder.update.UpdateStart;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Owns authoritative message and attachment references. Identifiers remain opaque to the blob layer. */
public class CassandraBlobReferenceDAO {
    enum Type {
        HEADER(CassandraMessageV3Table.TABLE_NAME, MESSAGE_ID, HEADER_CONTENT),
        BODY(CassandraMessageV3Table.TABLE_NAME, MESSAGE_ID, BODY_CONTENT),
        ATTACHMENT(CassandraAttachmentV2Table.TABLE_NAME, CassandraAttachmentV2Table.ID_AS_UUID, CassandraAttachmentV2Table.BLOB_ID);

        private final String table;
        private final CqlIdentifier key;
        private final CqlIdentifier value;

        Type(String table, CqlIdentifier key, CqlIdentifier value) {
            this.table = table;
            this.key = key;
            this.value = value;
        }
    }

    record Reference(BlobId blobId, Type type, UUID referenceId) {
        Reference {
            Objects.requireNonNull(blobId);
            Objects.requireNonNull(type);
            Objects.requireNonNull(referenceId);
        }
    }

    private static final int PAGE_SIZE = 500;
    private static final String REMAINING_TTL = "remaining_ttl";
    private final CassandraAsyncExecutor executor;
    private final BlobId.Factory blobIdFactory;
    private final DriverExecutionProfile batchProfile;
    private final PreparedStatement messages;
    private final PreparedStatement attachments;
    private final PreparedStatement attachmentExpiry;
    private final Map<Type, PreparedStatement> replacements;
    private final int pageSize;

    @Inject
    public CassandraBlobReferenceDAO(CqlSession session, BlobId.Factory blobIdFactory) {
        this(session, blobIdFactory, PAGE_SIZE);
    }

    @VisibleForTesting
    CassandraBlobReferenceDAO(CqlSession session, BlobId.Factory blobIdFactory, int pageSize) {
        this.executor = new CassandraAsyncExecutor(session);
        this.blobIdFactory = blobIdFactory;
        this.batchProfile = JamesExecutionProfiles.getBatchProfile(session);
        this.pageSize = pageSize;
        messages = session.prepare(selectFrom(CassandraMessageV3Table.TABLE_NAME)
            .columns(MESSAGE_ID, HEADER_CONTENT, BODY_CONTENT).build());
        attachments = session.prepare(selectFrom(CassandraAttachmentV2Table.TABLE_NAME)
            .columns(CassandraAttachmentV2Table.ID_AS_UUID, CassandraAttachmentV2Table.BLOB_ID).build());
        attachmentExpiry = session.prepare(selectFrom(CassandraAttachmentV2Table.TABLE_NAME)
            .column(CassandraAttachmentV2Table.BLOB_ID)
            .ttl(CassandraAttachmentV2Table.BLOB_ID).as(REMAINING_TTL)
            .where(column(CassandraAttachmentV2Table.ID_AS_UUID).isEqualTo(bindMarker("reference_id"))).build());
        replacements = new EnumMap<>(Type.class);
        for (Type type : Type.values()) {
            UpdateStart replacement = update(type.table);
            if (type == Type.ATTACHMENT) {
                replacement = replacement.usingTtl(bindMarker(REMAINING_TTL));
            }
            replacements.put(type, session.prepare(replacement
                .setColumn(type.value, bindMarker("new_blob_id"))
                .where(column(type.key).isEqualTo(bindMarker("reference_id")))
                .ifColumn(type.value).isEqualTo(bindMarker("old_blob_id"))
                .build()));
        }
    }

    Flux<Reference> listReferences() {
        return listMessages().concatWith(listAttachments());
    }

    private Flux<Reference> listMessages() {
        return executor.executeRows(messages.bind().setExecutionProfile(batchProfile).setPageSize(pageSize))
            .concatMap(row -> Flux.just(Type.HEADER, Type.BODY)
                .filter(type -> !row.isNull(type.value))
                .map(type -> reference(row, type)));
    }

    private Flux<Reference> listAttachments() {
        return executor.executeRows(attachments.bind().setExecutionProfile(batchProfile).setPageSize(pageSize))
            .filter(row -> !row.isNull(Type.ATTACHMENT.value))
            .map(row -> reference(row, Type.ATTACHMENT));
    }

    private Reference reference(Row row, Type type) {
        return new Reference(blobIdFactory.parse(row.getString(type.value)), type, row.getUuid(type.key));
    }

    Mono<Void> replace(Reference reference, BlobId newId) {
        if (reference.type() == Type.ATTACHMENT) {
            return replaceAttachment(reference, newId);
        }
        // The condition is false for already updated, concurrently changed, or deleted rows.
        // In particular, a delayed update must not resurrect a deleted message or attachment.
        return executor.executeReturnApplied(replacements.get(reference.type()).bind()
                .setUuid("reference_id", reference.referenceId())
                .setString("old_blob_id", reference.blobId().asString())
                .setString("new_blob_id", newId.asString()))
            .then();
    }

    private Mono<Void> replaceAttachment(Reference reference, BlobId newId) {
        return Mono.defer(() -> {
            long started = System.nanoTime();
            return executor.executeSingleRow(attachmentExpiry.bind().setUuid("reference_id", reference.referenceId()))
                .filter(row -> reference.blobId().asString().equals(row.getString(CassandraAttachmentV2Table.BLOB_ID)))
                .flatMap(row -> executor.executeReturnApplied(replacements.get(Type.ATTACHMENT).bind()
                    .setUuid("reference_id", reference.referenceId())
                    .setString("old_blob_id", reference.blobId().asString())
                    .setString("new_blob_id", newId.asString())
                    .setInt(REMAINING_TTL, remainingTtl(row, started))))
                .then();
        });
    }

    private int remainingTtl(Row row, long started) {
        if (row.isNull(REMAINING_TTL)) {
            return 0;
        }
        // Preserve attachment expiry rather than turning the relocated column into permanent metadata.
        long remaining = row.getInt(REMAINING_TTL) - TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started) - 1;
        Preconditions.checkState(remaining > 0, "Attachment reference is expiring; retry relocation after expiry");
        return Math.toIntExact(remaining);
    }
}
