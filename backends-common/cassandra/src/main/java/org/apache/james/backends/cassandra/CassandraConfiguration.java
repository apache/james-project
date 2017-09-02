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

package org.apache.james.backends.cassandra;

import java.util.Objects;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class CassandraConfiguration {
    public static final CassandraConfiguration DEFAULT_CONFIGURATION = builder().build();

    public static final int DEFAULT_MESSAGE_CHUNK_SIZE_ON_READ = 100;
    public static final int DEFAULT_EXPUNGE_BATCH_SIZE = 100;
    public static final int DEFAULT_UPDATE_FLAGS_BATCH_SIZE = 20;
    public static final int DEFAULT_FLAGS_UPDATE_MESSAGE_MAX_RETRY = 1000;
    public static final int DEFAULT_FLAGS_UPDATE_MESSAGE_ID_MAX_RETRY = 1000;
    public static final int DEFAULT_MODSEQ_MAX_RETRY = 100000;
    public static final int DEFAULT_UID_MAX_RETRY = 100000;
    public static final int DEFAULT_ACL_MAX_RETRY = 1000;
    public static final int DEFAULT_FETCH_NEXT_PAGE_ADVANCE_IN_ROW = 100;
    public static final int DEFAULT_BLOB_PART_SIZE = 100 * 1024;

    public static class Builder {
        private Optional<Integer> messageReadChunkSize = Optional.empty();
        private Optional<Integer> expungeChunkSize = Optional.empty();
        private Optional<Integer> flagsUpdateChunkSize = Optional.empty();
        private Optional<Integer> flagsUpdateMessageIdMaxRetry = Optional.empty();
        private Optional<Integer> flagsUpdateMessageMaxRetry = Optional.empty();
        private Optional<Integer> modSeqMaxRetry = Optional.empty();
        private Optional<Integer> uidMaxRetry = Optional.empty();
        private Optional<Integer> aclMaxRetry = Optional.empty();
        private Optional<Integer> fetchNextPageInAdvanceRow = Optional.empty();
        private Optional<Integer> blobPartSize = Optional.empty();

        public Builder messageReadChunkSize(int value) {
            Preconditions.checkArgument(value > 0, "messageReadChunkSize needs to be strictly positive");
            this.messageReadChunkSize = Optional.of(value);
            return this;
        }

        public Builder expungeChunkSize(int value) {
            Preconditions.checkArgument(value > 0, "expungeChunkSize needs to be strictly positive");
            this.expungeChunkSize = Optional.of(value);
            return this;
        }

        public Builder flagsUpdateChunkSize(int value) {
            Preconditions.checkArgument(value > 0, "flagsUpdateChunkSize needs to be strictly positive");
            this.flagsUpdateChunkSize = Optional.of(value);
            return this;
        }

        public Builder flagsUpdateMessageIdMaxRetry(int value) {
            Preconditions.checkArgument(value > 0, "flagsUpdateMessageIdMaxRetry needs to be strictly positive");
            this.flagsUpdateMessageIdMaxRetry = Optional.of(value);
            return this;
        }

        public Builder flagsUpdateMessageMaxRetry(int value) {
            Preconditions.checkArgument(value > 0, "flagsUpdateMessageMaxRetry needs to be strictly positive");
            this.flagsUpdateMessageMaxRetry = Optional.of(value);
            return this;
        }

        public Builder modSeqMaxRetry(int value) {
            Preconditions.checkArgument(value > 0, "modSeqMaxRetry needs to be strictly positive");
            this.modSeqMaxRetry = Optional.of(value);
            return this;
        }

        public Builder uidMaxRetry(int value) {
            Preconditions.checkArgument(value > 0, "uidMaxRetry needs to be strictly positive");
            this.uidMaxRetry = Optional.of(value);
            return this;
        }

        public Builder aclMaxRetry(int value) {
            Preconditions.checkArgument(value > 0, "aclMaxRetry needs to be strictly positive");
            this.aclMaxRetry = Optional.of(value);
            return this;
        }

        public Builder fetchNextPageInAdvanceRow(int value) {
            Preconditions.checkArgument(value > 0, "fetchNextPageInAdvanceRow needs to be strictly positive");
            this.fetchNextPageInAdvanceRow = Optional.of(value);
            return this;
        }

        public Builder blobPartSize(int value) {
            Preconditions.checkArgument(value > 0, "blobPartSize needs to be strictly positive");
            this.blobPartSize = Optional.of(value);
            return this;
        }

        public Builder messageReadChunkSize(Optional<Integer> value) {
            value.ifPresent(this::messageReadChunkSize);
            return this;
        }

        public Builder expungeChunkSize(Optional<Integer> value) {
            value.ifPresent(this::expungeChunkSize);
            return this;
        }

        public Builder flagsUpdateChunkSize(Optional<Integer> value) {
            value.ifPresent(this::flagsUpdateChunkSize);
            return this;
        }

        public Builder flagsUpdateMessageIdMaxRetry(Optional<Integer> value) {
            value.ifPresent(this::flagsUpdateMessageIdMaxRetry);
            return this;
        }

        public Builder flagsUpdateMessageMaxRetry(Optional<Integer> value) {
            value.ifPresent(this::flagsUpdateMessageMaxRetry);
            return this;
        }

        public Builder modSeqMaxRetry(Optional<Integer> value) {
            value.ifPresent(this::modSeqMaxRetry);
            return this;
        }

        public Builder uidMaxRetry(Optional<Integer> value) {
            value.ifPresent(this::uidMaxRetry);
            return this;
        }

        public Builder aclMaxRetry(Optional<Integer> value) {
            value.ifPresent(this::aclMaxRetry);
            return this;
        }

        public Builder fetchNextPageInAdvanceRow(Optional<Integer> value) {
            value.ifPresent(this::fetchNextPageInAdvanceRow);
            return this;
        }

        public Builder blobPartSize(Optional<Integer> value) {
            value.ifPresent(this::blobPartSize);
            return this;
        }

        public CassandraConfiguration build() {
            return new CassandraConfiguration(aclMaxRetry.orElse(DEFAULT_ACL_MAX_RETRY),
                messageReadChunkSize.orElse(DEFAULT_MESSAGE_CHUNK_SIZE_ON_READ),
                expungeChunkSize.orElse(DEFAULT_EXPUNGE_BATCH_SIZE),
                flagsUpdateChunkSize.orElse(DEFAULT_UPDATE_FLAGS_BATCH_SIZE),
                flagsUpdateMessageIdMaxRetry.orElse(DEFAULT_FLAGS_UPDATE_MESSAGE_ID_MAX_RETRY),
                flagsUpdateMessageMaxRetry.orElse(DEFAULT_FLAGS_UPDATE_MESSAGE_MAX_RETRY),
                modSeqMaxRetry.orElse(DEFAULT_MODSEQ_MAX_RETRY),
                uidMaxRetry.orElse(DEFAULT_UID_MAX_RETRY),
                fetchNextPageInAdvanceRow.orElse(DEFAULT_FETCH_NEXT_PAGE_ADVANCE_IN_ROW),
                blobPartSize.orElse(DEFAULT_BLOB_PART_SIZE));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final int messageReadChunkSize;
    private final int expungeChunkSize;
    private final int flagsUpdateChunkSize;
    private final int flagsUpdateMessageIdMaxRetry;
    private final int flagsUpdateMessageMaxRetry;
    private final int modSeqMaxRetry;
    private final int uidMaxRetry;
    private final int aclMaxRetry;
    private final int fetchNextPageInAdvanceRow;
    private final int blobPartSize;

    @VisibleForTesting
    CassandraConfiguration(int aclMaxRetry, int messageReadChunkSize, int expungeChunkSize,
                           int flagsUpdateChunkSize, int flagsUpdateMessageIdMaxRetry, int flagsUpdateMessageMaxRetry,
                           int modSeqMaxRetry, int uidMaxRetry, int fetchNextPageInAdvanceRow,
                           int blobPartSize) {
        this.aclMaxRetry = aclMaxRetry;
        this.messageReadChunkSize = messageReadChunkSize;
        this.expungeChunkSize = expungeChunkSize;
        this.flagsUpdateMessageIdMaxRetry = flagsUpdateMessageIdMaxRetry;
        this.flagsUpdateMessageMaxRetry = flagsUpdateMessageMaxRetry;
        this.modSeqMaxRetry = modSeqMaxRetry;
        this.uidMaxRetry = uidMaxRetry;
        this.fetchNextPageInAdvanceRow = fetchNextPageInAdvanceRow;
        this.flagsUpdateChunkSize = flagsUpdateChunkSize;
        this.blobPartSize = blobPartSize;
    }

    public int getBlobPartSize() {
        return blobPartSize;
    }

    public int getFlagsUpdateChunkSize() {
        return flagsUpdateChunkSize;
    }

    public int getAclMaxRetry() {
        return aclMaxRetry;
    }

    public int getMessageReadChunkSize() {
        return messageReadChunkSize;
    }

    public int getExpungeChunkSize() {
        return expungeChunkSize;
    }

    public int getFlagsUpdateMessageIdMaxRetry() {
        return flagsUpdateMessageIdMaxRetry;
    }

    public int getFlagsUpdateMessageMaxRetry() {
        return flagsUpdateMessageMaxRetry;
    }

    public int getModSeqMaxRetry() {
        return modSeqMaxRetry;
    }

    public int getUidMaxRetry() {
        return uidMaxRetry;
    }

    public int getFetchNextPageInAdvanceRow() {
        return fetchNextPageInAdvanceRow;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof CassandraConfiguration) {
            CassandraConfiguration that = (CassandraConfiguration) o;

            return Objects.equals(this.aclMaxRetry, that.aclMaxRetry)
                && Objects.equals(this.messageReadChunkSize, that.messageReadChunkSize)
                && Objects.equals(this.expungeChunkSize, that.expungeChunkSize)
                && Objects.equals(this.flagsUpdateMessageIdMaxRetry, that.flagsUpdateMessageIdMaxRetry)
                && Objects.equals(this.flagsUpdateMessageMaxRetry, that.flagsUpdateMessageMaxRetry)
                && Objects.equals(this.modSeqMaxRetry, that.modSeqMaxRetry)
                && Objects.equals(this.uidMaxRetry, that.uidMaxRetry)
                && Objects.equals(this.flagsUpdateChunkSize, that.flagsUpdateChunkSize)
                && Objects.equals(this.fetchNextPageInAdvanceRow, that.fetchNextPageInAdvanceRow)
                && Objects.equals(this.blobPartSize, that.blobPartSize);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(aclMaxRetry, messageReadChunkSize, expungeChunkSize, flagsUpdateMessageIdMaxRetry,
            flagsUpdateMessageMaxRetry, modSeqMaxRetry, uidMaxRetry, fetchNextPageInAdvanceRow, flagsUpdateChunkSize,
            blobPartSize);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("aclMaxRetry", aclMaxRetry)
            .add("messageReadChunkSize", messageReadChunkSize)
            .add("expungeChunkSize", expungeChunkSize)
            .add("flagsUpdateMessageIdMaxRetry", flagsUpdateMessageIdMaxRetry)
            .add("flagsUpdateMessageMaxRetry", flagsUpdateMessageMaxRetry)
            .add("modSeqMaxRetry", modSeqMaxRetry)
            .add("fetchNextPageInAdvanceRow", fetchNextPageInAdvanceRow)
            .add("flagsUpdateChunkSize", flagsUpdateChunkSize)
            .add("uidMaxRetry", uidMaxRetry)
            .add("blobPartSize", blobPartSize)
            .toString();
    }
}
