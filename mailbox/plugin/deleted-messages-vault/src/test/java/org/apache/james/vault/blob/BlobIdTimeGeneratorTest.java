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

package org.apache.james.vault.blob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.server.blob.deduplication.MinIOGenerationAwareBlobId;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class BlobIdTimeGeneratorTest {
    private static final Instant NOW = Instant.parse("2007-07-03T10:15:30.00Z");
    private static final Clock CLOCK = new UpdatableTickingClock(NOW);

    interface BlobIdTimeGeneratorContract {
        BlobId.Factory blobIdFactory();

        BlobIdTimeGenerator blobIdTimeGenerator();

        @Test
        default void currentBlobIdShouldReturnBlobIdFormattedWithYearAndMonthPrefix() {
            String currentBlobId = blobIdTimeGenerator().currentBlobId().asString();

            int firstSlash = currentBlobId.indexOf('/');
            int secondSlash = currentBlobId.indexOf('/', firstSlash + 1);
            String prefix = currentBlobId.substring(0, secondSlash);

            assertThat(prefix).isEqualTo("2007/07");
        }

        @Test
        default void shouldReturnProperDeletedMessageBlobIdFromString() {
            BlobId currentBlobId = blobIdTimeGenerator().currentBlobId();
            BlobId deletedMessageBlobId = blobIdTimeGenerator().toDeletedMessageBlobId(currentBlobId.asString());

            assertThat(deletedMessageBlobId).isEqualTo(currentBlobId);
        }

        @Test
        default void shouldFallbackToOldDeletedBlobIdFromString() {
            BlobId currentBlobId = blobIdFactory().of(UUID.randomUUID().toString());
            BlobId deletedMessageBlobId = blobIdTimeGenerator().toDeletedMessageBlobId(currentBlobId.asString());

            assertThat(deletedMessageBlobId).isEqualTo(currentBlobId);
        }
    }


    @Nested
    class GenerationAwareBlobIdTimeGeneratorTest implements BlobIdTimeGeneratorContract {
        @Override
        public BlobId.Factory blobIdFactory() {
            return new GenerationAwareBlobId.Factory(CLOCK, new PlainBlobId.Factory(), GenerationAwareBlobId.Configuration.DEFAULT);
        }

        @Override
        public BlobIdTimeGenerator blobIdTimeGenerator() {
            return new BlobIdTimeGenerator(blobIdFactory(), CLOCK);
        }
    }

    @Nested
    class MinIOGenerationAwareBlobIdTimeGeneratorTest implements BlobIdTimeGeneratorContract {
        @Override
        public BlobId.Factory blobIdFactory() {
            return new MinIOGenerationAwareBlobId.Factory(CLOCK, GenerationAwareBlobId.Configuration.DEFAULT, new PlainBlobId.Factory());
        }

        @Override
        public BlobIdTimeGenerator blobIdTimeGenerator() {
            return new BlobIdTimeGenerator(blobIdFactory(), CLOCK);
        }
    }
}
