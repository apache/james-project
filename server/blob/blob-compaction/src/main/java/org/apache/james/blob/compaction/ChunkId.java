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

import java.security.SecureRandom;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.ChunkMarker;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;

import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;

public record ChunkId(int family, long generation, String randomPart, long offset, long limit) implements BlobId {
    private static final String CHUNK_MARKER = "_chunk";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RANDOM_BYTES_COUNT = 16;
    private static final int MIN_RANDOM_LENGTH = 16;

    public ChunkId {
        Preconditions.checkArgument(family > 0, "'family' must be strictly positive");
        Preconditions.checkArgument(generation >= 0, "'generation' must not be negative");
        Preconditions.checkNotNull(randomPart, "'randomPart' must not be null");
        Preconditions.checkArgument(offset >= 0, "'offset' must not be negative");
        Preconditions.checkArgument(limit >= 0, "'limit' must not be negative");
    }

    public static ChunkId ofChunk(int family, long generation) {
        Preconditions.checkArgument(family > 0, "'family' must be strictly positive");
        Preconditions.checkArgument(generation >= 0, "'generation' must not be negative");

        byte[] randomBytes = new byte[RANDOM_BYTES_COUNT];
        RANDOM.nextBytes(randomBytes);
        String randomPart = BaseEncoding.base64Url().omitPadding().encode(randomBytes);

        return new ChunkId(family, generation, randomPart, 0L, 0L);
    }

    public static ChunkId ofChunk(GenerationAwareBlobId.Configuration configuration, long generation) {
        Preconditions.checkNotNull(configuration, "'configuration' must not be null");
        return ofChunk(configuration.getFamily(), generation);
    }

    public static ChunkId slotRef(ChunkId chunkId, long offset, long limit) {
        Preconditions.checkNotNull(chunkId, "'chunkId' must not be null");
        Preconditions.checkArgument(offset >= 0, "'offset' must not be negative");
        Preconditions.checkArgument(limit >= 0, "'limit' must not be negative");

        return new ChunkId(chunkId.family(), chunkId.generation(), chunkId.randomPart(), offset, limit);
    }

    public static ChunkId slotRef(String chunkIdStr, long offset, long limit) {
        Preconditions.checkNotNull(chunkIdStr, "'chunkIdStr' must not be null");
        Preconditions.checkArgument(offset >= 0, "'offset' must not be negative");
        Preconditions.checkArgument(limit >= 0, "'limit' must not be negative");

        ChunkId base = parseChunkOrSlotRef(chunkIdStr);
        return new ChunkId(base.family(), base.generation(), base.randomPart(), offset, limit);
    }

    public static boolean isChunkRef(BlobId blobId) {
        if (blobId == null) {
            return false;
        }
        return isChunkRef(blobId.asString());
    }

    public static boolean isChunkRef(String id) {
        return ChunkMarker.looksLikeChunkId(id);
    }

    /**
     * Parses a chunk slot reference string formatted as {@code {family}_{generation}_chunk{randomPart}~{offset}~{limit}}.
     *
     * @param id non-null serialized chunk slot reference
     * @return the parsed {@link ChunkId}
     * @throws IllegalArgumentException if the format is invalid or values are out of bounds
     */
    public static ChunkId parse(String id) {
        Preconditions.checkNotNull(id, "'id' must not be null");
        int firstTilde = id.indexOf('~');
        Preconditions.checkArgument(firstTilde != -1, "Missing '~' in chunk-slot ref: " + id);
        int secondTilde = id.indexOf('~', firstTilde + 1);
        Preconditions.checkArgument(secondTilde != -1, "Missing second '~' in chunk-slot ref: " + id);

        String basePart = id.substring(0, firstTilde);
        long offset;
        long limit;
        try {
            offset = Long.parseLong(id.substring(firstTilde + 1, secondTilde));
            limit = Long.parseLong(id.substring(secondTilde + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid offset or limit in chunk ref: " + id, e);
        }
        Preconditions.checkArgument(offset >= 0, "'offset' must not be negative");
        Preconditions.checkArgument(limit >= 0, "'limit' must not be negative");

        return parseBase(basePart, offset, limit);
    }

    public static ChunkId parseChunk(String id) {
        Preconditions.checkNotNull(id, "'id' must not be null");
        return parseBase(id, 0L, 0L);
    }

    public static ChunkId parseChunkOrSlotRef(String id) {
        Preconditions.checkNotNull(id, "'id' must not be null");
        int firstTilde = id.indexOf('~');
        if (firstTilde == -1) {
            return parseBase(id, 0L, 0L);
        }
        return parse(id);
    }

    private static ChunkId parseBase(String basePart, long offset, long limit) {
        int firstUnder = basePart.indexOf('_');
        Preconditions.checkArgument(firstUnder > 0, "Missing family delimiter '_' in chunk id: " + basePart);
        int chunkIndex = basePart.indexOf(CHUNK_MARKER, firstUnder + 1);
        Preconditions.checkArgument(chunkIndex > firstUnder, "Missing '_chunk' marker in chunk id: " + basePart);

        int family;
        long generation;
        try {
            family = Integer.parseInt(basePart.substring(0, firstUnder));
            generation = Long.parseLong(basePart.substring(firstUnder + 1, chunkIndex));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid family or generation in chunk id: " + basePart, e);
        }

        Preconditions.checkArgument(family > 0, "'family' must be strictly positive");
        Preconditions.checkArgument(generation >= 0, "'generation' must not be negative");

        String randomPart = basePart.substring(chunkIndex + CHUNK_MARKER.length());
        Preconditions.checkArgument(randomPart.length() >= MIN_RANDOM_LENGTH,
            "Random part of chunk id is too short: " + randomPart);

        return new ChunkId(family, generation, randomPart, offset, limit);
    }

    public boolean isSlotRef() {
        return offset > 0 || limit > 0;
    }

    public String chunkId() {
        return family + "_" + generation + "_chunk" + randomPart;
    }

    public BlobId chunkBlobId() {
        return new PlainBlobId(chunkId());
    }

    public BlobId asBlobId() {
        return this;
    }

    @Override
    public String asString() {
        return chunkId() + "~" + offset + "~" + limit;
    }

    @Override
    public BlobId withSuffix(String suffix) {
        throw new UnsupportedOperationException("ChunkId does not support withSuffix");
    }

    @Override
    public String toString() {
        return asString();
    }
}
