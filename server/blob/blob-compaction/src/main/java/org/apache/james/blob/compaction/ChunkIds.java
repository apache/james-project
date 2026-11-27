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

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.ChunkMarker;

import com.google.common.base.Preconditions;

/**
 * Allocates chunk blob ids recognized by {@link ChunkMarker}, short enough for any slot address built on them to fit
 * {@link SlotAddress#MAX_LENGTH}.
 */
final class ChunkIds {
    private static final String CHUNK_MARKER = "chunk";
    // Family and generation as written by the generation aware blob id factories, with "_" or "/" as separator.
    private static final Pattern FAMILY_AND_GENERATION = Pattern.compile("^[1-9]\\d*(?<separator>[_/])\\d+\\k<separator>");

    private ChunkIds() {
    }

    /** A new chunk id in the same family, generation and layout as {@code sameGeneration}. */
    static BlobId newChunk(BlobId sameGeneration, BlobId.Factory blobIdFactory) {
        Matcher matcher = FAMILY_AND_GENERATION.matcher(sameGeneration.asString());
        Preconditions.checkArgument(matcher.lookingAt(), "%s carries no family and generation", sameGeneration.asString());

        String familyAndGeneration = matcher.group();
        String identifier = UUID.randomUUID().toString().replace("-", "");
        BlobId chunk = parse(familyAndGeneration + CHUNK_MARKER + matcher.group("separator") + identifier, blobIdFactory, sameGeneration);
        // The factory may rewrite or drop a family or generation it cannot represent as written, e.g. leading zeros
        // or integer overflow: the chunk would then land elsewhere.
        boolean keptFamilyAndGeneration = chunk.asString().startsWith(familyAndGeneration) && ChunkMarker.isChunkId(chunk);
        Preconditions.checkArgument(keptFamilyAndGeneration, "Cannot allocate a chunk in the family and generation of %s",
            sameGeneration.asString());
        Preconditions.checkArgument(chunk.asString().length() + SlotAddress.MAX_LOCATION_LENGTH <= SlotAddress.MAX_LENGTH,
            "Family and generation of %s leave no room for slot addresses", sameGeneration.asString());
        return chunk;
    }

    private static BlobId parse(String chunk, BlobId.Factory blobIdFactory, BlobId sameGeneration) {
        try {
            return blobIdFactory.parse(chunk);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cannot allocate a chunk in the family and generation of " + sameGeneration.asString(), e);
        }
    }
}
