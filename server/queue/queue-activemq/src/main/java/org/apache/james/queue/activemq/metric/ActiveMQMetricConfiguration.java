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
package org.apache.james.queue.activemq.metric;

import java.time.Duration;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.util.DurationParser;

import com.google.common.base.Preconditions;

public class ActiveMQMetricConfiguration {
    private static final String ENABLED = "metrics.enabled";
    private static final String START_DELAY = "metrics.start_delay";
    private static final String INTERVAL = "metrics.interval";
    private static final String TIMEOUT  = "metrics.timeout";
    private static final String AQMP_TIMEOUT  = "metrics.aqmp_timeout";

    private static final Duration MINIMAL_START_DELAY = Duration.ofSeconds(1);
    private static final Duration MINIMAL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration MINIMAL_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration MINIMAL_AQMP_TIMEOUT = Duration.ofSeconds(1);

    private final boolean enabled;
    private final Duration startDelay;
    private final Duration interval;
    private final Duration timeout;
    private final Duration aqmpTimeout;

    public static ActiveMQMetricConfiguration from(Configuration configuration) {
        return new ActiveMQMetricConfiguration(
            configuration.getBoolean(ENABLED, true),
            getDurationFromConfiguration(configuration, START_DELAY).orElse(MINIMAL_START_DELAY),
            getDurationFromConfiguration(configuration, INTERVAL).orElse(MINIMAL_INTERVAL),
            getDurationFromConfiguration(configuration, TIMEOUT).orElse(MINIMAL_TIMEOUT),
            getDurationFromConfiguration(configuration, AQMP_TIMEOUT).orElse(MINIMAL_AQMP_TIMEOUT)
        );
    }

    public ActiveMQMetricConfiguration(boolean enabled, Duration startDelay, Duration interval,
        Duration timeout, Duration aqmpTimeout) {
        this.enabled = enabled;
        this.startDelay = startDelay;
        this.interval = interval;
        this.timeout = timeout;
        this.aqmpTimeout = aqmpTimeout;
        checkConfiguration();
    }

    private void checkConfiguration() {
        Preconditions.checkArgument(startDelay.compareTo(MINIMAL_START_DELAY) >= 0,
            "'%s' must be equal or greater than %d ms",
            START_DELAY, MINIMAL_START_DELAY.toMillis());
        Preconditions.checkArgument(interval.compareTo(MINIMAL_INTERVAL) >= 0,
            "'%s' must be equal or greater than %d ms",
            INTERVAL, MINIMAL_INTERVAL.toMillis());
        Preconditions.checkArgument(timeout.compareTo(MINIMAL_TIMEOUT) >= 0,
            "'%s' must be equal or greater than %d ms",
            TIMEOUT, MINIMAL_TIMEOUT.toMillis());
        Preconditions.checkArgument(aqmpTimeout.compareTo(MINIMAL_AQMP_TIMEOUT) >= 0,
            "'%s' must be equal or greater than %d ms",
            AQMP_TIMEOUT, MINIMAL_AQMP_TIMEOUT.toMillis());

        Preconditions.checkArgument(interval.compareTo(timeout) > 0,
            "'%s' must be less than '%s'", TIMEOUT, INTERVAL);
        Preconditions.checkArgument(timeout.compareTo(aqmpTimeout) > 0,
            "'%s' must be less than '%s'", AQMP_TIMEOUT, TIMEOUT);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Duration getStartDelay() {
        return startDelay;
    }

    public Duration getInterval() {
        return interval;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public Duration getAqmpTimeout() {
        return aqmpTimeout;
    }

    private static Optional<Duration> getDurationFromConfiguration(Configuration configuration, String key) {
        return StringUtils.isEmpty(configuration.getString(key))
            ? Optional.empty() : Optional.of(DurationParser.parse(configuration.getString(key)));
    }
}
