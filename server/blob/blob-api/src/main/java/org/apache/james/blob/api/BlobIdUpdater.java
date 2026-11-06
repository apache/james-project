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

package org.apache.james.blob.api;

import java.util.function.Consumer;
import java.util.function.Predicate;

import reactor.core.publisher.Mono;

/**
 * Replaces references without exposing the storage backend's metadata model.
 *
 * <p>Use a prepared updater for one pass over a completed generation. Preparation must finish before replacing
 * references. Callers must serialize passes affecting the same generation and must not delete an old object until
 * replacement completes successfully. No new authoritative references into that generation may be created during
 * the pass. The updater itself never writes or deletes blob payloads.</p>
 */
public interface BlobIdUpdater {
    interface Factory {
        /**
         * Scans authoritative metadata once, observing every reference (duplicates are allowed) and indexing
         * references matching the predicate. Observers must be non-blocking. Each subscription prepares a new updater.
         */
        Mono<BlobIdUpdater> forPredicate(Predicate<BlobId> generationCondition, Consumer<BlobId> referencedBlobIdObserver);
    }

    /**
     * Replaces references from the prepared snapshot. Retry the same old/new pair after partial failure.
     * Prepare a new updater to process references created after the snapshot or identifiers introduced by this pass.
     */
    Mono<Void> replaceReferences(BlobId oldId, BlobId newId);

    /**
     * Releases temporary storage. Must be called on completion or abandonment, after all replacement operations finish.
     * Cleanup is idempotent and can be retried. Replacement must not be called after cleanup.
     */
    Mono<Void> cleanup();
}
