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
import java.util.Objects;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class RetryBackoffConfiguration {

    @FunctionalInterface
    public interface RequireMaxRetries {
        RequireFirstBackoff maxRetries(int maxRetries);
    }

    @FunctionalInterface
    public interface RequireFirstBackoff {
        RequireJitterFactor firstBackoff(Duration firstBackoff);
    }

    @FunctionalInterface
    public interface RequireJitterFactor {
        ReadyToBuild jitterFactor(double jitterFactor);
    }

    public static class ReadyToBuild {
        private final int maxRetries;
        private final Duration firstBackoff;
        private final double jitterFactor;

        private ReadyToBuild(int maxRetries, Duration firstBackoff, double jitterFactor) {
            this.maxRetries = maxRetries;
            this.firstBackoff = firstBackoff;
            this.jitterFactor = jitterFactor;
        }

        public RetryBackoffConfiguration build() {
            return new RetryBackoffConfiguration(maxRetries, firstBackoff, jitterFactor);
        }
    }

    public static RequireMaxRetries builder() {
        return maxRetries -> firstBackoff -> jitterFactor -> new ReadyToBuild(maxRetries, firstBackoff, jitterFactor);
    }

    static final double DEFAULT_JITTER_FACTOR = 0.5;
    static final int DEFAULT_MAX_RETRIES = 8;
    static final Duration DEFAULT_FIRST_BACKOFF = Duration.ofMillis(100);
    public static final RetryBackoffConfiguration DEFAULT = new RetryBackoffConfiguration(
        DEFAULT_MAX_RETRIES,
        DEFAULT_FIRST_BACKOFF,
        DEFAULT_JITTER_FACTOR);

    private final int maxRetries;
    private final Duration firstBackoff;
    private final double jitterFactor;

    private RetryBackoffConfiguration(int maxRetries, Duration firstBackoff, double jitterFactor) {
        Preconditions.checkArgument(!firstBackoff.isNegative(), "firstBackoff is not allowed to be negative");
        Preconditions.checkArgument(maxRetries >= 0, "maxRetries is not allowed to be negative");
        Preconditions.checkArgument(jitterFactor >= 0 && jitterFactor <= 1.0, "jitterFactor is not " +
            "allowed to be negative or greater than 1");

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
    public final boolean equals(Object o) {
        if (o instanceof RetryBackoffConfiguration) {
            RetryBackoffConfiguration that = (RetryBackoffConfiguration) o;

            return Objects.equals(this.maxRetries, that.maxRetries)
                && Objects.equals(this.jitterFactor, that.jitterFactor)
                && Objects.equals(this.firstBackoff, that.firstBackoff);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(maxRetries, firstBackoff, jitterFactor);
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
