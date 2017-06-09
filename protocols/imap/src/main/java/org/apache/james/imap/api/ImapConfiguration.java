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

import java.util.concurrent.TimeUnit;

import org.apache.james.imap.processor.IdleProcessor;
import org.apache.commons.lang.StringUtils;

import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;

public class ImapConfiguration {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private static final Function<String, String> NORMALIZE_STRING = new Function<String, String>() {
            @Override
            public String apply(String disableCap) {
                return StringUtils.normalizeSpace(disableCap);
            }
        };
        private static final Predicate<String> NO_BLANK = new Predicate<String>() {
            @Override
            public boolean apply(String disableCap) {
                return noBlankString(disableCap);
            }
        };

        private static boolean noBlankString(String disableCap) {
            return !StringUtils.isBlank(disableCap);
        }

        private Optional<Long> idleTimeInterval;
        private Optional<TimeUnit> idleTimeIntervalUnit;
        private Optional<Boolean> enableIdle;
        private ImmutableSet<String> disabledCaps;

        private Builder() {
            this.idleTimeInterval = Optional.absent();
            this.idleTimeIntervalUnit = Optional.absent();
            this.enableIdle = Optional.absent();
            this.disabledCaps = ImmutableSet.of();
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
        public ImapConfiguration build() {
            ImmutableSet<String> normalizeDisableCaps = FluentIterable.from(disabledCaps)
                    .filter(NO_BLANK)
                    .transform(NORMALIZE_STRING)
                    .toSet();
            return new ImapConfiguration(
                    enableIdle.or(IdleProcessor.DEFAULT_ENABLE_IDLE),
                    idleTimeInterval.or(IdleProcessor.DEFAULT_HEARTBEAT_INTERVAL_IN_SECONDS),
                    idleTimeIntervalUnit.or(IdleProcessor.DEFAULT_HEARTBEAT_INTERVAL_UNIT),
                    normalizeDisableCaps);
        }
    }

    private final long idleTimeInterval;
    private final TimeUnit idleTimeIntervalUnit;
    private final ImmutableSet<String> disabledCaps;
    private final boolean enableIdle;

    private ImapConfiguration(boolean enableIdle, long idleTimeInterval, TimeUnit idleTimeIntervalUnit, ImmutableSet<String> disabledCaps) {
        this.enableIdle = enableIdle;
        this.idleTimeInterval = idleTimeInterval;
        this.idleTimeIntervalUnit = idleTimeIntervalUnit;
        this.disabledCaps = disabledCaps;
    }

    public long getIdleTimeInterval() {
        return idleTimeInterval;
    }

    public TimeUnit getIdleTimeIntervalUnit() {
        return idleTimeIntervalUnit;
    }

    public ImmutableSet<String> getDisabledCaps() {
        return disabledCaps;
    }

    public boolean isEnableIdle() {
        return enableIdle;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof ImapConfiguration) {
            ImapConfiguration that = (ImapConfiguration)obj;
            return Objects.equal(that.isEnableIdle(), enableIdle)
                && Objects.equal(that.getIdleTimeInterval(), idleTimeInterval)
                && Objects.equal(that.getIdleTimeIntervalUnit(), idleTimeIntervalUnit)
                && Objects.equal(that.getDisabledCaps(), disabledCaps);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(enableIdle, idleTimeInterval, idleTimeIntervalUnit, disabledCaps);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("enabledIdle", enableIdle)
                .add("idleTimeInterval", idleTimeInterval)
                .add("idleTimeIntervalUnit", idleTimeIntervalUnit)
                .add("disabledCaps", disabledCaps)
                .toString();
    }
}
