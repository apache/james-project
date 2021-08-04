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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.blob.api.BucketName;
import org.junit.jupiter.api.Test;

class UploadBucketNameTest {
    @Test
    void shouldThrowOnNegativeWeekCount() {
        assertThatThrownBy(() -> new UploadBucketName(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroShouldBeValid() {
        assertThat(new UploadBucketName(0).getWeekNumber())
            .isEqualTo(0);
    }

    @Test
    void asBucketNameShouldConvertToABucketName() {
        assertThat(new UploadBucketName(36).asBucketName())
            .isEqualTo(BucketName.of("uploads-36"));
    }

    @Test
    void ofBucketShouldFilterUnrelatedBuckets() {
        assertThat(UploadBucketName.ofBucket(BucketName.of("bad")))
            .isEmpty();
    }

    @Test
    void ofBucketShouldFilterEmptyWeekCount() {
        assertThat(UploadBucketName.ofBucket(BucketName.of("uploads-")))
            .isEmpty();
    }

    @Test
    void ofBucketShouldFilterInvalidWeekCount() {
        assertThat(UploadBucketName.ofBucket(BucketName.of("uploads-invalid")))
            .isEmpty();
    }

    @Test
    void ofBucketShouldFilterNegativeWeekCount() {
        assertThat(UploadBucketName.ofBucket(BucketName.of("uploads--1")))
            .isEmpty();
    }

    @Test
    void ofBucketShouldParseValidValues() {
        assertThat(UploadBucketName.ofBucket(BucketName.of("uploads-36")))
            .contains(new UploadBucketName(36));
    }
}