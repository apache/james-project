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
import com.google.common.base.Preconditions;

public class CompactionConfiguration {
    public static final long DEFAULT_CHUNK_TARGET_SIZE = 100 * 1024 * 1024L; // 100MB
    public static final long DEFAULT_MAX_PACKABLE_SIZE = 1024 * 1024L;       // 1MB
    public static final double DEFAULT_PURGE_DEAD_RATIO = 0.1;               // 10%
    public static final double DEFAULT_MERGE_DEAD_RATIO = 0.5;               // 50%
    public static final double DEFAULT_GAIN_THRESHOLD = 0.1;                 // 10%
    public static final int DEFAULT_WINDOW_BATCH_SIZE = 1000;

    public static final CompactionConfiguration DEFAULT = builder().build();

    public static class Builder {
        private long chunkTargetSize = DEFAULT_CHUNK_TARGET_SIZE;
        private long maxPackableSize = DEFAULT_MAX_PACKABLE_SIZE;
        private double purgeDeadRatio = DEFAULT_PURGE_DEAD_RATIO;
        private double mergeDeadRatio = DEFAULT_MERGE_DEAD_RATIO;
        private double gainThreshold = DEFAULT_GAIN_THRESHOLD;
        private int windowBatchSize = DEFAULT_WINDOW_BATCH_SIZE;

        public Builder chunkTargetSize(long chunkTargetSize) {
            Preconditions.checkArgument(chunkTargetSize > 0, "'chunkTargetSize' must be strictly positive");
            this.chunkTargetSize = chunkTargetSize;
            return this;
        }

        public Builder maxPackableSize(long maxPackableSize) {
            Preconditions.checkArgument(maxPackableSize > 0, "'maxPackableSize' must be strictly positive");
            this.maxPackableSize = maxPackableSize;
            return this;
        }

        public Builder purgeDeadRatio(double purgeDeadRatio) {
            Preconditions.checkArgument(purgeDeadRatio >= 0.0 && purgeDeadRatio <= 1.0,
                "'purgeDeadRatio' must be between 0.0 and 1.0");
            this.purgeDeadRatio = purgeDeadRatio;
            return this;
        }

        public Builder mergeDeadRatio(double mergeDeadRatio) {
            Preconditions.checkArgument(mergeDeadRatio >= 0.0 && mergeDeadRatio <= 1.0,
                "'mergeDeadRatio' must be between 0.0 and 1.0");
            this.mergeDeadRatio = mergeDeadRatio;
            return this;
        }

        public Builder gainThreshold(double gainThreshold) {
            Preconditions.checkArgument(gainThreshold >= 0.0 && gainThreshold <= 1.0,
                "'gainThreshold' must be between 0.0 and 1.0");
            this.gainThreshold = gainThreshold;
            return this;
        }

        public Builder windowBatchSize(int windowBatchSize) {
            Preconditions.checkArgument(windowBatchSize > 0, "'windowBatchSize' must be strictly positive");
            this.windowBatchSize = windowBatchSize;
            return this;
        }

        public CompactionConfiguration build() {
            return new CompactionConfiguration(chunkTargetSize, maxPackableSize, purgeDeadRatio, mergeDeadRatio, gainThreshold, windowBatchSize);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final long chunkTargetSize;
    private final long maxPackableSize;
    private final double purgeDeadRatio;
    private final double mergeDeadRatio;
    private final double gainThreshold;
    private final int windowBatchSize;

    public CompactionConfiguration(long chunkTargetSize, long maxPackableSize, double purgeDeadRatio, double mergeDeadRatio, double gainThreshold) {
        this(chunkTargetSize, maxPackableSize, purgeDeadRatio, mergeDeadRatio, gainThreshold, DEFAULT_WINDOW_BATCH_SIZE);
    }

    public CompactionConfiguration(long chunkTargetSize, long maxPackableSize, double purgeDeadRatio, double mergeDeadRatio, double gainThreshold, int windowBatchSize) {
        this.chunkTargetSize = chunkTargetSize;
        this.maxPackableSize = maxPackableSize;
        this.purgeDeadRatio = purgeDeadRatio;
        this.mergeDeadRatio = mergeDeadRatio;
        this.gainThreshold = gainThreshold;
        this.windowBatchSize = windowBatchSize;
    }

    public long chunkTargetSize() {
        return chunkTargetSize;
    }

    public long maxPackableSize() {
        return maxPackableSize;
    }

    public double purgeDeadRatio() {
        return purgeDeadRatio;
    }

    public double mergeDeadRatio() {
        return mergeDeadRatio;
    }

    public double gainThreshold() {
        return gainThreshold;
    }

    public int windowBatchSize() {
        return windowBatchSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof CompactionConfiguration that) {
            return chunkTargetSize == that.chunkTargetSize
                && maxPackableSize == that.maxPackableSize
                && Double.compare(that.purgeDeadRatio, purgeDeadRatio) == 0
                && Double.compare(that.mergeDeadRatio, mergeDeadRatio) == 0
                && Double.compare(that.gainThreshold, gainThreshold) == 0
                && windowBatchSize == that.windowBatchSize;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunkTargetSize, maxPackableSize, purgeDeadRatio, mergeDeadRatio, gainThreshold, windowBatchSize);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("chunkTargetSize", chunkTargetSize)
            .add("maxPackableSize", maxPackableSize)
            .add("purgeDeadRatio", purgeDeadRatio)
            .add("mergeDeadRatio", mergeDeadRatio)
            .add("gainThreshold", gainThreshold)
            .add("windowBatchSize", windowBatchSize)
            .toString();
    }
}
