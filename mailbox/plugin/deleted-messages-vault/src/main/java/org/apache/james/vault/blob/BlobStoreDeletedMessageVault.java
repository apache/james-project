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

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.InputStream;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.task.Task;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.DeletedMessageContentNotFoundException;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.VaultConfiguration;
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

    private static final String BLOBSTORE_DELETED_MESSAGE_VAULT_METRIC = "deletedMessageVault:blobStore:";
    static final String APPEND_METRIC_NAME = BLOBSTORE_DELETED_MESSAGE_VAULT_METRIC + "append";
    static final String LOAD_MIME_MESSAGE_METRIC_NAME = BLOBSTORE_DELETED_MESSAGE_VAULT_METRIC + "loadMimeMessage";
    static final String SEARCH_METRIC_NAME = BLOBSTORE_DELETED_MESSAGE_VAULT_METRIC + "search";
    static final String DELETE_METRIC_NAME = BLOBSTORE_DELETED_MESSAGE_VAULT_METRIC + "delete";
    static final String DELETE_EXPIRED_MESSAGES_METRIC_NAME = BLOBSTORE_DELETED_MESSAGE_VAULT_METRIC + "deleteExpiredMessages";

    private final MetricFactory metricFactory;
    private final DeletedMessageMetadataVault messageMetadataVault;
    private final BlobStore blobStore;
    private final BlobStoreDAO blobStoreDAO;
    private final BucketNameGenerator nameGenerator;
    private final Clock clock;
    private final VaultConfiguration vaultConfiguration;
    private final BlobStoreVaultGarbageCollectionTask.Factory taskFactory;

    @Inject
    public BlobStoreDeletedMessageVault(MetricFactory metricFactory, DeletedMessageMetadataVault messageMetadataVault,
                                        BlobStore blobStore, BlobStoreDAO blobStoreDAO, BucketNameGenerator nameGenerator,
                                        Clock clock,
                                        VaultConfiguration vaultConfiguration) {
        this.metricFactory = metricFactory;
        this.messageMetadataVault = messageMetadataVault;
        this.blobStore = blobStore;
        this.blobStoreDAO = blobStoreDAO;
        this.nameGenerator = nameGenerator;
        this.clock = clock;
        this.vaultConfiguration = vaultConfiguration;
        this.taskFactory = new BlobStoreVaultGarbageCollectionTask.Factory(this);
    }

    @Override
    public Publisher<Void> append(DeletedMessage deletedMessage, InputStream mimeMessage) {
        Preconditions.checkNotNull(deletedMessage);
        Preconditions.checkNotNull(mimeMessage);
        BucketName bucketName = nameGenerator.currentBucket();

        return metricFactory.decoratePublisherWithTimerMetric(
            APPEND_METRIC_NAME,
            appendMessage(deletedMessage, mimeMessage, bucketName));
    }

    private Mono<Void> appendMessage(DeletedMessage deletedMessage, InputStream mimeMessage, BucketName bucketName) {
        return Mono.from(blobStore.save(bucketName, mimeMessage, LOW_COST))
            .map(blobId -> StorageInformation.builder()
                .bucketName(bucketName)
                .blobId(blobId))
            .map(storageInformation -> new DeletedMessageWithStorageInformation(deletedMessage, storageInformation))
            .flatMap(message -> Mono.from(messageMetadataVault.store(message)))
            .then();
    }

    @Override
    public Publisher<InputStream> loadMimeMessage(Username username, MessageId messageId) {
        Preconditions.checkNotNull(username);
        Preconditions.checkNotNull(messageId);

        return metricFactory.decoratePublisherWithTimerMetric(
            LOAD_MIME_MESSAGE_METRIC_NAME,
            Mono.from(messageMetadataVault.retrieveStorageInformation(username, messageId))
                .flatMap(storageInformation -> loadMimeMessage(storageInformation, username, messageId)));
    }

    private Mono<InputStream> loadMimeMessage(StorageInformation storageInformation, Username username, MessageId messageId) {
        return Mono.from(blobStore.readReactive(storageInformation.getBucketName(), storageInformation.getBlobId(), LOW_COST))
            .onErrorResume(
                ObjectNotFoundException.class,
                ex -> Mono.error(new DeletedMessageContentNotFoundException(username, messageId)));
    }

    @Override
    public Publisher<DeletedMessage> search(Username username, Query query) {
        Preconditions.checkNotNull(username);
        Preconditions.checkNotNull(query);

        return metricFactory.decoratePublisherWithTimerMetric(
            SEARCH_METRIC_NAME,
            searchOn(username, query));
    }

    private Flux<DeletedMessage> searchOn(Username username, Query query) {
        Flux<DeletedMessage> filterPublisher = Flux.from(messageMetadataVault.listRelatedBuckets())
            .concatMap(bucketName -> messageMetadataVault.listMessages(bucketName, username))
            .map(DeletedMessageWithStorageInformation::getDeletedMessage)
            .filter(query.toPredicate());
        return query.getLimit()
            .map(filterPublisher::take)
            .orElse(filterPublisher);
    }

    @Override
    public Publisher<Void> delete(Username username, MessageId messageId) {
        Preconditions.checkNotNull(username);
        Preconditions.checkNotNull(messageId);

        return metricFactory.decoratePublisherWithTimerMetric(
            DELETE_METRIC_NAME,
            deleteMessage(username, messageId));
    }

    private Mono<Void> deleteMessage(Username username, MessageId messageId) {
        return Mono.from(messageMetadataVault.retrieveStorageInformation(username, messageId))
            .flatMap(storageInformation -> Mono.from(messageMetadataVault.remove(storageInformation.getBucketName(), username, messageId))
                .thenReturn(storageInformation))
            .flatMap(storageInformation -> Mono.from(blobStoreDAO.delete(storageInformation.getBucketName(), storageInformation.getBlobId())));
    }

    @Override
    public Task deleteExpiredMessagesTask() {
        return taskFactory.create();
    }


    Flux<BucketName> deleteExpiredMessages(ZonedDateTime beginningOfRetentionPeriod) {
        return Flux.from(
            metricFactory.decoratePublisherWithTimerMetric(
                DELETE_EXPIRED_MESSAGES_METRIC_NAME,
                retentionQualifiedBuckets(beginningOfRetentionPeriod)
                    .flatMap(bucketName -> deleteBucketData(bucketName).then(Mono.just(bucketName)), DEFAULT_CONCURRENCY)));
    }

    ZonedDateTime getBeginningOfRetentionPeriod() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        return now.minus(vaultConfiguration.getRetentionPeriod());
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
        return Mono.from(blobStore.deleteBucket(bucketName))
            .then(Mono.from(messageMetadataVault.removeMetadataRelatedToBucket(bucketName)));
    }
}
