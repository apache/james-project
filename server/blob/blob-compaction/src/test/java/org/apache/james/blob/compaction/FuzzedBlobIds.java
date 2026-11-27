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

import java.time.Clock;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.server.blob.deduplication.MinIOGenerationAwareBlobId;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;

/** Blob id factories and blob ids drawn for fuzzing the addressing. */
final class FuzzedBlobIds {
    static final PlainBlobId.Factory PLAIN = new PlainBlobId.Factory();
    static final GenerationAwareBlobId.Factory GENERATION_AWARE = new GenerationAwareBlobId.Factory(
        Clock.systemUTC(), PLAIN, GenerationAwareBlobId.Configuration.DEFAULT);
    static final MinIOGenerationAwareBlobId.Factory MINIO = new MinIOGenerationAwareBlobId.Factory(
        Clock.systemUTC(), GenerationAwareBlobId.Configuration.DEFAULT, PLAIN);
    private static final BlobId.Factory[] FACTORIES = {PLAIN, GENERATION_AWARE, MINIO};

    private FuzzedBlobIds() {
    }

    static BlobId.Factory factory(FuzzedDataProvider data) {
        return data.pickValue(FACTORIES);
    }

    static char separator(FuzzedDataProvider data) {
        return data.consumeBoolean() ? '_' : '/';
    }

    /** Digits, sometimes with leading zeros or beyond the integer range. */
    static String digits(FuzzedDataProvider data) {
        StringBuilder digits = new StringBuilder();
        int length = data.consumeInt(1, 25);
        for (int i = 0; i < length; i++) {
            digits.append((char) ('0' + data.consumeInt(0, 9)));
        }
        return digits.toString();
    }

    static SlotLocation location(FuzzedDataProvider data) {
        long offset = data.consumeLong(ChunkFormat.HEADER_SIZE, Long.MAX_VALUE - ChunkFormat.MIN_SLOT_SIZE);
        return new SlotLocation(offset, data.consumeLong(ChunkFormat.MIN_SLOT_SIZE, Long.MAX_VALUE - offset));
    }
}
