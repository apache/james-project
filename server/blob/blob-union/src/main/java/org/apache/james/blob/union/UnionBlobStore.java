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

package org.apache.james.blob.union;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Optional;
import java.util.function.Function;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class UnionBlobStore implements BlobStore {

    @FunctionalInterface
    public interface StorageOperation<T> {
        Mono<BlobId> save(BucketName bucketName, T data, StoragePolicy storagePolicy);
    }

    @FunctionalInterface
    public interface RequireCurrent {
        RequireLegacy current(BlobStore blobStore);
    }

    @FunctionalInterface
    public interface RequireLegacy {
        Builder legacy(BlobStore blobStore);
    }

    public static class Builder {
        private final BlobStore currentBlobStore;
        private final BlobStore legacyBlobStore;

        Builder(BlobStore currentBlobStore, BlobStore legacyBlobStore) {
            this.currentBlobStore = currentBlobStore;
            this.legacyBlobStore = legacyBlobStore;
        }

        public UnionBlobStore build() {
            return new UnionBlobStore(
                currentBlobStore,
                legacyBlobStore);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(UnionBlobStore.class);
    private static final int UNAVAILABLE = -1;

    public static RequireCurrent builder() {
        return current -> legacy -> new Builder(current, legacy);
    }

    private final BlobStore currentBlobStore;
    private final BlobStore legacyBlobStore;

    private UnionBlobStore(BlobStore currentBlobStore, BlobStore legacyBlobStore) {
        this.currentBlobStore = currentBlobStore;
        this.legacyBlobStore = legacyBlobStore;
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, byte[] data, StoragePolicy storagePolicy) {
        try {
            return saveToCurrentFallbackIfFails(bucketName, data, storagePolicy,
                currentBlobStore::save,
                legacyBlobStore::save);
        } catch (Exception e) {
            LOGGER.error("exception directly happens while saving bytes data, fall back to legacy blob store", e);
            return legacyBlobStore.save(bucketName, data, storagePolicy);
        }
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, String data, StoragePolicy storagePolicy) {
        try {
            return saveToCurrentFallbackIfFails(bucketName, data, storagePolicy,
                currentBlobStore::save,
                legacyBlobStore::save);
        } catch (Exception e) {
            LOGGER.error("exception directly happens while saving String data, fall back to legacy blob store", e);
            return legacyBlobStore.save(bucketName, data, storagePolicy);
        }
    }

    @Override
    public BucketName getDefaultBucketName() {
        Preconditions.checkState(
            currentBlobStore.getDefaultBucketName()
                .equals(legacyBlobStore.getDefaultBucketName()),
            "currentBlobStore and legacyBlobStore doen't have same defaultBucketName which could lead to " +
                "unexpected result when interact with other APIs");

        return currentBlobStore.getDefaultBucketName();
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, InputStream data, StoragePolicy storagePolicy) {
        try {
            return saveToCurrentFallbackIfFails(bucketName, data, storagePolicy,
                currentBlobStore::save,
                legacyBlobStore::save);
        } catch (Exception e) {
            LOGGER.error("exception directly happens while saving InputStream data, fall back to legacy blob store", e);
            return legacyBlobStore.save(bucketName, data, storagePolicy);
        }
    }

    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        try {
            return readBytesFallBackIfFailsOrEmptyResult(bucketName, blobId);
        } catch (Exception e) {
            LOGGER.error("exception directly happens while readBytes, fall back to legacy blob store", e);
            return Mono.defer(() -> legacyBlobStore.readBytes(bucketName, blobId));
        }
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) {
        try {
            return readFallBackIfEmptyResult(bucketName, blobId);
        } catch (Exception e) {
            LOGGER.error("exception directly happens while read, fall back to legacy blob store", e);
            return legacyBlobStore.read(bucketName, blobId);
        }
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        return Mono.defer(() -> currentBlobStore.deleteBucket(bucketName))
            .and(legacyBlobStore.deleteBucket(bucketName))
            .onErrorResume(this::logDeleteFailureAndReturnEmpty);
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        return Mono.defer(() -> currentBlobStore.delete(bucketName, blobId))
            .and(legacyBlobStore.delete(bucketName, blobId))
            .onErrorResume(this::logDeleteFailureAndReturnEmpty);
    }

    private InputStream readFallBackIfEmptyResult(BucketName bucketName, BlobId blobId) {
        return Optional.ofNullable(currentBlobStore.read(bucketName, blobId))
            .map(PushbackInputStream::new)
            .filter(Throwing.predicate(this::streamHasContent).sneakyThrow())
            .<InputStream>map(Function.identity())
            .orElseGet(() -> legacyBlobStore.read(bucketName, blobId));
    }

    @VisibleForTesting
    boolean streamHasContent(PushbackInputStream pushBackIS) throws IOException {
        int byteRead = pushBackIS.read();
        if (byteRead != UNAVAILABLE) {
            pushBackIS.unread(byteRead);
            return true;
        }
        return false;
    }

    private Mono<byte[]> readBytesFallBackIfFailsOrEmptyResult(BucketName bucketName, BlobId blobId) {
        return Mono.defer(() -> currentBlobStore.readBytes(bucketName, blobId))
            .onErrorResume(this::logAndReturnEmpty)
            .switchIfEmpty(legacyBlobStore.readBytes(bucketName, blobId));
    }

    private <T> Mono<BlobId> saveToCurrentFallbackIfFails(
        BucketName bucketName,
        T data,
        StoragePolicy storagePolicy,
        StorageOperation<T> currentSavingOperation,
        StorageOperation<T> fallbackSavingOperationSupplier) {

        return Mono.defer(() -> currentSavingOperation.save(bucketName, data, storagePolicy))
            .onErrorResume(this::logAndReturnEmpty)
            .switchIfEmpty(Mono.defer(() -> fallbackSavingOperationSupplier.save(bucketName, data, storagePolicy)));
    }

    private <T> Mono<T> logAndReturnEmpty(Throwable throwable) {
        LOGGER.error("error happens from current blob store, fall back to legacy blob store", throwable);
        return Mono.empty();
    }

    private <T> Mono<T> logDeleteFailureAndReturnEmpty(Throwable throwable) {
        LOGGER.error("Cannot delete from either legacy or current blob store", throwable);
        return Mono.empty();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("currentBlobStore", currentBlobStore)
            .add("legacyBlobStore", legacyBlobStore)
            .toString();
    }
}
