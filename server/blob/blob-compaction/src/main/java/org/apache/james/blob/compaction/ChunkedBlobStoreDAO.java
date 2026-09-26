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
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ChunkedBlobStoreDAO implements BlobStoreDAO {
    private final BlobStoreDAO rawStore;

    @Inject
    public ChunkedBlobStoreDAO(@Named("raw") BlobStoreDAO rawStore) {
        this.rawStore = Preconditions.checkNotNull(rawStore, "'rawStore' must not be null");
    }

    @Override
    public InputStreamBlob read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        if (!ChunkId.isChunkRef(blobId)) {
            return rawStore.read(bucketName, blobId);
        }
        ChunkId chunkId = ChunkId.parseChunkOrSlotRef(blobId.asString());
        if (!chunkId.isSlotRef()) {
            return rawStore.read(bucketName, chunkId.chunkBlobId());
        }
        BytesBlob bytes = Mono.from(readChunkSlot(bucketName, chunkId)).block();
        return InputStreamBlob.of(new ByteArrayInputStream(bytes.payload()), bytes.metadata());
    }

    @Override
    public Publisher<InputStreamBlob> readReactive(BucketName bucketName, BlobId blobId) {
        if (!ChunkId.isChunkRef(blobId)) {
            return rawStore.readReactive(bucketName, blobId);
        }
        ChunkId chunkId = ChunkId.parseChunkOrSlotRef(blobId.asString());
        if (!chunkId.isSlotRef()) {
            return rawStore.readReactive(bucketName, chunkId.chunkBlobId());
        }
        return Mono.from(readChunkSlot(bucketName, chunkId))
            .map(bytesBlob -> InputStreamBlob.of(new ByteArrayInputStream(bytesBlob.payload()), bytesBlob.metadata()));
    }

    @Override
    public Publisher<BytesBlob> readBytes(BucketName bucketName, BlobId blobId) {
        if (!ChunkId.isChunkRef(blobId)) {
            return rawStore.readBytes(bucketName, blobId);
        }
        ChunkId chunkId = ChunkId.parseChunkOrSlotRef(blobId.asString());
        if (!chunkId.isSlotRef()) {
            return rawStore.readBytes(bucketName, chunkId.chunkBlobId());
        }
        return readChunkSlot(bucketName, chunkId);
    }

    private Mono<BytesBlob> readChunkSlot(BucketName bucketName, ChunkId slotRef) {
        if (slotRef.limit() > 0) {
            long offset = slotRef.offset();
            long limit = slotRef.limit();
            BlobId chunkObjectBlobId = slotRef.chunkBlobId();
            return rawStore.readRange(bucketName, chunkObjectBlobId, offset, offset + limit - 1)
                .publishOn(Schedulers.parallel())
                .flatMap(rangeBlob -> {
                    try {
                        byte[] rangeData = rangeBlob.asBytes().payload();
                        long totalSize = BlobStoreDAO.totalObjectSize(rangeBlob);
                        if (rangeData.length == 0 || offset >= totalSize) {
                            return Mono.error(new ObjectNotFoundException("Slot not found at offset " + offset + " in chunk " + chunkObjectBlobId.asString()));
                        }
                        BlobSlot slot = ChunkFormat.parseSlot(rangeData, offset);
                        return Mono.just(slot.toBlob());
                    } catch (IOException e) {
                        return Mono.error(new ObjectStoreIOException("Error reading slot for chunk " + chunkObjectBlobId.asString(), e));
                    }
                });
        } else {
            BlobId chunkObjectBlobId = slotRef.chunkBlobId();
            return rawStore.readRange(bucketName, chunkObjectBlobId, -65536, -1)
                .flatMap(tailBlob -> {
                    try {
                        byte[] tailData = tailBlob.asBytes().payload();
                        long totalSize = BlobStoreDAO.totalObjectSize(tailBlob);
                        ChunkFooter footer = ChunkFormat.readFooter(tailData, totalSize);
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
                            .flatMap(sliceBlob -> {
                                try {
                                    byte[] sliceData = sliceBlob.asBytes().payload();
                                    BlobSlot slot = ChunkFormat.parseSlot(sliceData, start);
                                    return Mono.just(slot.toBlob());
                                } catch (IOException e) {
                                    return Mono.error(new ObjectStoreIOException("Error reading slot at start " + start, e));
                                }
                            });
                    } catch (IOException e) {
                        return Mono.error(new ObjectStoreIOException("Error reading chunk footer for " + chunkObjectBlobId.asString(), e));
                    }
                });
        }
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, Blob blob) {
        if (!ChunkId.isChunkRef(blobId)) {
            return rawStore.save(bucketName, blobId, blob);
        }
        ChunkId chunkId = ChunkId.parseChunkOrSlotRef(blobId.asString());
        if (chunkId.isSlotRef()) {
            return Mono.error(new UnsupportedOperationException(
                "Individual chunk slot refs cannot be saved directly; chunks are immutable. Ref: " + blobId.asString()));
        }
        return rawStore.save(bucketName, chunkId.chunkBlobId(), blob);
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, BlobId blobId) {
        if (!ChunkId.isChunkRef(blobId)) {
            return rawStore.delete(bucketName, blobId);
        }
        ChunkId chunkId = ChunkId.parseChunkOrSlotRef(blobId.asString());
        if (chunkId.isSlotRef()) {
            return Mono.error(new UnsupportedOperationException(
                "Individual chunk slot refs cannot be deleted directly; slots are reclaimed via compaction GC. Ref: " + blobId.asString()));
        }
        return rawStore.delete(bucketName, chunkId.chunkBlobId());
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, Collection<BlobId> blobIds) {
        for (BlobId blobId : blobIds) {
            if (ChunkId.isChunkRef(blobId) && ChunkId.parseChunkOrSlotRef(blobId.asString()).isSlotRef()) {
                return Mono.error(new UnsupportedOperationException(
                    "Individual chunk slot refs cannot be deleted directly; slots are reclaimed via compaction GC. Ref: " + blobId.asString()));
            }
        }
        List<BlobId> mapped = blobIds.stream()
            .map(id -> ChunkId.isChunkRef(id) ? ChunkId.parseChunkOrSlotRef(id.asString()).chunkBlobId() : id)
            .toList();
        return rawStore.delete(bucketName, mapped);
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
    public Mono<Blob> readRange(BucketName bucketName, BlobId blobId, long start, long end) {
        if (ChunkId.isChunkRef(blobId)) {
            throw new IllegalArgumentException("Cannot readRange on a chunk reference: " + blobId.asString());
        }
        return rawStore.readRange(bucketName, blobId, start, end);
    }
}
