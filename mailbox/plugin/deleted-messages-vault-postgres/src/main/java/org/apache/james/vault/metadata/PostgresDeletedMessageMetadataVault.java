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

package org.apache.james.vault.metadata;

import static org.apache.james.util.ReactorUtils.publishIfPresent;
import static org.apache.james.vault.metadata.PostgresDeletedMessageMetadataModule.DeletedMessageMetadataTable.BLOB_ID;
import static org.apache.james.vault.metadata.PostgresDeletedMessageMetadataModule.DeletedMessageMetadataTable.BUCKET_NAME;
import static org.apache.james.vault.metadata.PostgresDeletedMessageMetadataModule.DeletedMessageMetadataTable.MESSAGE_ID;
import static org.apache.james.vault.metadata.PostgresDeletedMessageMetadataModule.DeletedMessageMetadataTable.METADATA;
import static org.apache.james.vault.metadata.PostgresDeletedMessageMetadataModule.DeletedMessageMetadataTable.OWNER;
import static org.apache.james.vault.metadata.PostgresDeletedMessageMetadataModule.DeletedMessageMetadataTable.TABLE_NAME;
import static org.jooq.JSONB.jsonb;

import java.util.function.Function;

import jakarta.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MessageId;
import org.jooq.Record;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresDeletedMessageMetadataVault implements DeletedMessageMetadataVault {
    private final PostgresExecutor postgresExecutor;
    private final MetadataSerializer metadataSerializer;
    private final BlobId.Factory blobIdFactory;

    @Inject
    public PostgresDeletedMessageMetadataVault(PostgresExecutor postgresExecutor,
                                               MetadataSerializer metadataSerializer,
                                               BlobId.Factory blobIdFactory) {
        this.postgresExecutor = postgresExecutor;
        this.metadataSerializer = metadataSerializer;
        this.blobIdFactory = blobIdFactory;
    }

    @Override
    public Publisher<Void> store(DeletedMessageWithStorageInformation deletedMessage) {
        return postgresExecutor.executeVoid(context -> Mono.from(context.insertInto(TABLE_NAME)
            .set(OWNER, deletedMessage.getDeletedMessage().getOwner().asString())
            .set(MESSAGE_ID, deletedMessage.getDeletedMessage().getMessageId().serialize())
            .set(BUCKET_NAME, deletedMessage.getStorageInformation().getBucketName().asString())
            .set(BLOB_ID, deletedMessage.getStorageInformation().getBlobId().asString())
            .set(METADATA, jsonb(metadataSerializer.serialize(deletedMessage)))));
    }

    @Override
    public Publisher<Void> removeMetadataRelatedToBucket(BucketName bucketName) {
        return postgresExecutor.executeVoid(context -> Mono.from(context.deleteFrom(TABLE_NAME)
            .where(BUCKET_NAME.eq(bucketName.asString()))));
    }

    @Override
    public Publisher<Void> remove(BucketName bucketName, Username username, MessageId messageId) {
        return postgresExecutor.executeVoid(context -> Mono.from(context.deleteFrom(TABLE_NAME)
            .where(BUCKET_NAME.eq(bucketName.asString()),
                OWNER.eq(username.asString()),
                MESSAGE_ID.eq(messageId.serialize()))));
    }

    @Override
    public Publisher<StorageInformation> retrieveStorageInformation(Username username, MessageId messageId) {
        return postgresExecutor.executeRow(context -> Mono.from(context.select(BUCKET_NAME, BLOB_ID)
            .from(TABLE_NAME)
            .where(OWNER.eq(username.asString()),
                MESSAGE_ID.eq(messageId.serialize()))))
            .map(toStorageInformation());
    }

    private Function<Record, StorageInformation> toStorageInformation() {
        return record -> StorageInformation.builder()
            .bucketName(BucketName.of(record.get(BUCKET_NAME)))
            .blobId(blobIdFactory.from(record.get(BLOB_ID)));
    }

    @Override
    public Publisher<DeletedMessageWithStorageInformation> listMessages(BucketName bucketName, Username username) {
        return postgresExecutor.executeRows(context -> Flux.from(context.select(METADATA)
            .from(TABLE_NAME)
            .where(BUCKET_NAME.eq(bucketName.asString()),
                OWNER.eq(username.asString()))))
            .map(record -> metadataSerializer.deserialize(record.get(METADATA).data()))
            .handle(publishIfPresent());
    }

    @Override
    public Publisher<BucketName> listRelatedBuckets() {
        return postgresExecutor.executeRows(context -> Flux.from(context.selectDistinct(BUCKET_NAME)
            .from(TABLE_NAME)))
            .map(record -> BucketName.of(record.get(BUCKET_NAME)));
    }
}
