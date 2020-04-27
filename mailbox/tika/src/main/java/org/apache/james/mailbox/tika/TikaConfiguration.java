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
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.james.mailbox.model.ContentType.MimeType;
import org.apache.james.util.Port;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;

public class TikaConfiguration {

    public static class Builder {
        private Optional<Boolean> isEnabled;
        private Optional<Boolean> isCacheEnabled;
        private Optional<String> host;
        private Optional<Integer> port;
        private Optional<Integer> timeoutInMillis;
        private Optional<Duration> cacheEvictionPeriod;
        private Optional<Long> cacheWeightInBytes;
        private ImmutableSet.Builder<MimeType> contentTypeBlacklist;

        private Builder() {
            isEnabled = Optional.empty();
            isCacheEnabled = Optional.empty();
            host = Optional.empty();
            port = Optional.empty();
            timeoutInMillis = Optional.empty();
            cacheEvictionPeriod = Optional.empty();
            cacheWeightInBytes = Optional.empty();
            contentTypeBlacklist = ImmutableSet.builder();
        }

        public Builder enable(Optional<Boolean> isEnabled) {
            Preconditions.checkNotNull(isEnabled);
            this.isEnabled = isEnabled;
            return this;
        }

        public Builder enabled() {
            this.isEnabled = Optional.of(true);
            return this;
        }

        public Builder disabled() {
            this.isEnabled = Optional.of(false);
            return this;
        }

        public Builder cacheEnable(Optional<Boolean> isEnabled) {
            Preconditions.checkNotNull(isEnabled);
            this.isCacheEnabled = isEnabled;
            return this;
        }

        public Builder cacheEnabled() {
            this.isCacheEnabled = Optional.of(true);
            return this;
        }

        public Builder cacheDisabled() {
            this.isCacheEnabled = Optional.of(false);
            return this;
        }

        public Builder host(String host) {
            Preconditions.checkNotNull(host);
            this.host = Optional.of(host);
            return this;
        }

        public Builder host(Optional<String> host) {
            Preconditions.checkNotNull(host);
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = Optional.of(port);
            return this;
        }

        public Builder port(Optional<Integer> port) {
            Preconditions.checkNotNull(port);
            this.port = port;
            return this;
        }

        public Builder timeoutInMillis(int timeoutInMillis) {
            this.timeoutInMillis = Optional.of(timeoutInMillis);
            return this;
        }

        public Builder timeoutInMillis(Optional<Integer> timeoutInMillis) {
            Preconditions.checkNotNull(timeoutInMillis);
            this.timeoutInMillis = timeoutInMillis;
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

        public Builder contentTypeBlacklist(Set<MimeType> contentTypeBlacklist) {
            Preconditions.checkNotNull(contentTypeBlacklist);
            this.contentTypeBlacklist.addAll(contentTypeBlacklist);
            return this;
        }

        public TikaConfiguration build() {
            port.ifPresent(Port::assertValid);

            return new TikaConfiguration(
                isEnabled.orElse(DEFAULT_DISABLED),
                isCacheEnabled.orElse(DEFAULT_DISABLED),
                host.orElse(DEFAULT_HOST),
                port.orElse(DEFAULT_PORT),
                timeoutInMillis.orElse(DEFAULT_TIMEOUT_IN_MS),
                cacheEvictionPeriod.orElse(DEFAULT_CACHE_EVICTION_PERIOD),
                cacheWeightInBytes.orElse(DEFAULT_CACHE_LIMIT_100_MB),
                contentTypeBlacklist.build());
        }
    }

    public static final long DEFAULT_CACHE_LIMIT_100_MB = 1024L * 1024L * 100L;
    public static final Duration DEFAULT_CACHE_EVICTION_PERIOD = Duration.ofDays(1);
    public static final boolean DEFAULT_DISABLED = false;
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 9998;
    public static final int DEFAULT_TIMEOUT_IN_MS = Ints.checkedCast(TimeUnit.SECONDS.toMillis(30));

    public static Builder builder() {
        return new Builder();
    }

    private final boolean enabled;
    private final boolean cacheEnabled;
    private final String host;
    private final int port;
    private final int timeoutInMillis;
    private final Duration cacheEvictionPeriod;
    private final long cacheWeightInBytes;
    private final ImmutableSet<MimeType> contentTypeBlacklist;

    private TikaConfiguration(boolean enabled, boolean cacheEnabled, String host, int port, int timeoutInMillis, Duration cacheEvictionPeriod, long cacheWeightInBytes,  ImmutableSet<MimeType> contentTypeBlacklist) {
        this.enabled = enabled;
        this.cacheEnabled = cacheEnabled;
        this.host = host;
        this.port = port;
        this.timeoutInMillis = timeoutInMillis;
        this.cacheEvictionPeriod = cacheEvictionPeriod;
        this.cacheWeightInBytes = cacheWeightInBytes;
        this.contentTypeBlacklist = contentTypeBlacklist;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
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

    public ImmutableSet<MimeType> getContentTypeBlacklist() {
        return contentTypeBlacklist;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof TikaConfiguration) {
            TikaConfiguration that = (TikaConfiguration) o;

            return Objects.equals(this.enabled, that.enabled)
                && Objects.equals(this.cacheEnabled, that.cacheEnabled)
                && Objects.equals(this.port, that.port)
                && Objects.equals(this.timeoutInMillis, that.timeoutInMillis)
                && Objects.equals(this.cacheWeightInBytes, that.cacheWeightInBytes)
                && Objects.equals(this.host, that.host)
                && Objects.equals(this.cacheEvictionPeriod, that.cacheEvictionPeriod)
                && Objects.equals(this.contentTypeBlacklist, that.contentTypeBlacklist);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(enabled, cacheEnabled, host, port, timeoutInMillis, cacheEvictionPeriod, cacheWeightInBytes, contentTypeBlacklist);
    }

}
