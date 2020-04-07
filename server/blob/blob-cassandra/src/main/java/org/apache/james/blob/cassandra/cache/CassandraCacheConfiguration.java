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

package org.apache.james.blob.cassandra.cache;

import java.time.Duration;
import java.util.Optional;

import com.google.common.base.Preconditions;

public class CassandraCacheConfiguration {

    public static class Builder {
        private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofMillis(100);
        private static final Duration MAX_READ_TIMEOUT = Duration.ofHours(1);
        private static final Duration DEFAULT_TTL = Duration.ofDays(7);
        private static final int DEFAULT_BYTE_THRESHOLD_SIZE = 8 * 1024;

        private Optional<Duration> readTimeout = Optional.empty();
        private Optional<Integer> sizeThresholdInBytes = Optional.empty();
        private Optional<Duration> ttl = Optional.empty();

        public Builder timeOut(Duration timeout) {
            Preconditions.checkNotNull(timeout, "'Read timeout' must not to be null");
            Preconditions.checkArgument(timeout.getSeconds() > 0, "'Read timeout' needs to be positive");
            Preconditions.checkArgument(timeout.getSeconds() <= MAX_READ_TIMEOUT.getSeconds(),
                "'Read timeout' needs to be less than %s sec", MAX_READ_TIMEOUT.getSeconds());

            this.readTimeout = Optional.of(timeout);
            return this;
        }

        public Builder sizeThresholdInBytes(int sizeThresholdInBytes) {
            Preconditions.checkArgument(sizeThresholdInBytes >= 0, "'Threshold size' needs to be positive");

            this.sizeThresholdInBytes = Optional.of(sizeThresholdInBytes);
            return this;
        }

        public Builder ttl(Duration ttl) {
            Preconditions.checkNotNull(ttl, "'TTL' must not to be null");
            Preconditions.checkArgument(ttl.getSeconds() > 0, "'TTL' needs to be positive");
            Preconditions.checkArgument(ttl.getSeconds() < Integer.MAX_VALUE,
                "'TTL' must not greater than %s sec", Integer.MAX_VALUE);

            this.ttl = Optional.of(ttl);
            return this;
        }

        public CassandraCacheConfiguration build() {
            return new CassandraCacheConfiguration(
                readTimeout.orElse(DEFAULT_READ_TIMEOUT),
                sizeThresholdInBytes.orElse(DEFAULT_BYTE_THRESHOLD_SIZE),
                ttl.orElse(DEFAULT_TTL));
        }
    }

    private final Duration readTimeOut;
    private final int sizeThresholdInBytes;
    private final Duration ttl;

    private CassandraCacheConfiguration(Duration timeout, int sizeThresholdInBytes, Duration ttl) {
        this.readTimeOut = timeout;
        this.sizeThresholdInBytes = sizeThresholdInBytes;
        this.ttl = ttl;
    }

    public Duration getReadTimeOut() {
        return readTimeOut;
    }

    public Duration getTtl() {
        return ttl;
    }

    public int getSizeThresholdInBytes() {
        return sizeThresholdInBytes;
    }
}
