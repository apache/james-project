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

package org.apache.james.imap.api;

import java.time.Duration;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.imap.api.message.Capability;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

public class ImapConfiguration {
    public static final boolean DEFAULT_ENABLE_IDLE = true;
    public static final long DEFAULT_HEARTBEAT_INTERVAL_IN_SECONDS = 2 * 60;
    public static final TimeUnit DEFAULT_HEARTBEAT_INTERVAL_UNIT = TimeUnit.SECONDS;
    public static final int DEFAULT_CONCURRENT_REQUESTS = 128;
    public static final int DEFAULT_QUEUE_SIZE = 4096;
    public static final boolean DEFAULT_PROVISION_DEFAULT_MAILBOXES = true;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private static boolean noBlankString(String disableCap) {
            return !StringUtils.isBlank(disableCap);
        }

        private static final boolean DEFAULT_CONDSTORE_DISABLE = false;

        private Optional<Long> idleTimeInterval;
        private Optional<Long> appendLimit;
        private Optional<Integer> concurrentRequests;
        private Optional<Integer> maxQueueSize;
        private Optional<TimeUnit> idleTimeIntervalUnit;
        private Optional<Boolean> enableIdle;
        private ImmutableSet<String> disabledCaps;
        private Optional<Boolean> isCondstoreEnable;
        private Optional<Boolean> provisionDefaultMailboxes;
        private Optional<Properties> customProperties;
        private ImmutableSet<String> additionalConnectionChecks;

        private Builder() {
            this.appendLimit = Optional.empty();
            this.concurrentRequests = Optional.empty();
            this.maxQueueSize = Optional.empty();
            this.idleTimeInterval = Optional.empty();
            this.idleTimeIntervalUnit = Optional.empty();
            this.enableIdle = Optional.empty();
            this.disabledCaps = ImmutableSet.of();
            this.isCondstoreEnable = Optional.empty();
            this.provisionDefaultMailboxes = Optional.empty();
            this.customProperties = Optional.empty();
            this.additionalConnectionChecks = ImmutableSet.of();
        }

        public Builder idleTimeInterval(long idleTimeInterval) {
            Preconditions.checkArgument(idleTimeInterval > 0, "The interval time should not be zero or negative");
            this.idleTimeInterval = Optional.of(idleTimeInterval);
            return this;
        }

        public Builder concurrentRequests(int concurrentRequests) {
            this.concurrentRequests = Optional.of(concurrentRequests);
            return this;
        }

        public Builder maxQueueSize(int maxQueueSize) {
            Preconditions.checkArgument(maxQueueSize > 0, "maxQueueSize should not be negative");
            this.maxQueueSize = Optional.of(maxQueueSize);
            return this;
        }

        public Builder idleTimeIntervalUnit(TimeUnit idleTimeIntervalUnit) {
            this.idleTimeIntervalUnit = Optional.of(idleTimeIntervalUnit);
            return this;
        }

        public Builder enableIdle(Boolean enableIdle) {
            this.enableIdle = Optional.of(enableIdle);
            return this;
        }

        public Builder disabledCaps(ImmutableSet<String> disabledCaps) {
            this.disabledCaps = disabledCaps;
            return this;
        }

        public Builder disabledCaps(String... disableCaps) {
            this.disabledCaps = ImmutableSet.copyOf(disableCaps);
            return this;
        }

        public Builder disabledCap(String disableCap) {
            this.disabledCaps = ImmutableSet.of(disableCap);
            return this;
        }

        public Builder isCondstoreEnable(boolean isCondstoreEnable) {
            this.isCondstoreEnable = Optional.of(isCondstoreEnable);
            return this;
        }

        public Builder appendLimit(long appendLimit) {
            this.appendLimit = Optional.of(appendLimit);
            return this;
        }

        public Builder appendLimit(Optional<Integer> appendLimit) {
            this.appendLimit = appendLimit.map(Integer::longValue);
            return this;
        }

        public Builder isProvisionDefaultMailboxes(Boolean isProvisionDefaultMailboxes) {
            this.provisionDefaultMailboxes = Optional.of(isProvisionDefaultMailboxes);
            return this;
        }

        public Builder withCustomProperties(Properties customProperties) {
            this.customProperties = Optional.of(customProperties);
            return this;
        }

        public Builder connectionChecks(ImmutableSet<String> additionalConnectionChecks) {
            this.additionalConnectionChecks = additionalConnectionChecks;
            return this;
        }

