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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.ChunkMarker;

import com.google.common.base.Preconditions;

/**
 * Blob identifier of a slot: {@code <chunk blob id>~<offset>~<length>}.
 *
 * <p>The chunk blob id is opaque here: family and generation remain the concern of the blob id factory. Addresses are at
 * most {@link #MAX_LENGTH} characters, so that they fit the mailbox blob id columns (PostgreSQL stores them as
 * {@code VARCHAR(200)}).</p>
 */
record SlotAddress(BlobId chunk, SlotLocation location) {
    static final int MAX_LENGTH = 200;
    static final int MAX_LOCATION_LENGTH = 2 * (1 + String.valueOf(Long.MAX_VALUE).length());
    private static final char SEPARATOR = '~';
    private static final String CHUNK = "(?<chunk>[^" + SEPARATOR + "]+)";
    // Canonical decimal numbers only, so that parsing then serializing restores the input.
    private static final String OFFSET = "(?<offset>0|[1-9]\\d{0,18})";
    private static final String LENGTH = "(?<length>0|[1-9]\\d{0,18})";
    private static final Pattern ADDRESS = Pattern.compile("^" + CHUNK + SEPARATOR + OFFSET + SEPARATOR + LENGTH + "$");

    static Optional<SlotAddress> parse(BlobId blobId, BlobId.Factory blobIdFactory) {
        Matcher matcher = ADDRESS.matcher(blobId.asString());
        if (!ChunkMarker.isChunkId(blobId) || !matcher.matches()) {
            return Optional.empty();
        }
        try {
            BlobId chunk = blobIdFactory.parse(matcher.group("chunk"));
            SlotLocation location = new SlotLocation(Long.parseLong(matcher.group("offset")), Long.parseLong(matcher.group("length")));
            return Optional.of(new SlotAddress(chunk, location))
                .filter(address -> address.serialize().equals(blobId.asString()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    SlotAddress {
        Preconditions.checkArgument(ChunkMarker.isChunkId(chunk), "%s is not a chunk", chunk.asString());
        Preconditions.checkArgument(chunk.asString().indexOf(SEPARATOR) == -1, "%s is a slot address, not a chunk", chunk.asString());
        Preconditions.checkArgument(serialize(chunk, location).length() <= MAX_LENGTH, "Slot address exceeds %s characters", MAX_LENGTH);
    }

    BlobId asBlobId(BlobId.Factory blobIdFactory) {
        return blobIdFactory.parse(serialize());
    }

    private String serialize() {
        return serialize(chunk, location);
    }

    private static String serialize(BlobId chunk, SlotLocation location) {
        return chunk.asString() + SEPARATOR + location.offset() + SEPARATOR + location.length();
    }
}
