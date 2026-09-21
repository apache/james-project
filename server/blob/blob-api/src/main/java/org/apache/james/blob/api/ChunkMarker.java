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

import java.util.regex.Pattern;

/**
 * Utility for detecting chunk-based BlobId identifiers across Apache James modules
 * without introducing circular module dependencies.
 */
public final class ChunkMarker {
    public static final String CHUNK_MARKER = "_chunk";
    private static final Pattern CHUNK_PATTERN = Pattern.compile("^\\d+_\\d+_chunk[A-Za-z0-9_-]{16,}(~\\d+~\\d+)?$");

    private ChunkMarker() {
    }

    public static boolean looksLikeChunkId(String id) {
        if (id == null) {
            return false;
        }
        return CHUNK_PATTERN.matcher(id).matches();
    }

    public static boolean looksLikeChunkId(BlobId blobId) {
        if (blobId == null) {
            return false;
        }
        return looksLikeChunkId(blobId.asString());
    }
}
