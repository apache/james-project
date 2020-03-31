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

import java.time.Duration;
import java.util.Objects;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.util.DurationParser;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class PeriodicalHealthChecksConfiguration {

    private static final String HEALTH_CHECK_PERIOD = "healthcheck.period";
    private static final Duration DEFAULT_HEALTH_CHECK_PERIOD = Duration.ofSeconds(60);
    private static final Duration MINIMAL_HEALTH_CHECK_PERIOD = Duration.ofSeconds(10);
    public static final PeriodicalHealthChecksConfiguration DEFAULT_CONFIGURATION = builder()
        .period(DEFAULT_HEALTH_CHECK_PERIOD)
        .build();

    public interface Builder {

        @FunctionalInterface
        interface RequiredPeriod {
            ReadyToBuild period(Duration period);
        }

        class ReadyToBuild {
            private final Duration period;

            ReadyToBuild(Duration period) {
                this.period = period;
            }

            PeriodicalHealthChecksConfiguration build() {
                Preconditions.checkArgument(period.compareTo(MINIMAL_HEALTH_CHECK_PERIOD) >= 0,
                    "'period' must be equal or greater than " + MINIMAL_HEALTH_CHECK_PERIOD.toMillis() + "ms");

                return new PeriodicalHealthChecksConfiguration(period);
            }
        }
    }

    public static Builder.RequiredPeriod builder() {
        return Builder.ReadyToBuild::new;
    }

    public static PeriodicalHealthChecksConfiguration from(Configuration configuration) {
        return builder()
            .period(getDurationFromConfiguration(configuration))
            .build();
    }

    private final Duration period;

    @VisibleForTesting
    PeriodicalHealthChecksConfiguration(Duration period) {
        this.period = period;
    }

    public Duration getPeriod() {
        return period;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof PeriodicalHealthChecksConfiguration) {
            PeriodicalHealthChecksConfiguration that = (PeriodicalHealthChecksConfiguration) o;

            return Objects.equals(this.period, that.period);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(period);
    }

    private static Duration getDurationFromConfiguration(Configuration configuration) {
        if (StringUtils.isEmpty(configuration.getString(HEALTH_CHECK_PERIOD))) {
           return DEFAULT_HEALTH_CHECK_PERIOD;
        }

        return DurationParser.parse(configuration.getString(HEALTH_CHECK_PERIOD));
    }
}