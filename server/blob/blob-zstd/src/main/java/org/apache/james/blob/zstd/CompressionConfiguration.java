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

package org.apache.james.blob.zstd;

public record CompressionConfiguration(boolean enabled, long threshold, float minRatio) {
    public static final boolean DISABLED = false;
    public static final long DEFAULT_THRESHOLD = 16 * 1024L;
    public static final float DEFAULT_MIN_RATIO = 1F;
    public static final CompressionConfiguration DEFAULT = builder().build();

    public static class Builder {
        private boolean enabled = DISABLED;
        private long threshold = DEFAULT_THRESHOLD;
        private float minRatio = DEFAULT_MIN_RATIO;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder threshold(long threshold) {
            if (threshold <= 0) {
                throw new IllegalArgumentException("'threshold' needs to be strictly positive");
            }
            this.threshold = threshold;
            return this;
        }

        public Builder minRatio(float minRatio) {
            if (minRatio < 0 || minRatio > 1) {
                throw new IllegalArgumentException("'minRatio' needs to be between 0 and 1");
            }
            this.minRatio = minRatio;
            return this;
        }

        public CompressionConfiguration build() {
            return new CompressionConfiguration(enabled, threshold, minRatio);
        }
    }

    public CompressionConfiguration {
        if (threshold <= 0) {
            throw new IllegalArgumentException("'threshold' needs to be strictly positive");
        }
        if (minRatio < 0 || minRatio > 1) {
            throw new IllegalArgumentException("'minRatio' needs to be between 0 and 1");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CompressionConfiguration disabled() {
        return DEFAULT;
    }
}
