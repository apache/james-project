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

package org.apache.james;

import java.util.Objects;

import org.apache.commons.configuration2.Configuration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class PeriodicalHealthChecksConfiguration {

    static final String HEALTH_CHECK_INITIAL_DELAY = "healthcheck.initial.delay";
    static final String HEALTH_CHECK_PERIOD = "healthcheck.period";
    static final long DEFAULT_HEALTH_CHECK_INITIAL_DELAY = 60;
    static final long DEFAULT_HEALTH_CHECK_PERIOD = 60;
    static final long ZERO = 0;
    public static final PeriodicalHealthChecksConfiguration DEFAULT_CONFIGURATION = builder()
        .initialDelay(DEFAULT_HEALTH_CHECK_INITIAL_DELAY)
        .period(DEFAULT_HEALTH_CHECK_PERIOD)
        .build();

    public interface Builder {

        @FunctionalInterface
        interface RequiredInitialDelay {
            RequiredPeriod initialDelay(long initialDelay);
        }

        @FunctionalInterface
        interface RequiredPeriod {
            ReadyToBuild period(long period);
        }

        class ReadyToBuild {
            private final long initialDelay;
            private final long period;

            ReadyToBuild(long initialDelay, long period) {
                this.initialDelay = initialDelay;
                this.period = period;
            }

            PeriodicalHealthChecksConfiguration build() {
                Preconditions.checkArgument(initialDelay > ZERO, "'initialDelay' must be positive");
                Preconditions.checkArgument(period > ZERO, "'period' must be positive");

                return new PeriodicalHealthChecksConfiguration(initialDelay, period);
            }
        }
    }

    public static Builder.RequiredInitialDelay builder() {
        return initialDelay -> period -> new Builder.ReadyToBuild(initialDelay, period);
    }

    public static PeriodicalHealthChecksConfiguration from(Configuration configuration) {
        return builder()
            .initialDelay(configuration.getLong(HEALTH_CHECK_INITIAL_DELAY, DEFAULT_HEALTH_CHECK_INITIAL_DELAY))
            .period(configuration.getLong(HEALTH_CHECK_PERIOD, DEFAULT_HEALTH_CHECK_PERIOD))
            .build();
    }

    private final long initialDelay;
    private final long period;

    @VisibleForTesting
    PeriodicalHealthChecksConfiguration(long initialDelay, long period) {
        this.initialDelay = initialDelay;
        this.period = period;
    }

    public long getInitialDelay() {
        return initialDelay;
    }

    public long getPeriod() {
        return period;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof PeriodicalHealthChecksConfiguration) {
            PeriodicalHealthChecksConfiguration that = (PeriodicalHealthChecksConfiguration) o;

            return Objects.equals(this.initialDelay, that.initialDelay)
                && Objects.equals(this.period, that.period);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(initialDelay, period);
    }
}