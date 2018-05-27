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

package org.apache.james.mailbox.tika;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.util.Port;

import com.google.common.base.Preconditions;

public class TikaConfiguration {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Optional<String> host;
        private Optional<Integer> port;
        private Optional<Integer> timeoutInMillis;
        private Optional<Duration> cacheEvictionPeriod;
        private Optional<Long> cacheWeightInBytes;

        private Builder() {
            host = Optional.empty();
            port = Optional.empty();
            timeoutInMillis = Optional.empty();
            cacheEvictionPeriod = Optional.empty();
            cacheWeightInBytes = Optional.empty();
        }

        public Builder host(String host) {
            this.host = Optional.ofNullable(host);
            return this;
        }

        public Builder port(int port) {
            this.port = Optional.of(port);
            return this;
        }

        public Builder timeoutInMillis(int timeoutInMillis) {
            this.timeoutInMillis = Optional.of(timeoutInMillis);
            return this;
        }

        public Builder cacheEvictionPeriod(Duration duration) {
            this.cacheEvictionPeriod = Optional.of(duration);
            return this;
        }

        public Builder cacheEvictionPeriod(Optional<Duration> duration) {
            this.cacheEvictionPeriod = duration;
            return this;
        }

        public Builder cacheWeightInBytes(long weight) {
            this.cacheWeightInBytes = Optional.of(weight);
            return this;
        }

        public Builder cacheWeightInBytes(Optional<Long> weight) {
            this.cacheWeightInBytes = weight;
            return this;
        }

        public TikaConfiguration build() {
            Preconditions.checkState(host.isPresent(), "'host' is mandatory");
            Preconditions.checkState(port.isPresent(), "'port' is mandatory");
            Preconditions.checkState(timeoutInMillis.isPresent(), "'timeoutInMillis' is mandatory");
            Port.assertValid(port.get());

            return new TikaConfiguration(host.get(), port.get(), timeoutInMillis.get(),
                cacheEvictionPeriod.orElse(DEFAULT_CACHE_EVICTION_PERIOD),
                cacheWeightInBytes.orElse(DEFAULT_CACHE_LIMIT_100_MB));
        }
    }
    public static final long DEFAULT_CACHE_LIMIT_100_MB = 1024L * 1024L * 100L;
    public static final Duration DEFAULT_CACHE_EVICTION_PERIOD = Duration.ofDays(1);

    private final String host;
    private final int port;
    private final int timeoutInMillis;
    private final Duration cacheEvictionPeriod;
    private final long cacheWeightInBytes;

    private TikaConfiguration(String host, int port, int timeoutInMillis, Duration cacheEvictionPeriod, long cacheWeightInBytes) {
        this.host = host;
        this.port = port;
        this.timeoutInMillis = timeoutInMillis;
        this.cacheEvictionPeriod = cacheEvictionPeriod;
        this.cacheWeightInBytes = cacheWeightInBytes;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getTimeoutInMillis() {
        return timeoutInMillis;
    }

    public Duration getCacheEvictionPeriod() {
        return cacheEvictionPeriod;
    }

    public long getCacheWeightInBytes() {
        return cacheWeightInBytes;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof TikaConfiguration) {
            TikaConfiguration that = (TikaConfiguration) o;

            return Objects.equals(this.port, that.port)
                && Objects.equals(this.timeoutInMillis, that.timeoutInMillis)
                && Objects.equals(this.cacheWeightInBytes, that.cacheWeightInBytes)
                && Objects.equals(this.host, that.host)
                && Objects.equals(this.cacheEvictionPeriod, that.cacheEvictionPeriod);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(host, port, timeoutInMillis, cacheEvictionPeriod, cacheWeightInBytes);
    }

}
