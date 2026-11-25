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
import java.util.Optional;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ChunkMarker;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

/**
 * Serves slot addresses from chunks; every other blob is delegated unchanged.
 *
 * <p>Sits directly above the storage DAO so that compression and encryption decorators apply to each logical blob.
 * A slot is returned with the payload and metadata it was stored with. Chunks are written and deleted by compaction
 * only: saving a chunk id is refused and deleting one is ignored, as its space is reclaimed by re-compaction.</p>
 */
public class ChunkedBlobStoreDAO implements BlobStoreDAO {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkedBlobStoreDAO.class);

    private final BlobStoreDAO underlying;
    private final BlobId.Factory blobIdFactory;

    public ChunkedBlobStoreDAO(BlobStoreDAO underlying, BlobId.Factory blobIdFactory) {
        this.underlying = underlying;
        this.blobIdFactory = blobIdFactory;
    }

    @Override
    public InputStreamBlob read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        return SlotAddress.parse(blobId, blobIdFactory)
            .map(address -> readSlot(bucketName, address).map(ChunkedBlobStoreDAO::asInputStreamBlob).block())
            .orElseGet(() -> underlying.read(bucketName, blobId));
    }

    @Override
    public Publisher<InputStreamBlob> readReactive(BucketName bucketName, BlobId blobId) {
        return SlotAddress.parse(blobId, blobIdFactory)
            .map(address -> readSlot(bucketName, address).map(ChunkedBlobStoreDAO::asInputStreamBlob))
            .orElseGet(() -> Mono.from(underlying.readReactive(bucketName, blobId)));
    }

    @Override
    public Publisher<BytesBlob> readBytes(BucketName bucketName, BlobId blobId) {
        return SlotAddress.parse(blobId, blobIdFactory)
            .map(address -> readSlot(bucketName, address))
            .orElseGet(() -> Mono.from(underlying.readBytes(bucketName, blobId)));
    }

    /** Ranged reads are issued by this DAO; slot addresses do not serve them. */
    @Override
    public Mono<Blob> readRange(BucketName bucketName, BlobId blobId, long start, Optional<Long> end) {
        if (SlotAddress.parse(blobId, blobIdFactory).isPresent()) {
            return Mono.error(new IllegalArgumentException("Ranged reads of slot addresses are not supported: " + blobId.asString()));
        }
        return underlying.readRange(bucketName, blobId, start, end);
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, Blob blob) {
        if (ChunkMarker.isChunkId(blobId)) {
            return Mono.error(new IllegalArgumentException("Chunks are written by compaction only: " + blobId.asString()));
        }
        return underlying.save(bucketName, blobId, blob);
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, BlobId blobId) {
        if (ChunkMarker.isChunkId(blobId)) {
            return Mono.empty();
        }
        return underlying.delete(bucketName, blobId);
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, Collection<BlobId> blobIds) {
        List<BlobId> standaloneBlobIds = blobIds.stream()
            .filter(blobId -> !ChunkMarker.isChunkId(blobId))
            .toList();
        if (standaloneBlobIds.isEmpty()) {
            return Mono.empty();
        }
        return underlying.delete(bucketName, standaloneBlobIds);
    }

    @Override
    public Publisher<Void> deleteBucket(BucketName bucketName) {
        return underlying.deleteBucket(bucketName);
    }

    @Override
    public Publisher<BucketName> listBuckets() {
        return underlying.listBuckets();
    }

    @Override
    public Publisher<BlobId> listBlobs(BucketName bucketName) {
        return underlying.listBlobs(bucketName);
    }

    @Override
    public Publisher<BlobId> listBlobs(BucketName bucketName, String prefix) {
        return underlying.listBlobs(bucketName, prefix);
    }

    private Mono<BytesBlob> readSlot(BucketName bucketName, SlotAddress address) {
        return readSlotRange(bucketName, address)
            .onErrorResume(CorruptedSlotException.class, corruption -> readSlotFromWholeChunk(bucketName, address, corruption));
    }

    private Mono<BytesBlob> readSlotRange(BucketName bucketName, SlotAddress address) {
        SlotLocation location = address.location();
        return underlying.readRange(bucketName, address.chunk(), location.offset(), Optional.of(location.lastByte()))
            .onErrorMap(IllegalArgumentException.class, e -> new ChunkFormatException("Slot " + location + " lies outside chunk " + address.chunk().asString(), e))
            .flatMap(range -> Mono.fromCallable(() -> ChunkParser.parseSlot(payload(range), location)));
    }

    /** Lets the object store verify, and if possible repair, the chunk before giving up. */
    private Mono<BytesBlob> readSlotFromWholeChunk(BucketName bucketName, SlotAddress address, CorruptedSlotException corruption) {
        LOGGER.warn("Slot {} of chunk {} failed its checksum, reading the whole chunk", address.location(), address.chunk().asString(), corruption);
        return Mono.from(underlying.readBytes(bucketName, address.chunk()))
            .flatMap(chunk -> Mono.fromCallable(() -> ChunkParser.parseSlot(ChunkParser.sliceSlot(chunk.payload(), address.location()), address.location())));
    }

    private static byte[] payload(Blob blob) {
        try {
            return blob.asBytes().payload();
        } catch (IOException e) {
            throw new ObjectStoreIOException("Unable to read slot bytes", e);
        }
    }

    private static InputStreamBlob asInputStreamBlob(BytesBlob blob) {
        return InputStreamBlob.of(new ByteArrayInputStream(blob.payload()), blob.metadata());
    }
}
