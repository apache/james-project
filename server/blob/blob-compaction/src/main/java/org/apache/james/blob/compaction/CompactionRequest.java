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

import java.util.Optional;

import org.apache.james.blob.api.BucketName;

import com.google.common.base.Preconditions;

public record CompactionRequest(BucketName bucketName,
                                long generation,
                                Optional<Integer> family,
                                CompactionConfiguration configuration) {

    public CompactionRequest {
        Preconditions.checkNotNull(bucketName, "'bucketName' must not be null");
        Preconditions.checkArgument(generation >= 0, "'generation' must not be negative");
        Preconditions.checkNotNull(family, "'family' must not be null");
        Preconditions.checkNotNull(configuration, "'configuration' must not be null");
    }

    public static class Builder {
        private BucketName bucketName = BucketName.DEFAULT;
        private Long generation;
        private Optional<Integer> family = Optional.empty();
        private CompactionConfiguration configuration = CompactionConfiguration.DEFAULT;

        public Builder bucketName(BucketName bucketName) {
            this.bucketName = Preconditions.checkNotNull(bucketName, "'bucketName' must not be null");
            return this;
        }

        public Builder generation(long generation) {
            Preconditions.checkArgument(generation >= 0, "'generation' must not be negative");
            this.generation = generation;
            return this;
        }

        public Builder family(int family) {
            Preconditions.checkArgument(family > 0, "'family' must be strictly positive");
            this.family = Optional.of(family);
            return this;
        }

        public Builder family(Optional<Integer> family) {
            this.family = Preconditions.checkNotNull(family, "'family' must not be null");
            return this;
        }

        public Builder configuration(CompactionConfiguration configuration) {
            this.configuration = Preconditions.checkNotNull(configuration, "'configuration' must not be null");
            return this;
        }

        public CompactionRequest build() {
            Preconditions.checkState(generation != null, "'generation' is required");
            return new CompactionRequest(bucketName, generation, family, configuration);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
