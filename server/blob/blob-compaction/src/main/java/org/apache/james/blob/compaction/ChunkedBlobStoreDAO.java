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

package org.apache.james.blob.compaction;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAO.Blob;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ChunkedBlobStoreDAO implements BlobStoreDAO {
    private final BlobStoreDAO plainChain;
    private final BlobStoreDAO rawStore;
    private final BlobIdRepairer blobIdRepairer;

    public ChunkedBlobStoreDAO(BlobStoreDAO plainChain, BlobStoreDAO rawStore) {
        this(plainChain, rawStore, Optional.empty());
    }

    public ChunkedBlobStoreDAO(BlobStoreDAO plainChain, BlobStoreDAO rawStore, Optional<BlobIdRepairer> blobIdRepairer) {
        this.plainChain = Preconditions.checkNotNull(plainChain, "'plainChain' must not be null");
        this.rawStore = Preconditions.checkNotNull(rawStore, "'rawStore' must not be null");
        this.blobIdRepairer = blobIdRepairer.orElse(BlobIdRepairer.NOOP);
    }

    @Override
    public InputStreamBlob read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        if (ChunkId.isChunkRef(blobId)) {
            ChunkId chunkId = ChunkId.parseChunkOrSlotRef(blobId.asString());
            if (chunkId.isSlotRef()) {
                BytesBlob bytes = Mono.from(readChunkSlotWithRepair(bucketName, chunkId, blobId)).block();
                return InputStreamBlob.of(new ByteArrayInputStream(bytes.payload()), bytes.metadata());
            }
            return rawStore.read(bucketName, chunkId.chunkBlobId());
        }
        return plainChain.read(bucketName, blobId);
    }

    @Override
    public Publisher<InputStreamBlob> readReactive(BucketName bucketName, BlobId blobId) {
        if (ChunkId.isChunkRef(blobId)) {
            ChunkId chunkId = ChunkId.parseChunkOrSlotRef(blobId.asString());
            if (chunkId.isSlotRef()) {
                return Mono.from(readChunkSlotWithRepair(bucketName, chunkId, blobId))
                    .map(bytesBlob -> InputStreamBlob.of(new ByteArrayInputStream(bytesBlob.payload()), bytesBlob.metadata()));
            }
            return rawStore.readReactive(bucketName, chunkId.chunkBlobId());
        }
        return plainChain.readReactive(bucketName, blobId);
    }

    @Override
    public Publisher<BytesBlob> readBytes(BucketName bucketName, BlobId blobId) {
        if (ChunkId.isChunkRef(blobId)) {
            ChunkId chunkId = ChunkId.parseChunkOrSlotRef(blobId.asString());
            if (chunkId.isSlotRef()) {
                return readChunkSlotWithRepair(bucketName, chunkId, blobId);
            }
            return rawStore.readBytes(bucketName, chunkId.chunkBlobId());
        }
        return plainChain.readBytes(bucketName, blobId);
    }

    private Mono<BytesBlob> readChunkSlotWithRepair(BucketName bucketName, ChunkId slotRef, BlobId originalBlobId) {
        return readChunkSlot(bucketName, slotRef)
            .onErrorResume(ObjectNotFoundException.class, notFound ->
                blobIdRepairer.repair(bucketName, originalBlobId)
                    .flatMap(repairedBlobId -> {
                        if (repairedBlobId.equals(originalBlobId)) {
                            return Mono.error(notFound);
                        }
                        if (ChunkId.isChunkRef(repairedBlobId)) {
                            ChunkId repairedChunkId = ChunkId.parseChunkOrSlotRef(repairedBlobId.asString());
                            if (repairedChunkId.isSlotRef()) {
                                return readChunkSlot(bucketName, repairedChunkId);
                            }
                            return Mono.from(rawStore.readBytes(bucketName, repairedChunkId.chunkBlobId()));
                        }
                        return Mono.from(plainChain.readBytes(bucketName, repairedBlobId));
                    })
                    .switchIfEmpty(Mono.error(notFound)));
    }

    private Mono<BytesBlob> readChunkSlot(BucketName bucketName, ChunkId slotRef) {
        if (slotRef.limit() > 0) {
            long offset = slotRef.offset();
            long limit = slotRef.limit();
            BlobId chunkObjectBlobId = slotRef.chunkBlobId();
            return rawStore.readRange(bucketName, chunkObjectBlobId, offset, offset + limit - 1)
                .publishOn(Schedulers.parallel())
                .flatMap(rangeSlice -> {
                    if (rangeSlice.data().length == 0 || offset >= rangeSlice.totalObjectSize()) {
                        return Mono.error(new ObjectNotFoundException("Slot not found at offset " + offset + " in chunk " + chunkObjectBlobId.asString()));
                    }
                    try {
                        byte[] decompressed = ChunkFormat.parseSlotBytes(rangeSlice.data(), offset);
                        return Mono.just(BytesBlob.of(decompressed));
                    } catch (ObjectStoreIOException e) {
                        return Mono.error(e);
                    }
                });
        } else {
            BlobId chunkObjectBlobId = slotRef.chunkBlobId();
            return rawStore.readRange(bucketName, chunkObjectBlobId, -65536, -1)
                .flatMap(tailSlice -> {
                    try {
                        ChunkFooter footer = ChunkFormat.readFooter(tailSlice.data(), tailSlice.totalObjectSize());
                        if (footer.slotCount() == 0) {
                            return Mono.just(BytesBlob.of(new byte[0]));
                        }
                        long start = slotRef.offset() > 0 ? slotRef.offset() : footer.slotStarts().get(0);
                        int slotIndex = footer.slotStarts().indexOf(start);
                        if (slotIndex == -1) {
                            return Mono.error(new ObjectNotFoundException("Slot not found at offset " + start + " in chunk " + chunkObjectBlobId.asString()));
                        }
                        long length = footer.slotLength(slotIndex);
                        return rawStore.readRange(bucketName, chunkObjectBlobId, start, start + length - 1)
                            .publishOn(Schedulers.parallel())
                            .flatMap(slice -> {
                                try {
                                    byte[] decompressed = ChunkFormat.parseSlotBytes(slice.data(), start);
                                    return Mono.just(BytesBlob.of(decompressed));
                                } catch (ObjectStoreIOException e) {
                                    return Mono.error(e);
                                }
                            });
                    } catch (ObjectStoreIOException e) {
                        return Mono.error(e);
                    }
                });
        }
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, Blob blob) {
        if (ChunkId.isChunkRef(blobId)) {
            ChunkId chunkId = ChunkId.parseChunkOrSlotRef(blobId.asString());
            if (chunkId.isSlotRef()) {
                return Mono.error(new UnsupportedOperationException(
                    "Individual chunk slot refs cannot be saved directly; chunks are immutable. Ref: " + blobId.asString()));
            }
            return rawStore.save(bucketName, chunkId.chunkBlobId(), blob);
        }
        return plainChain.save(bucketName, blobId, blob);
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, BlobId blobId) {
        if (ChunkId.isChunkRef(blobId)) {
            ChunkId chunkId = ChunkId.parseChunkOrSlotRef(blobId.asString());
            if (chunkId.isSlotRef()) {
                return Mono.error(new UnsupportedOperationException(
                    "Individual chunk slot refs cannot be deleted directly; slots are reclaimed via compaction GC. Ref: " + blobId.asString()));
            }
            return rawStore.delete(bucketName, chunkId.chunkBlobId());
        }
        return plainChain.delete(bucketName, blobId);
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, Collection<BlobId> blobIds) {
        return Mono.defer(() -> {
            for (BlobId blobId : blobIds) {
                if (ChunkId.isChunkRef(blobId)) {
                    ChunkId chunkId = ChunkId.parseChunkOrSlotRef(blobId.asString());
                    if (chunkId.isSlotRef()) {
                        return Mono.error(new UnsupportedOperationException(
                            "Individual chunk slot refs cannot be deleted directly; slots are reclaimed via compaction GC. Ref: " + blobId.asString()));
                    }
                }
            }
            List<BlobId> chunkDeletions = blobIds.stream()
                .filter(ChunkId::isChunkRef)
                .map(id -> ChunkId.parseChunkOrSlotRef(id.asString()).chunkBlobId())
                .toList();
            List<BlobId> plainDeletions = blobIds.stream()
                .filter(id -> !ChunkId.isChunkRef(id))
                .toList();

            return Flux.mergeDelayError(
                1,
                chunkDeletions.isEmpty() ? Mono.empty() : rawStore.delete(bucketName, chunkDeletions),
                plainDeletions.isEmpty() ? Mono.empty() : plainChain.delete(bucketName, plainDeletions)
            ).then();
        });
    }

    @Override
    public Publisher<Void> deleteBucket(BucketName bucketName) {
        return rawStore.deleteBucket(bucketName);
    }

    @Override
    public Publisher<BucketName> listBuckets() {
        return rawStore.listBuckets();
    }

    @Override
    public Publisher<BlobId> listBlobs(BucketName bucketName) {
        return rawStore.listBlobs(bucketName);
    }

    @Override
    public Publisher<BlobId> listBlobs(BucketName bucketName, String prefix) {
        return rawStore.listBlobs(bucketName, prefix);
    }

    @Override
    public Mono<RangeByteSlice> readRange(BucketName bucketName, BlobId blobId, long start, long end) {
        if (ChunkId.isChunkRef(blobId)) {
            ChunkId chunkId = ChunkId.parseChunkOrSlotRef(blobId.asString());
            return rawStore.readRange(bucketName, chunkId.chunkBlobId(), start, end);
        }
        return rawStore.readRange(bucketName, blobId, start, end);
    }
}
