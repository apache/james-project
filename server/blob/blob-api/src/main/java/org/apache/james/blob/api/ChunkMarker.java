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
 * <p>Physical objects use {@code <family>_<generation>_chunk<identifier>} or the MinIO layout
 * {@code <family>/<generation>/chunk<identifier>}. The identifier is unrestricted and can also carry a virtual
 * slot suffix. This only recognizes the chunk marker; parsing and validating a slot reference is the
 * responsibility of the compaction module.</p>
 */
public final class ChunkMarker {
    private static final Pattern CHUNK_ID_PATTERN = Pattern.compile(
        "^[1-9]\\d*(?:_\\d+_chunk|/\\d+/chunk).*$");

    private ChunkMarker() {
    }

    public static boolean isChunkId(BlobId blobId) {
        return blobId != null && isChunkId(blobId.asString());
    }

    private static boolean isChunkId(String blobId) {
        return blobId != null && CHUNK_ID_PATTERN.matcher(blobId).matches();
    }
}
