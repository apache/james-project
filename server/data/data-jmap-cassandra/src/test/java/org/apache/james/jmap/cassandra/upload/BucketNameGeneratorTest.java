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

package org.apache.james.jmap.cassandra.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

class BucketNameGeneratorTest {
    ZonedDateTime NOW = ZonedDateTime.parse("2015-10-30T14:12:00Z");

    @Test
    void currentShouldReturnCorrectValue() {
        BucketNameGenerator generator = new BucketNameGenerator(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));

        assertThat(generator.current()).isEqualTo(new UploadBucketName(2391));
    }

    @Test
    void evictionPredicateShouldKeepPresentBucket() {
        BucketNameGenerator generator = new BucketNameGenerator(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));

        assertThat(generator.evictionPredicate().test(new UploadBucketName(2391))).isFalse();
    }

    @Test
    void evictionPredicateShouldKeepFutureBuckets() {
        BucketNameGenerator generator = new BucketNameGenerator(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));

        assertThat(generator.evictionPredicate().test(new UploadBucketName(2392))).isFalse();
    }

    @Test
    void evictionPredicateShouldKeepRecentBuckets() {
        BucketNameGenerator generator = new BucketNameGenerator(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));

        assertThat(generator.evictionPredicate().test(new UploadBucketName(2390))).isFalse();
    }

    @Test
    void evictionPredicateShouldMatchOldBuckets() {
        BucketNameGenerator generator = new BucketNameGenerator(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));

        assertThat(generator.evictionPredicate().test(new UploadBucketName(2389))).isTrue();
    }
}