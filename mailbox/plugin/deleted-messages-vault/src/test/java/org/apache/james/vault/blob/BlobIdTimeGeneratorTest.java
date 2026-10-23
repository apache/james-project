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

import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.Test;

class BlobIdTimeGeneratorTest {
    private static final Instant NOW = Instant.parse("2007-07-03T10:15:30.00Z");
    private static final Clock CLOCK = new UpdatableTickingClock(NOW);

    @Test
    void currentBlobIdShouldReturnBlobIdFormattedWithYearAndMonthPrefix() {
        String currentBlobId = BlobIdTimeGenerator.currentBlobId(CLOCK).asString();

        assertThat(currentBlobId).matches("2007/07/././.*");
    }
}
