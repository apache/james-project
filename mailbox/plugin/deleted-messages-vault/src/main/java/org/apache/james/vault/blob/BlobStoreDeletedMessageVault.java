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

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.User;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.task.Task;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.metadata.DeletedMessageMetadataVault;
import org.apache.james.vault.metadata.DeletedMessageWithStorageInformation;
import org.apache.james.vault.metadata.StorageInformation;
import org.apache.james.vault.search.Query;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class BlobStoreDeletedMessageVault implements DeletedMessageVault {
    private final DeletedMessageMetadataVault messageMetadataVault;
    private final BlobStore blobStore;
    private final BucketNameGenerator nameGenerator;

    public BlobStoreDeletedMessageVault(DeletedMessageMetadataVault messageMetadataVault, BlobStore blobStore, BucketNameGenerator nameGenerator) {
        this.messageMetadataVault = messageMetadataVault;
        this.blobStore = blobStore;
        this.nameGenerator = nameGenerator;
    }

    @Override
    public Publisher<Void> append(User user, DeletedMessage deletedMessage, InputStream mimeMessage) {
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(deletedMessage);
        Preconditions.checkNotNull(mimeMessage);
        BucketName bucketName = nameGenerator.currentBucket();
        return blobStore.save(bucketName, mimeMessage)
            .map(blobId -> new StorageInformation(bucketName, blobId))
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
    public Publisher<User> usersWithVault() {
        throw new NotImplementedException("Will be implemented later");
    }

    @Override
    public Task deleteExpiredMessagesTask() {
        throw new NotImplementedException("Will be implemented later");
    }
}
