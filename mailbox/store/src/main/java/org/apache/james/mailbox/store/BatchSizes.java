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
package org.apache.james.mailbox.store;

import java.util.Optional;

import org.apache.james.mailbox.store.mail.MessageMapper;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class BatchSizes {
   
    public static final int DEFAULT_BATCH_SIZE = 200;

    public static BatchSizes defaultValues() {
        return new Builder().build();
    }

    public static BatchSizes uniqueBatchSize(int batchSize) {
        return new Builder()
                .fetchMetadata(batchSize)
                .fetchHeaders(batchSize)
                .fetchFull(batchSize)
                .copyBatchSize(batchSize)
                .moveBatchSize(batchSize)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Optional<Integer> fetchMetadata;
        private Optional<Integer> fetchHeaders;
        private Optional<Integer> fetchFull;
        private Optional<Integer> copyBatchSize;
        private Optional<Integer> moveBatchSize;

        private Builder() {
            fetchMetadata = Optional.empty();
            fetchHeaders = Optional.empty();
            fetchFull = Optional.empty();
            copyBatchSize = Optional.empty();
            moveBatchSize = Optional.empty();
        }

        public Builder fetchMetadata(int batchSize) {
            Preconditions.checkArgument(batchSize > 0, "'fetchMetadata' must be greater than zero");
            this.fetchMetadata = Optional.of(batchSize);
            return this;
        }

        public Builder fetchHeaders(int batchSize) {
            Preconditions.checkArgument(batchSize > 0, "'fetchHeaders' must be greater than zero");
            this.fetchHeaders = Optional.of(batchSize);
            return this;
        }

        public Builder fetchFull(int batchSize) {
            Preconditions.checkArgument(batchSize > 0, "'fetchFull' must be greater than zero");
            this.fetchFull = Optional.of(batchSize);
            return this;
        }

        public Builder copyBatchSize(int batchSize) {
            Preconditions.checkArgument(batchSize > 0, "'copyBatchSize' must be greater than zero");
            this.copyBatchSize = Optional.of(batchSize);
            return this;
        }

        public Builder moveBatchSize(int batchSize) {
            Preconditions.checkArgument(batchSize > 0, "'moveBatchSize' must be greater than zero");
            this.moveBatchSize = Optional.of(batchSize);
            return this;
        }

        public BatchSizes build() {
            return new BatchSizes(
                    fetchMetadata.orElse(DEFAULT_BATCH_SIZE),
                    fetchHeaders.orElse(DEFAULT_BATCH_SIZE),
                    fetchFull.orElse(DEFAULT_BATCH_SIZE),
                    copyBatchSize,
                    moveBatchSize);
        }
    }

    private final int fetchMetadata;
    private final int fetchHeaders;
    private final int fetchFull;
    private final Optional<Integer> copyBatchSize;
    private final Optional<Integer> moveBatchSize;

    private BatchSizes(int fetchMetadata, int fetchHeaders, int fetchFull, Optional<Integer> copyBatchSize, Optional<Integer> moveBatchSize) {
        this.fetchMetadata = fetchMetadata;
        this.fetchHeaders = fetchHeaders;
        this.fetchFull = fetchFull;
        this.copyBatchSize = copyBatchSize;
        this.moveBatchSize = moveBatchSize;
    }

    public int getFetchMetadata() {
        return fetchMetadata;
    }

    public int getFetchHeaders() {
        return fetchHeaders;
    }

    public int getFetchFull() {
        return fetchFull;
    }

    public int forFetchType(MessageMapper.FetchType fetchType) {
        switch (fetchType) {
            case METADATA:
                return fetchMetadata;
            case ATTACHMENTS_METADATA:
            case HEADERS:
                return fetchHeaders;
            case FULL:
                return fetchFull;
        }
        throw new RuntimeException("Unknown fetchType: " + fetchType);
    }

    public Optional<Integer> getCopyBatchSize() {
        return copyBatchSize;
    }

    public Optional<Integer> getMoveBatchSize() {
        return moveBatchSize;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof BatchSizes) {
            BatchSizes other = (BatchSizes) obj;
            return Objects.equal(this.fetchMetadata, other.fetchMetadata)
                && Objects.equal(this.fetchHeaders, other.fetchHeaders)
                && Objects.equal(this.fetchFull, other.fetchFull)
                && Objects.equal(this.copyBatchSize, other.copyBatchSize)
                && Objects.equal(this.moveBatchSize, other.moveBatchSize);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(this.fetchMetadata, this.fetchHeaders, this.fetchFull, this.copyBatchSize, this.moveBatchSize);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(BatchSizes.class)
                .add("fetchMetadata", fetchMetadata)
                .add("fetchHeaders", fetchHeaders)
                .add("fetchFull", fetchFull)
                .add("copyBatchSize", copyBatchSize)
                .add("moveBatchSize", moveBatchSize)
                .toString();
    }
}
