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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

/** Parsing never fails and, when it succeeds, serializing gives back the exact input. */
class SlotAddressFuzzTest {
    private static final PlainBlobId.Factory PLAIN = new PlainBlobId.Factory();
    private static final GenerationAwareBlobId.Factory GENERATION_AWARE = new GenerationAwareBlobId.Factory(
        Clock.systemUTC(), PLAIN, GenerationAwareBlobId.Configuration.DEFAULT);

    @FuzzTest(maxDuration = "60s")
    void parseShouldBeSafeAndReversible(FuzzedDataProvider data) {
        String input = data.consumeRemainingAsString();
        if (input.isEmpty()) {
            return;
        }
        BlobId blobId = PLAIN.of(input);

        SlotAddress.parse(blobId, GENERATION_AWARE)
            .ifPresent(address -> assertThat(address.asBlobId(GENERATION_AWARE).asString()).isEqualTo(input));
    }
}
