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

import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

/**
 * Populates the blob store cache for a blob that was written through {@link BlobStoreDAO} rather than
 * through {@link BlobStore}.
 *
 * <p>Callers needing the metadata of a blob have to go through {@link BlobStoreDAO}, which sits below the
 * caching decorator and thus knows nothing of {@link BlobStore.StoragePolicy}. This callback gives them
 * back the caching that a {@code SIZE_BASED} save would have performed.</p>
 *
 * <p>The caller vouches for the blob being worth caching: stored in the default bucket, and semantically
 * what a non-{@code LOW_COST} storage policy expresses. Implementations remain free to decline, typically
 * on payload size.</p>
 */
@FunctionalInterface
public interface BlobStoreCacheCallback {
    BlobStoreCacheCallback NOOP = (blobId, bytes) -> Mono.empty();

    Publisher<Void> cacheIfNeeded(BlobId blobId, byte[] bytes);
}
