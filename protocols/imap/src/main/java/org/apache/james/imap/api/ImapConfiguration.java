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

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.imap.api.message.Capability;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

public class ImapConfiguration {
    public static final boolean DEFAULT_ENABLE_IDLE = true;
    public static final long DEFAULT_HEARTBEAT_INTERVAL_IN_SECONDS = 2 * 60;
    public static final TimeUnit DEFAULT_HEARTBEAT_INTERVAL_UNIT = TimeUnit.SECONDS;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private static boolean noBlankString(String disableCap) {
            return !StringUtils.isBlank(disableCap);
        }

        private static final boolean DEFAULT_CONDSTORE_DISABLE = false;

        private Optional<Long> idleTimeInterval;
        private Optional<TimeUnit> idleTimeIntervalUnit;
        private Optional<Boolean> enableIdle;
        private ImmutableSet<String> disabledCaps;
        private Optional<Boolean> isCondstoreEnable;

        private Builder() {
            this.idleTimeInterval = Optional.empty();
            this.idleTimeIntervalUnit = Optional.empty();
            this.enableIdle = Optional.empty();
            this.disabledCaps = ImmutableSet.of();
            this.isCondstoreEnable = Optional.empty();
        }

        public Builder idleTimeInterval(long idleTimeInterval) {
            Preconditions.checkArgument(idleTimeInterval > 0, "The interval time should not be rezo or negative");
            this.idleTimeInterval = Optional.of(idleTimeInterval);
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

        public ImapConfiguration build() {
            ImmutableSet<Capability> normalizeDisableCaps = disabledCaps.stream()
                    .filter(Builder::noBlankString)
                    .map(StringUtils::normalizeSpace)
                    .map(Capability::of)
                    .collect(Guavate.toImmutableSet());
            return new ImapConfiguration(
                    enableIdle.orElse(DEFAULT_ENABLE_IDLE),
                    idleTimeInterval.orElse(DEFAULT_HEARTBEAT_INTERVAL_IN_SECONDS),
                    idleTimeIntervalUnit.orElse(DEFAULT_HEARTBEAT_INTERVAL_UNIT),
                    normalizeDisableCaps,
                    isCondstoreEnable.orElse(DEFAULT_CONDSTORE_DISABLE));
        }
    }

    private final long idleTimeInterval;
    private final TimeUnit idleTimeIntervalUnit;
    private final ImmutableSet<Capability> disabledCaps;
    private final boolean enableIdle;
    private final boolean isCondstoreEnable;

    private ImapConfiguration(boolean enableIdle, long idleTimeInterval, TimeUnit idleTimeIntervalUnit, ImmutableSet<Capability> disabledCaps, boolean isCondstoreEnable) {
        this.enableIdle = enableIdle;
        this.idleTimeInterval = idleTimeInterval;
        this.idleTimeIntervalUnit = idleTimeIntervalUnit;
        this.disabledCaps = disabledCaps;
        this.isCondstoreEnable = isCondstoreEnable;
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

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof ImapConfiguration) {
            ImapConfiguration that = (ImapConfiguration)obj;
            return Objects.equal(that.isEnableIdle(), enableIdle)
                && Objects.equal(that.getIdleTimeInterval(), idleTimeInterval)
                && Objects.equal(that.getIdleTimeIntervalUnit(), idleTimeIntervalUnit)
                && Objects.equal(that.getDisabledCaps(), disabledCaps)
                && Objects.equal(that.isCondstoreEnable(), isCondstoreEnable);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(enableIdle, idleTimeInterval, idleTimeIntervalUnit, disabledCaps, isCondstoreEnable);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("enabledIdle", enableIdle)
                .add("idleTimeInterval", idleTimeInterval)
                .add("idleTimeIntervalUnit", idleTimeIntervalUnit)
                .add("disabledCaps", disabledCaps)
                .add("isCondstoreEnable", isCondstoreEnable)
                .toString();
    }
}
