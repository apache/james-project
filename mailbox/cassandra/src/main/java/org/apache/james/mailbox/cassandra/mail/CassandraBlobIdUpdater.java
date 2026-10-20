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

package org.apache.james.mailbox.cassandra.mail;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobIdUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

/** Relocates authoritative references; secondary header indexes are repaired on a missing-blob read. */
public class CassandraBlobIdUpdater implements BlobIdUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraBlobIdUpdater.class);

    public static class Factory implements BlobIdUpdater.Factory {
        private final CassandraBlobReferenceDAO references;
        private final CassandraBlobReferenceIndexDAO index;

        @Inject
        public Factory(CassandraBlobReferenceDAO references, CassandraBlobReferenceIndexDAO index) {
            this.references = references;
            this.index = index;
        }

        @Override
        public Mono<BlobIdUpdater> forPredicate(Predicate<BlobId> generationCondition, Consumer<BlobId> referencedBlobIdObserver) {
            Objects.requireNonNull(generationCondition);
            Objects.requireNonNull(referencedBlobIdObserver);
            return Mono.usingWhen(
                Mono.fromSupplier(() -> new CassandraBlobIdUpdater(UUID.randomUUID(), references, index)),
                updater -> references.listReferences()
                    .doOnNext(reference -> referencedBlobIdObserver.accept(reference.blobId()))
                    .filter(reference -> generationCondition.test(reference.blobId()))
                    .concatMap(reference -> index.store(updater.runId, reference))
                    .then(Mono.just((BlobIdUpdater) updater)),
                updater -> Mono.empty(),
                (updater, failure) -> updater.cleanup(),
                CassandraBlobIdUpdater::cleanup);
        }
    }

    private final UUID runId;
    private final CassandraBlobReferenceDAO references;
    private final CassandraBlobReferenceIndexDAO index;
    private final AtomicBoolean closed = new AtomicBoolean();

    private CassandraBlobIdUpdater(UUID runId, CassandraBlobReferenceDAO references, CassandraBlobReferenceIndexDAO index) {
        this.runId = runId;
        this.references = references;
        this.index = index;
        LOGGER.info("Preparing blob reference snapshot {}", runId);
    }

    @Override
    public Mono<Void> replaceReferences(BlobId oldId, BlobId newId) {
        Objects.requireNonNull(oldId);
        Objects.requireNonNull(newId);
        return Mono.defer(() -> {
            Preconditions.checkState(!closed.get(), "Blob ID updater has been cleaned up");
            // Keep the snapshot until cleanup so a partial failure can be retried safely.
            return index.retrieve(runId, oldId)
                .concatMap(reference -> references.replace(reference, newId))
                .then();
        });
    }

    @Override
    public Mono<Void> cleanup() {
        return Mono.defer(() -> {
            closed.set(true);
            return index.cleanup(runId)
                .doOnSuccess(any -> LOGGER.info("Cleaned blob reference snapshot {}", runId))
                .doOnError(error -> LOGGER.warn("Unable to clean blob reference snapshot {}. Cleanup can be retried using this run ID.", runId, error));
        });
    }
}
