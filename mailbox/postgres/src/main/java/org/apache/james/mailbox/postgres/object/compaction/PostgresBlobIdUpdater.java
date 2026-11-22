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

package org.apache.james.mailbox.postgres.object.compaction;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobIdUpdater;
import org.apache.james.mailbox.postgres.object.compaction.PostgresBlobReferenceDAO.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Relocates message body and attachment references stored in PostgreSQL. Headers are stored inline. */
public class PostgresBlobIdUpdater implements BlobIdUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresBlobIdUpdater.class);

    public static class Factory implements BlobIdUpdater.Factory {
        private final PostgresBlobReferenceDAO references;
        private final PostgresBlobReferenceIndex index;

        @Inject
        public Factory(PostgresBlobReferenceDAO references, PostgresBlobReferenceIndex index) {
            this.references = references;
            this.index = index;
        }

        @Override
        public Mono<BlobIdUpdater> forPredicate(Predicate<BlobId> generationCondition, Consumer<BlobId> referencedBlobIdObserver) {
            Objects.requireNonNull(generationCondition);
            Objects.requireNonNull(referencedBlobIdObserver);
            return Mono.usingWhen(
                Mono.fromSupplier(() -> new PostgresBlobIdUpdater(RunId.generate(), index)),
                updater -> updater.createIndex.then(Mono.fromRunnable(updater::startHeartbeat))
                    .thenMany(references.listReferencePages()
                        .map(page -> select(page, generationCondition, referencedBlobIdObserver))
                        .concatMap(selected -> index.store(updater.runId, selected)))
                    .then(Mono.fromCallable(() -> {
                        updater.checkUsable();
                        return (BlobIdUpdater) updater;
                    })),
                updater -> Mono.empty(),
                (updater, failure) -> updater.cleanup(),
                PostgresBlobIdUpdater::cleanup);
        }

        private static List<Reference> select(List<Reference> page, Predicate<BlobId> generationCondition, Consumer<BlobId> referencedBlobIdObserver) {
            page.forEach(reference -> referencedBlobIdObserver.accept(reference.blobId()));
            return page.stream()
                .filter(reference -> generationCondition.test(reference.blobId()))
                .toList();
        }
    }

    private final RunId runId;
    private final PostgresBlobReferenceIndex index;
    private final Mono<Void> createIndex;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<Disposable> heartbeatTask = new AtomicReference<>();
    private final AtomicReference<Throwable> heartbeatFailure = new AtomicReference<>();

    private PostgresBlobIdUpdater(RunId runId, PostgresBlobReferenceIndex index) {
        this.runId = runId;
        this.index = index;
        // Let creation finish before cancellation can DROP the table.
        this.createIndex = Mono.defer(() -> index.create(runId)).cache();
        LOGGER.info("Preparing blob reference snapshot {}", runId);
    }

    @Override
    public Mono<Void> replaceReferences(BlobId oldId, BlobId newId) {
        Objects.requireNonNull(oldId);
        Objects.requireNonNull(newId);
        return Mono.defer(() -> {
            checkUsable();
            // Keep the snapshot until cleanup so a failure can be retried safely.
            return createIndex.then(index.replace(runId, oldId, newId));
        });
    }

    @Override
    public Mono<Void> cleanup() {
        return Mono.defer(() -> {
            closed.set(true);
            disposeHeartbeat();
            return createIndex.onErrorComplete()
                .then(Mono.defer(() -> index.cleanup(runId)))
                .doOnSuccess(any -> LOGGER.info("Cleaned blob reference snapshot {}", runId))
                .doOnError(error -> LOGGER.warn("Unable to clean blob reference snapshot {}. Cleanup can be retried using this run ID.", runId, error));
        });
    }

    private void startHeartbeat() {
        Disposable task = Flux.interval(index.heartbeatInterval())
            .onBackpressureDrop()
            .concatMap(ignored -> index.heartbeat(runId))
            .subscribe(ignored -> { }, error -> {
                heartbeatFailure.compareAndSet(null, error);
                LOGGER.error("Blob reference snapshot heartbeat failed for {}. The compaction run can no longer continue safely.", runId, error);
            });
        if (!heartbeatTask.compareAndSet(null, task)) {
            task.dispose();
        }
    }

    private void disposeHeartbeat() {
        Disposable task = heartbeatTask.getAndSet(null);
        if (task != null) {
            task.dispose();
        }
    }

    private void checkUsable() {
        Preconditions.checkState(!closed.get(), "Blob ID updater has been cleaned up");
        Throwable failure = heartbeatFailure.get();
        if (failure != null) {
            throw new IllegalStateException("Blob reference snapshot heartbeat failed", failure);
        }
    }
}
