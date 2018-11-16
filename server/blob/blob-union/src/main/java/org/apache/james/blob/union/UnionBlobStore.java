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
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;

public class UnionBlobStore implements BlobStore {

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
    public CompletableFuture<BlobId> save(byte[] data) {
        try {
            return saveToCurrentFallbackIfFails(
                currentBlobStore.save(data),
                () -> legacyBlobStore.save(data));
        } catch (Exception e) {
            LOGGER.error("exception directly happens while saving bytes data, fall back to legacy blob store", e);
            return legacyBlobStore.save(data);
        }
    }

    @Override
    public CompletableFuture<BlobId> save(InputStream data) {
        try {
            return saveToCurrentFallbackIfFails(
                currentBlobStore.save(data),
                () -> legacyBlobStore.save(data));
        } catch (Exception e) {
            LOGGER.error("exception directly happens while saving InputStream data, fall back to legacy blob store", e);
            return legacyBlobStore.save(data);
        }
    }

    @Override
    public CompletableFuture<byte[]> readBytes(BlobId blobId) {
        try {
            return readBytesFallBackIfFailsOrEmptyResult(blobId);
        } catch (Exception e) {
            LOGGER.error("exception directly happens while readBytes, fall back to legacy blob store", e);
            return legacyBlobStore.readBytes(blobId);
        }
    }

    @Override
    public InputStream read(BlobId blobId) {
        try {
            return readFallBackIfEmptyResult(blobId);
        } catch (Exception e) {
            LOGGER.error("exception directly happens while read, fall back to legacy blob store", e);
            return legacyBlobStore.read(blobId);
        }
    }

    private InputStream readFallBackIfEmptyResult(BlobId blobId) {
        return Optional.ofNullable(currentBlobStore.read(blobId))
            .map(PushbackInputStream::new)
            .filter(Throwing.predicate(this::streamHasContent).sneakyThrow())
            .<InputStream>map(Function.identity())
            .orElseGet(() -> legacyBlobStore.read(blobId));
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

    private CompletableFuture<byte[]> readBytesFallBackIfFailsOrEmptyResult(BlobId blobId) {
        return currentBlobStore.readBytes(blobId)
            .thenApply(Optional::ofNullable)
            .exceptionally(this::logAndReturnEmptyOptional)
            .thenCompose(maybeBytes -> readFromLegacyIfNeeded(maybeBytes, blobId));
    }

    private CompletableFuture<BlobId> saveToCurrentFallbackIfFails(
        CompletableFuture<BlobId> currentSavingOperation,
        Supplier<CompletableFuture<BlobId>> fallbackSavingOperationSupplier) {

        return currentSavingOperation
            .thenApply(Optional::ofNullable)
            .exceptionally(this::logAndReturnEmptyOptional)
            .thenCompose(maybeBlobId -> saveToLegacyIfNeeded(maybeBlobId, fallbackSavingOperationSupplier));
    }

    private <T> Optional<T> logAndReturnEmptyOptional(Throwable throwable) {
        LOGGER.error("error happens from current blob store, fall back to legacy blob store", throwable);
        return Optional.empty();
    }

    private CompletableFuture<BlobId> saveToLegacyIfNeeded(Optional<BlobId> maybeBlobId,
                                                           Supplier<CompletableFuture<BlobId>> saveToLegacySupplier) {
        return maybeBlobId
            .map(CompletableFuture::completedFuture)
            .orElseGet(saveToLegacySupplier);
    }

    private CompletableFuture<byte[]> readFromLegacyIfNeeded(Optional<byte[]> readFromCurrentResult, BlobId blodId) {
        return readFromCurrentResult
            .filter(this::hasContent)
            .map(CompletableFuture::completedFuture)
            .orElseGet(() -> legacyBlobStore.readBytes(blodId));
    }

    private boolean hasContent(byte [] bytes) {
        return bytes.length > 0;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("currentBlobStore", currentBlobStore)
            .add("legacyBlobStore", legacyBlobStore)
            .toString();
    }
}
