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

package org.apache.james.mailbox.events;

import java.time.Duration;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

class RetryBackoff {

    @FunctionalInterface
    interface RequireMaxRetries {
        RequireFirstBackoff maxRetries(int maxRetries);
    }

    @FunctionalInterface
    interface RequireFirstBackoff {
        RequireJitterFactor firstBackoff(Duration firstBackoff);
    }

    @FunctionalInterface
    interface RequireJitterFactor {
        ReadyToBuild jitterFactor(double jitterFactor);
    }

    static class ReadyToBuild {
        private final int maxRetries;
        private final Duration firstBackoff;
        private final double jitterFactor;

        private ReadyToBuild(int maxRetries, Duration firstBackoff, double jitterFactor) {
            this.maxRetries = maxRetries;
            this.firstBackoff = firstBackoff;
            this.jitterFactor = jitterFactor;
        }

        RetryBackoff build() {
            return new RetryBackoff(maxRetries, firstBackoff, jitterFactor);
        }
    }

    static RequireMaxRetries builder() {
        return maxRetries -> firstBackoff -> jitterFactor -> new ReadyToBuild(maxRetries, firstBackoff, jitterFactor);
    }

    static RetryBackoff defaultRetryBackoff() {
        return builder()
            .maxRetries(DEFAULT_MAX_RETRIES)
            .firstBackoff(DEFAULT_FIRST_BACKOFF)
            .jitterFactor(DEFAULT_JITTER_FACTOR)
            .build();
    }

    private static final double DEFAULT_JITTER_FACTOR = 0.5;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final Duration DEFAULT_FIRST_BACKOFF = Duration.ofMillis(100);

    private final int maxRetries;
    private final Duration firstBackoff;
    private final double jitterFactor;

    RetryBackoff(int maxRetries, Duration firstBackoff, double jitterFactor) {
        Preconditions.checkArgument(!firstBackoff.isNegative() && !firstBackoff.isZero(), "firstBackoff has to be strictly positive");
        Preconditions.checkArgument(maxRetries > 0, "maxRetries has to be strictly positive");
        Preconditions.checkArgument(jitterFactor > 0, "jitterFactor has to be strictly positive");

        this.maxRetries = maxRetries;
        this.firstBackoff = firstBackoff;
        this.jitterFactor = jitterFactor;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public Duration getFirstBackoff() {
        return firstBackoff;
    }

    public double getJitterFactor() {
        return jitterFactor;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("maxRetries", maxRetries)
            .add("firstBackoff", firstBackoff)
            .add("jitterFactor", jitterFactor)
            .toString();
    }
}
