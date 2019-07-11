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

package org.apache.james.vault.blob;

import java.io.InputStream;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.User;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.task.Task;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.RetentionConfiguration;
import org.apache.james.vault.metadata.DeletedMessageMetadataVault;
import org.apache.james.vault.metadata.DeletedMessageWithStorageInformation;
import org.apache.james.vault.metadata.StorageInformation;
import org.apache.james.vault.search.Query;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class BlobStoreDeletedMessageVault implements DeletedMessageVault {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlobStoreDeletedMessageVault.class);

    private final DeletedMessageMetadataVault messageMetadataVault;
    private final BlobStore blobStore;
    private final BucketNameGenerator nameGenerator;
    private final Clock clock;
    private final RetentionConfiguration retentionConfiguration;

    BlobStoreDeletedMessageVault(DeletedMessageMetadataVault messageMetadataVault, BlobStore blobStore, BucketNameGenerator nameGenerator, Clock clock, RetentionConfiguration retentionConfiguration) {
        this.messageMetadataVault = messageMetadataVault;
        this.blobStore = blobStore;
        this.nameGenerator = nameGenerator;
        this.clock = clock;
        this.retentionConfiguration = retentionConfiguration;
    }

    @Override
    public Publisher<Void> append(DeletedMessage deletedMessage, InputStream mimeMessage) {
        Preconditions.checkNotNull(deletedMessage);
        Preconditions.checkNotNull(mimeMessage);
        BucketName bucketName = nameGenerator.currentBucket();
        return blobStore.save(bucketName, mimeMessage)
            .map(blobId -> StorageInformation.builder()
                .bucketName(bucketName)
                .blobId(blobId))
            .map(storageInformation -> new DeletedMessageWithStorageInformation(deletedMessage, storageInformation))
            .flatMap(message -> Mono.from(messageMetadataVault.store(message)))
            .then();
    }

    @Override
    public Publisher<InputStream> loadMimeMessage(User user, MessageId messageId) {
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(messageId);
        return Mono.from(messageMetadataVault.retrieveStorageInformation(user, messageId))
            .map(storageInformation -> blobStore.read(storageInformation.getBucketName(), storageInformation.getBlobId()));
    }

    @Override
    public Publisher<DeletedMessage> search(User user, Query query) {
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(query);
        return Flux.from(messageMetadataVault.listRelatedBuckets())
            .concatMap(bucketName -> Flux.from(messageMetadataVault.listMessages(bucketName, user)))
            .map(DeletedMessageWithStorageInformation::getDeletedMessage)
            .filter(query.toPredicate());
    }

    @Override
    public Publisher<Void> delete(User user, MessageId messageId) {
        throw new NotImplementedException("Will be implemented later");
    }

    @Override
    public Task deleteExpiredMessagesTask() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime beginningOfRetentionPeriod = now.minus(retentionConfiguration.getRetentionPeriod());

        Flux<BucketName> deleteOperation = retentionQualifiedBuckets(beginningOfRetentionPeriod)
            .flatMap(bucketName -> deleteBucketData(bucketName).then(Mono.just(bucketName)));

        return new BlobStoreVaultGarbageCollectionTask(beginningOfRetentionPeriod, deleteOperation);
    }

    @VisibleForTesting
    Flux<BucketName> retentionQualifiedBuckets(ZonedDateTime beginningOfRetentionPeriod) {
        return Flux.from(messageMetadataVault.listRelatedBuckets())
            .filter(bucketName -> isFullyExpired(beginningOfRetentionPeriod, bucketName));
    }

    private boolean isFullyExpired(ZonedDateTime beginningOfRetentionPeriod, BucketName bucketName) {
        Optional<ZonedDateTime> maybeEndDate = nameGenerator.bucketEndTime(bucketName);

        if (!maybeEndDate.isPresent()) {
            LOGGER.error("Pattern used for bucketName used in deletedMessageVault is invalid and end date cannot be parsed {}", bucketName);
        }
        return maybeEndDate.map(endDate -> endDate.isBefore(beginningOfRetentionPeriod))
            .orElse(false);
    }

    private Mono<Void> deleteBucketData(BucketName bucketName) {
        return blobStore.deleteBucket(bucketName)
            .then(Mono.from(messageMetadataVault.removeMetadataRelatedToBucket(bucketName)));
    }
}
