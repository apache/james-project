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

import org.apache.james.blob.api.BucketName;
import org.junit.jupiter.api.Test;

class BucketNameGeneratorTest {
    private static final Instant NOW = Instant.parse("2007-12-03T10:15:30.00Z");
    private static final Instant DATE_2 = Instant.parse("2007-07-03T10:15:30.00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("UTC"));
    private static final Clock CLOCK_2 = Clock.fixed(DATE_2, ZoneId.of("UTC"));

    @Test
    void currentBucketShouldReturnBucketFormattedOnFirstDayOfMonth() {
        assertThat(new BucketNameGenerator(CLOCK).currentBucket())
            .isEqualTo(BucketName.of("deletedMessages-2007-12-01"));
    }

    @Test
    void monthShouldBeFormattedWithTwoDigits() {
        assertThat(new BucketNameGenerator(CLOCK_2).currentBucket())
            .isEqualTo(BucketName.of("deletedMessages-2007-07-01"));
    }
}