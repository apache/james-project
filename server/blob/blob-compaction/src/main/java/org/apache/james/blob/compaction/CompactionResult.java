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

public record CompactionResult(long packedBlobs,
                               long packedBytes,
                               long chunksWritten,
                               long deadPurged,
                               long mergedChunks,
                               long freedBytes) {

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
}
