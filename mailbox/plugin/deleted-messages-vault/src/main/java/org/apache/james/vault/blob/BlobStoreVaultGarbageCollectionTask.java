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

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BucketName;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class BlobStoreVaultGarbageCollectionTask implements Task {

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final ZonedDateTime beginningOfRetentionPeriod;
        private final ImmutableSet<BucketName> deletedBuckets;
        private final Instant timestamp;

        AdditionalInformation(ZonedDateTime beginningOfRetentionPeriod, ImmutableSet<BucketName> deletedBuckets, Instant timestamp) {
            this.beginningOfRetentionPeriod = beginningOfRetentionPeriod;
            this.deletedBuckets = deletedBuckets;
            this.timestamp = timestamp;
        }

        public ZonedDateTime getBeginningOfRetentionPeriod() {
            return beginningOfRetentionPeriod;
        }

        public List<String> getDeletedBuckets() {
            return deletedBuckets.stream()
                .map(BucketName::asString)
                .collect(ImmutableList.toImmutableList());
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    static final TaskType TYPE = TaskType.of("deleted-messages-blob-store-based-garbage-collection");
    private final Collection<BucketName> deletedBuckets;
    private final BlobStoreDeletedMessageVault deletedMessageVault;
    private final ZonedDateTime beginningOfRetentionPeriod;

    public static class Factory {
        private final BlobStoreDeletedMessageVault deletedMessageVault;

        @Inject
        public Factory(BlobStoreDeletedMessageVault deletedMessageVault) {
            this.deletedMessageVault = deletedMessageVault;
        }

        public BlobStoreVaultGarbageCollectionTask create() {
            return new BlobStoreVaultGarbageCollectionTask(deletedMessageVault);
        }
    }

    private BlobStoreVaultGarbageCollectionTask(BlobStoreDeletedMessageVault deletedMessageVault) {
        this.beginningOfRetentionPeriod = deletedMessageVault.getBeginningOfRetentionPeriod();
        this.deletedMessageVault = deletedMessageVault;
        this.deletedBuckets = new ConcurrentLinkedQueue<>();
    }

    @Override
    public Result run() {
        deletedMessageVault.deleteExpiredMessages(beginningOfRetentionPeriod)
            .doOnNext(deletedBuckets::add)
            .then()
            .block();

        return Result.COMPLETED;
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(beginningOfRetentionPeriod, ImmutableSet.copyOf(deletedBuckets), Clock.systemUTC().instant()));
    }
}