        public ImapConfiguration build() {
            ImmutableSet<Capability> normalizeDisableCaps = disabledCaps.stream()
                    .filter(Builder::noBlankString)
                    .map(StringUtils::normalizeSpace)
                    .map(Capability::of)
                    .collect(ImmutableSet.toImmutableSet());
            return new ImapConfiguration(
                    appendLimit,
                    enableIdle.orElse(DEFAULT_ENABLE_IDLE),
                    idleTimeInterval.orElse(DEFAULT_HEARTBEAT_INTERVAL_IN_SECONDS),
                    concurrentRequests.orElse(DEFAULT_CONCURRENT_REQUESTS),
                    maxQueueSize.orElse(DEFAULT_QUEUE_SIZE),
                    idleTimeIntervalUnit.orElse(DEFAULT_HEARTBEAT_INTERVAL_UNIT),
                    normalizeDisableCaps,
                    isCondstoreEnable.orElse(DEFAULT_CONDSTORE_DISABLE),
                    provisionDefaultMailboxes.orElse(DEFAULT_PROVISION_DEFAULT_MAILBOXES),
                    customProperties.orElseGet(Properties::new),
                    additionalConnectionChecks);
        }
    }

    private final Optional<Long> appendLimit;
    private final long idleTimeInterval;
    private final int concurrentRequests;
    private final int maxQueueSize;
    private final TimeUnit idleTimeIntervalUnit;
    private final ImmutableSet<Capability> disabledCaps;
    private final boolean enableIdle;
    private final boolean isCondstoreEnable;
    private final boolean provisionDefaultMailboxes;
    private final Properties customProperties;
    private final ImmutableSet<String> additionalConnectionChecks;

    private ImapConfiguration(Optional<Long> appendLimit, boolean enableIdle, long idleTimeInterval, int concurrentRequests, int maxQueueSize, TimeUnit idleTimeIntervalUnit, ImmutableSet<Capability> disabledCaps, boolean isCondstoreEnable, boolean provisionDefaultMailboxes, Properties customProperties, ImmutableSet<String> additionalConnectionChecks) {
        this.appendLimit = appendLimit;
        this.enableIdle = enableIdle;
        this.idleTimeInterval = idleTimeInterval;
        this.concurrentRequests = concurrentRequests;
        this.maxQueueSize = maxQueueSize;
        this.idleTimeIntervalUnit = idleTimeIntervalUnit;
        this.disabledCaps = disabledCaps;
        this.isCondstoreEnable = isCondstoreEnable;
        this.provisionDefaultMailboxes = provisionDefaultMailboxes;
        this.customProperties = customProperties;
        this.additionalConnectionChecks = additionalConnectionChecks;
    }

    public Optional<Long> getAppendLimit() {
        return appendLimit;
    }

    public int getConcurrentRequests() {
        return concurrentRequests;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public long getIdleTimeInterval() {
        return idleTimeInterval;
    }

    public TimeUnit getIdleTimeIntervalUnit() {
        return idleTimeIntervalUnit;
    }

    public ImmutableSet<Capability> getDisabledCaps() {
        return disabledCaps;
    }

    public boolean isEnableIdle() {
        return enableIdle;
    }
    
    public boolean isCondstoreEnable() {
        return isCondstoreEnable;
    }

    public boolean isProvisionDefaultMailboxes() {
        return provisionDefaultMailboxes;
    }

    public Duration idleTimeIntervalAsDuration() {
        return Duration.of(getIdleTimeInterval(), getIdleTimeIntervalUnit().toChronoUnit());
    }

    public Properties getCustomProperties() {
        return customProperties;
    }

    public ImmutableSet<String> getAdditionalConnectionChecks() {
        return additionalConnectionChecks;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof ImapConfiguration) {
            ImapConfiguration that = (ImapConfiguration)obj;
            return Objects.equal(that.isEnableIdle(), enableIdle)
                && Objects.equal(that.getIdleTimeInterval(), idleTimeInterval)
                && Objects.equal(that.getAppendLimit(), appendLimit)
                && Objects.equal(that.getIdleTimeIntervalUnit(), idleTimeIntervalUnit)
                && Objects.equal(that.getConcurrentRequests(), concurrentRequests)
                && Objects.equal(that.getMaxQueueSize(), maxQueueSize)
                && Objects.equal(that.getDisabledCaps(), disabledCaps)
                && Objects.equal(that.isProvisionDefaultMailboxes(), provisionDefaultMailboxes)
                && Objects.equal(that.getCustomProperties(), customProperties)
                && Objects.equal(that.isCondstoreEnable(), isCondstoreEnable)
                && Objects.equal(that.getAdditionalConnectionChecks(), additionalConnectionChecks);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(enableIdle, idleTimeInterval, idleTimeIntervalUnit, disabledCaps, isCondstoreEnable,
            concurrentRequests, maxQueueSize, appendLimit, provisionDefaultMailboxes, customProperties, additionalConnectionChecks);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("appendLimit", appendLimit)
                .add("enabledIdle", enableIdle)
                .add("idleTimeInterval", idleTimeInterval)
                .add("idleTimeIntervalUnit", idleTimeIntervalUnit)
                .add("disabledCaps", disabledCaps)
                .add("isCondstoreEnable", isCondstoreEnable)
                .add("concurrentRequests", concurrentRequests)
                .add("maxQueueSize", maxQueueSize)
                .add("provisionDefaultMailboxes", provisionDefaultMailboxes)
                .add("customProperties", customProperties)
                .add("additionalConnectionChecks", additionalConnectionChecks)
                .toString();
    }
}
