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

package org.apache.james.mailbox.postgres.mail;

import static org.apache.james.mailbox.postgres.mail.PostgresAttachmentDataDefinition.PostgresAttachmentTable;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageTable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobIdUpdater;
import org.apache.james.util.ReactorUtils;
import org.jooq.impl.DSL;

import com.google.common.collect.Iterables;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresBlobIdUpdater implements BlobIdUpdater {
    public static class Factory implements BlobIdUpdater.Factory {
        private final PostgresExecutor postgresExecutor;
        private final BlobId.Factory blobIdFactory;

        @Inject
        @Singleton
        public Factory(@Named(PostgresExecutor.BY_PASS_RLS_INJECT) PostgresExecutor postgresExecutor,
                       BlobId.Factory blobIdFactory) {
            this.postgresExecutor = postgresExecutor;
            this.blobIdFactory = blobIdFactory;
        }

        @Override
        public Mono<BlobIdUpdater> forPredicate(Predicate<BlobId> generationCondition,
                                                Consumer<BlobId> referencedBlobIdObserver) {
            Map<BlobId, Set<UUID>> messageReferences = new ConcurrentHashMap<>();
            Map<BlobId, Set<String>> attachmentReferences = new ConcurrentHashMap<>();

            Mono<Void> scanMessages = postgresExecutor.executeRowsPaginated((dslContext, lastRecord) -> dslContext
                    .select(MessageTable.MESSAGE_ID, MessageTable.BODY_BLOB_ID)
                    .from(MessageTable.TABLE_NAME)
                    .where(lastRecord.map(record -> MessageTable.MESSAGE_ID.greaterThan(record.get(MessageTable.MESSAGE_ID))).orElseGet(DSL::noCondition))
                    .orderBy(MessageTable.MESSAGE_ID))
                .doOnNext(record -> {
                    String bodyBlobIdStr = record.get(MessageTable.BODY_BLOB_ID);
                    if (bodyBlobIdStr != null) {
                        BlobId bodyBlobId = blobIdFactory.parse(bodyBlobIdStr);
                        referencedBlobIdObserver.accept(bodyBlobId);
                        if (generationCondition.test(bodyBlobId)) {
                            messageReferences.computeIfAbsent(bodyBlobId, k -> ConcurrentHashMap.newKeySet())
                                .add(record.get(MessageTable.MESSAGE_ID));
                        }
                    }
                })
                .then();

            Mono<Void> scanAttachments = postgresExecutor.executeRowsPaginated((dslContext, lastRecord) -> dslContext
                    .select(PostgresAttachmentTable.ID, PostgresAttachmentTable.BLOB_ID)
                    .from(PostgresAttachmentTable.TABLE_NAME)
                    .where(lastRecord.map(record -> PostgresAttachmentTable.ID.greaterThan(record.get(PostgresAttachmentTable.ID))).orElseGet(DSL::noCondition))
                    .orderBy(PostgresAttachmentTable.ID))
                .doOnNext(record -> {
                    String attachmentBlobIdStr = record.get(PostgresAttachmentTable.BLOB_ID);
                    if (attachmentBlobIdStr != null) {
                        BlobId attachmentBlobId = blobIdFactory.parse(attachmentBlobIdStr);
                        referencedBlobIdObserver.accept(attachmentBlobId);
                        if (generationCondition.test(attachmentBlobId)) {
                            attachmentReferences.computeIfAbsent(attachmentBlobId, k -> ConcurrentHashMap.newKeySet())
                                .add(record.get(PostgresAttachmentTable.ID));
                        }
                    }
                })
                .then();

            return Flux.merge(scanMessages, scanAttachments)
                .then(Mono.fromCallable(() -> new PostgresBlobIdUpdater(postgresExecutor, messageReferences, attachmentReferences)));
        }
    }

    private static final int BATCH_SIZE = 500;

    private final PostgresExecutor postgresExecutor;
    private final Map<BlobId, Set<UUID>> messageReferences;
    private final Map<BlobId, Set<String>> attachmentReferences;

    public PostgresBlobIdUpdater(PostgresExecutor postgresExecutor,
                                 Map<BlobId, Set<UUID>> messageReferences,
                                 Map<BlobId, Set<String>> attachmentReferences) {
        this.postgresExecutor = postgresExecutor;
        this.messageReferences = messageReferences;
        this.attachmentReferences = attachmentReferences;
    }

    @Override
    public Mono<Void> replaceReferences(BlobId oldId, BlobId newId) {
        Set<UUID> messageUuids = messageReferences.remove(oldId);
        Set<String> attachmentIds = attachmentReferences.remove(oldId);

        if ((messageUuids == null || messageUuids.isEmpty()) && (attachmentIds == null || attachmentIds.isEmpty())) {
            return Mono.empty();
        }

        String oldIdStr = oldId.asString();
        String newIdStr = newId.asString();

        Mono<Void> updateMessages = (messageUuids == null || messageUuids.isEmpty())
            ? Mono.empty()
            : Flux.fromIterable(Iterables.partition(messageUuids, BATCH_SIZE))
                .flatMap(batch -> postgresExecutor.executeVoid(dslContext -> Mono.from(
                    dslContext.update(MessageTable.TABLE_NAME)
                        .set(MessageTable.BODY_BLOB_ID, newIdStr)
                        .where(MessageTable.MESSAGE_ID.in(batch))
                        .and(MessageTable.BODY_BLOB_ID.eq(oldIdStr)))), ReactorUtils.DEFAULT_CONCURRENCY)
                .then();

        Mono<Void> updateAttachments = (attachmentIds == null || attachmentIds.isEmpty())
            ? Mono.empty()
            : Flux.fromIterable(Iterables.partition(attachmentIds, BATCH_SIZE))
                .flatMap(batch -> postgresExecutor.executeVoid(dslContext -> Mono.from(
                    dslContext.update(PostgresAttachmentTable.TABLE_NAME)
                        .set(PostgresAttachmentTable.BLOB_ID, newIdStr)
                        .where(PostgresAttachmentTable.ID.in(batch))
                        .and(PostgresAttachmentTable.BLOB_ID.eq(oldIdStr)))), ReactorUtils.DEFAULT_CONCURRENCY)
                .then();

        return Flux.merge(updateMessages, updateAttachments).then();
    }
}
