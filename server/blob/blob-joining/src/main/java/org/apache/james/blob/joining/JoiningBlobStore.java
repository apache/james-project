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

package org.apache.james.blob.joining;

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

public class JoiningBlobStore implements BlobStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(JoiningBlobStore.class);
    private static final int UN_AVAILABLE = -1;

    private final BlobStore primaryBlobStore;
    private final BlobStore secondaryBlobStore;

    @VisibleForTesting
    JoiningBlobStore(BlobStore primaryBlobStore, BlobStore secondaryBlobStore) {
        this.primaryBlobStore = primaryBlobStore;
        this.secondaryBlobStore = secondaryBlobStore;
    }

    @Override
    public CompletableFuture<BlobId> save(byte[] data) {
        try {
            return saveToPrimaryFallbackIfFails(
                primaryBlobStore.save(data),
                () -> secondaryBlobStore.save(data));
        } catch (Exception e) {
            LOGGER.error("exception directly happens while saving bytes data, fall back to secondary blob store", e);
            return secondaryBlobStore.save(data);
        }
    }

    @Override
    public CompletableFuture<BlobId> save(InputStream data) {
        try {
            return saveToPrimaryFallbackIfFails(
                primaryBlobStore.save(data),
                () -> secondaryBlobStore.save(data));
        } catch (Exception e) {
            LOGGER.error("exception directly happens while saving InputStream data, fall back to secondary blob store", e);
            return secondaryBlobStore.save(data);
        }
    }

    @Override
    public CompletableFuture<byte[]> readBytes(BlobId blobId) {
        try {
            return readBytesFallBackIfFailsOrEmptyResult(blobId);
        } catch (Exception e) {
            LOGGER.error("exception directly happens while readBytes, fall back to secondary blob store", e);
            return secondaryBlobStore.readBytes(blobId);
        }
    }

    @Override
    public InputStream read(BlobId blobId) {
        try {
            return readFallBackIfEmptyResult(blobId);
        } catch (Exception e) {
            LOGGER.error("exception directly happens while read, fall back to secondary blob store", e);
            return secondaryBlobStore.read(blobId);
        }
    }

    private InputStream readFallBackIfEmptyResult(BlobId blobId) {
        return Optional.ofNullable(primaryBlobStore.read(blobId))
            .map(PushbackInputStream::new)
            .filter(Throwing.predicate(this::streamHasContent).sneakyThrow())
            .<InputStream>map(Function.identity())
            .orElseGet(() -> secondaryBlobStore.read(blobId));
    }

    @VisibleForTesting
    boolean streamHasContent(PushbackInputStream pushBackIS) throws IOException {
        int byteRead = pushBackIS.read();
        if (byteRead != UN_AVAILABLE) {
            pushBackIS.unread(byteRead);
            return true;
        }
        return false;
    }

    private CompletableFuture<byte[]> readBytesFallBackIfFailsOrEmptyResult(BlobId blobId) {
        return primaryBlobStore.readBytes(blobId)
            .thenApply(Optional::ofNullable)
            .exceptionally(this::logAndReturnEmptyOptional)
            .thenCompose(maybeBytes -> readFromSecondaryIfNeeded(maybeBytes, blobId));
    }

    private CompletableFuture<BlobId> saveToPrimaryFallbackIfFails(
        CompletableFuture<BlobId> primarySavingOperation,
        Supplier<CompletableFuture<BlobId>> fallbackSavingOperationSupplier) {

        return primarySavingOperation
            .thenApply(Optional::ofNullable)
            .exceptionally(this::logAndReturnEmptyOptional)
            .thenCompose(maybeBlobId -> saveToSecondaryIfNeeded(maybeBlobId, fallbackSavingOperationSupplier));
    }

    private <T> Optional<T> logAndReturnEmptyOptional(Throwable throwable) {
        LOGGER.error("primary completed exceptionally, fall back to second blob store", throwable);
        return Optional.empty();
    }

    private CompletableFuture<BlobId> saveToSecondaryIfNeeded(Optional<BlobId> maybeBlobId,
                                                              Supplier<CompletableFuture<BlobId>> saveToSecondarySupplier) {
        return maybeBlobId
            .map(CompletableFuture::completedFuture)
            .orElseGet(saveToSecondarySupplier);
    }

    private CompletableFuture<byte[]> readFromSecondaryIfNeeded(Optional<byte[]> readFromPrimaryResult, BlobId blodId) {
        return readFromPrimaryResult
            .filter(this::hasContent)
            .map(CompletableFuture::completedFuture)
            .orElseGet(() -> secondaryBlobStore.readBytes(blodId));
    }

    private boolean hasContent(byte [] bytes) {
        return bytes.length > 0;
    }
}
