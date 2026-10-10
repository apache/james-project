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
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.blob.api;

import java.util.regex.Pattern;

/**
 * Identifies physical chunk object identifiers and their virtual slot references.
 *
 * <p>The compacted-object format is {@code <family>_<generation>_chunk<random-part>} for the physical object and
 * {@code <physical-object>~<offset>~<length>} for a virtual slot. This shared marker deliberately contains no
 * chunk-format parsing so storage strategies can recognize chunk IDs without depending on the compaction module.</p>
 */
public final class ChunkMarker {
    private static final Pattern CHUNK_ID_PATTERN = Pattern.compile(
        "^[1-9]\\d*_\\d+_chunk[A-Za-z0-9_-]{16,}(?:~\\d+~\\d+)?$");

    private ChunkMarker() {
    }

    public static boolean isChunkId(BlobId blobId) {
        return blobId != null && isChunkId(blobId.asString());
    }

    public static boolean isChunkId(String blobId) {
        return blobId != null && CHUNK_ID_PATTERN.matcher(blobId).matches();
    }
}
