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

import java.util.Objects;
import java.util.Optional;

import org.apache.james.blob.api.BucketName;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class CompactionRequest {
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

    private final BucketName bucketName;
    private final long generation;
    private final Optional<Integer> family;
    private final CompactionConfiguration configuration;

    public CompactionRequest(BucketName bucketName, long generation, Optional<Integer> family, CompactionConfiguration configuration) {
        this.bucketName = Preconditions.checkNotNull(bucketName, "'bucketName' must not be null");
        this.generation = generation;
        this.family = Preconditions.checkNotNull(family, "'family' must not be null");
        this.configuration = Preconditions.checkNotNull(configuration, "'configuration' must not be null");
    }

    public BucketName bucketName() {
        return bucketName;
    }

    public long generation() {
        return generation;
    }

    public Optional<Integer> family() {
        return family;
    }

    public CompactionConfiguration configuration() {
        return configuration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof CompactionRequest that) {
            return generation == that.generation
                && Objects.equals(bucketName, that.bucketName)
                && Objects.equals(family, that.family)
                && Objects.equals(configuration, that.configuration);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bucketName, generation, family, configuration);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("bucketName", bucketName)
            .add("generation", generation)
            .add("family", family)
            .add("configuration", configuration)
            .toString();
    }
}
