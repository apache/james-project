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

import com.google.common.base.MoreObjects;

public class CompactionResult {
    public static final CompactionResult NONE = builder().build();

    public static class Builder {
        private long packedBlobs;
        private long packedBytes;
        private long chunksWritten;
        private long deadPurged;
        private long mergedChunks;
        private long freedBytes;

        public Builder packedBlobs(long packedBlobs) {
            this.packedBlobs = packedBlobs;
            return this;
        }

        public Builder packedBytes(long packedBytes) {
            this.packedBytes = packedBytes;
            return this;
        }

        public Builder chunksWritten(long chunksWritten) {
            this.chunksWritten = chunksWritten;
            return this;
        }

        public Builder deadPurged(long deadPurged) {
            this.deadPurged = deadPurged;
            return this;
        }

        public Builder mergedChunks(long mergedChunks) {
            this.mergedChunks = mergedChunks;
            return this;
        }

        public Builder freedBytes(long freedBytes) {
            this.freedBytes = freedBytes;
            return this;
        }

        public CompactionResult build() {
            return new CompactionResult(packedBlobs, packedBytes, chunksWritten, deadPurged, mergedChunks, freedBytes);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final long packedBlobs;
    private final long packedBytes;
    private final long chunksWritten;
    private final long deadPurged;
    private final long mergedChunks;
    private final long freedBytes;

    public CompactionResult(long packedBlobs, long packedBytes, long chunksWritten, long deadPurged, long mergedChunks, long freedBytes) {
        this.packedBlobs = packedBlobs;
        this.packedBytes = packedBytes;
        this.chunksWritten = chunksWritten;
        this.deadPurged = deadPurged;
        this.mergedChunks = mergedChunks;
        this.freedBytes = freedBytes;
    }

    public long packedBlobs() {
        return packedBlobs;
    }

    public long packedBytes() {
        return packedBytes;
    }

    public long chunksWritten() {
        return chunksWritten;
    }

    public long deadPurged() {
        return deadPurged;
    }

    public long mergedChunks() {
        return mergedChunks;
    }

    public long freedBytes() {
        return freedBytes;
    }

    public CompactionResult combine(CompactionResult other) {
        if (other == null) {
            return this;
        }
        return new CompactionResult(
            this.packedBlobs + other.packedBlobs,
            this.packedBytes + other.packedBytes,
            this.chunksWritten + other.chunksWritten,
            this.deadPurged + other.deadPurged,
            this.mergedChunks + other.mergedChunks,
            this.freedBytes + other.freedBytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof CompactionResult that) {
            return packedBlobs == that.packedBlobs
                && packedBytes == that.packedBytes
                && chunksWritten == that.chunksWritten
                && deadPurged == that.deadPurged
                && mergedChunks == that.mergedChunks
                && freedBytes == that.freedBytes;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(packedBlobs, packedBytes, chunksWritten, deadPurged, mergedChunks, freedBytes);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("packedBlobs", packedBlobs)
            .add("packedBytes", packedBytes)
            .add("chunksWritten", chunksWritten)
            .add("deadPurged", deadPurged)
            .add("mergedChunks", mergedChunks)
            .add("freedBytes", freedBytes)
            .toString();
    }
}
