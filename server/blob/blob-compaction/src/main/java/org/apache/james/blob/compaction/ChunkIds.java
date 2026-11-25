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

/** Allocates chunk blob ids recognized by {@link ChunkMarker}. */
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

        String identifier = UUID.randomUUID().toString().replace("-", "");
        BlobId chunk = blobIdFactory.parse(matcher.group() + CHUNK_MARKER + matcher.group("separator") + identifier);
        Preconditions.checkState(ChunkMarker.isChunkId(chunk), "%s is not recognized as a chunk", chunk.asString());
        return chunk;
    }
}
