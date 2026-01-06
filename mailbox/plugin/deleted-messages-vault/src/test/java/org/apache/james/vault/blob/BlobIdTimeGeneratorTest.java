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
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class BlobIdTimeGeneratorTest {
    private static final Instant NOW = Instant.parse("2007-12-03T10:15:30.00Z");
    private static final Instant DATE_2 = Instant.parse("2007-07-03T10:15:30.00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("UTC"));
    private static final BlobId.Factory BLOB_ID_FACTORY = new PlainBlobId.Factory();
    private static final BlobIdTimeGenerator DEFAULT_GENERATOR = new BlobIdTimeGenerator(BLOB_ID_FACTORY, CLOCK);
    private static final Clock CLOCK_2 = Clock.fixed(DATE_2, ZoneId.of("UTC"));

    @Test
    void currentBlobIdShouldReturnBlobIdFormattedWithYearAndMonthPrefix() {
        String currentBlobId = DEFAULT_GENERATOR.currentBlobId().asString();
        String prefix = currentBlobId.substring(0, currentBlobId.lastIndexOf('/'));

        assertThat(prefix).isEqualTo("2007/12");
    }

    @Test
    void monthShouldBeFormattedWithTwoDigits() {
        String currentBlobId = new BlobIdTimeGenerator(BLOB_ID_FACTORY, CLOCK_2).currentBlobId().asString();
        String prefix = currentBlobId.substring(0, currentBlobId.lastIndexOf('/'));

        assertThat(prefix).isEqualTo("2007/07");
    }

    @Nested
    class IsBucketRangeBefore {
        @ParameterizedTest
        @ValueSource(strings = {
                "2018-07-cddf9c7c-12f1-4ce7-9993-8606b9fb8816",
                "2018-07/cddf9c7c-12f1-4ce7-9993-8606b9fb8816",
                "2018/07-cddf9c7c-12f1-4ce7-9993-8606b9fb8816",
                "cddf9c7c-12f1-4ce7-9993-8606b9fb8816",
                "18/07/cddf9c7c-12f1-4ce7-9993-8606b9fb8816",
                "2018/7/cddf9c7c-12f1-4ce7-9993-8606b9fb8816",
                "07/2018/cddf9c7c-12f1-4ce7-9993-8606b9fb8816",
        })
        void shouldBeEmptyWhenPassingNonWellFormattedBlobId(String blobIdAsString) {
            BlobId blobId = BLOB_ID_FACTORY.of(blobIdAsString);

            assertThat(DEFAULT_GENERATOR.blobIdEndTime(blobId)).isEmpty();
        }

        @Test
        void shouldReturnNextMonthAsEndTime() {
            BlobId blobId = BLOB_ID_FACTORY.of("2018/07/cddf9c7c-12f1-4ce7-9993-8606b9fb8816");

            assertThat(DEFAULT_GENERATOR.blobIdEndTime(blobId))
                .contains(ZonedDateTime.parse("2018-08-01T00:00:00.000000000Z[UTC]"));
        }
    }
}
