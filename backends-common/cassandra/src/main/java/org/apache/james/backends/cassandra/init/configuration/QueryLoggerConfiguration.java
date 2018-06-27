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

package org.apache.james.backends.cassandra.init.configuration;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.PropertiesConfiguration;

import com.datastax.driver.core.PerHostPercentileTracker;
import com.datastax.driver.core.QueryLogger;
import com.google.common.base.Preconditions;

public class QueryLoggerConfiguration {

    public static class Builder {
        private Optional<Long> constantThreshold;
        private Optional<PerHostPercentileTracker> percentileTracker;
        private Optional<Double> slowQueryLatencyThresholdPercentile;
        private Optional<Integer> maxLoggedParameters;
        private Optional<Integer> maxParameterValueLength;
        private Optional<Integer> maxQueryStringLength;

        private Builder() {
            constantThreshold = Optional.empty();
            percentileTracker = Optional.empty();
            slowQueryLatencyThresholdPercentile = Optional.empty();
            maxLoggedParameters = Optional.empty();
            maxParameterValueLength = Optional.empty();
            maxQueryStringLength = Optional.empty();
        }

        public Builder withConstantThreshold(long constantThreshold) {
            this.constantThreshold = Optional.of(constantThreshold);

            return this;
        }

        public Builder withDynamicThreshold(PerHostPercentileTracker percentileTracker,
                                            double slowQueryLatencyThresholdPercentile) {
            this.percentileTracker = Optional.of(percentileTracker);
            this.slowQueryLatencyThresholdPercentile = Optional.of(slowQueryLatencyThresholdPercentile);

            return this;
        }

        public Builder withMaxLoggedParameters(int maxLoggedParameters) {
            this.maxLoggedParameters = Optional.of(maxLoggedParameters);

            return this;
        }

        public Builder withMaxParameterValueLength(int maxParameterValueLength) {
            this.maxParameterValueLength = Optional.of(maxParameterValueLength);

            return this;
        }

        public Builder withMaxQueryStringLength(int maxQueryStringLength) {
            this.maxQueryStringLength = Optional.of(maxQueryStringLength);

            return this;
        }

        public QueryLoggerConfiguration build() {
            Preconditions.checkState(!(constantThreshold.isPresent() && percentileTracker.isPresent()),
                "You can not use slowQueryLatencyTheresholdMillis and percentileTracker at the same time");

            return new QueryLoggerConfiguration(
                constantThreshold,
                percentileTracker,
                slowQueryLatencyThresholdPercentile,
                maxLoggedParameters,
                maxParameterValueLength,
                maxQueryStringLength
            );
        }
    }

    private static final long CASSANDRA_HIGHEST_TRACKABLE_LATENCY_MILLIS = TimeUnit.SECONDS.toMillis(10);

    public static final QueryLoggerConfiguration DEFAULT = QueryLoggerConfiguration.builder()
        .withDynamicThreshold(PerHostPercentileTracker
            .builder(CASSANDRA_HIGHEST_TRACKABLE_LATENCY_MILLIS)
            .build(),
            QueryLogger.DEFAULT_SLOW_QUERY_THRESHOLD_PERCENTILE)
        .build();


    public static Builder builder() {
        return new Builder();
    }

    public static QueryLoggerConfiguration from(PropertiesConfiguration configuration) {
        QueryLoggerConfiguration.Builder builder = QueryLoggerConfiguration.builder();

        Optional<Long> constantThreshold = getOptionalIntegerFromConf(configuration, "cassandra.query.logger.constant.threshold")
            .map(Long::valueOf);

        constantThreshold.ifPresent(builder::withConstantThreshold);

        getOptionalIntegerFromConf(configuration, "cassandra.query.logger.max.logged.parameters")
            .ifPresent(builder::withMaxLoggedParameters);

        getOptionalIntegerFromConf(configuration, "cassandra.query.logger.max.query.string.length")
            .ifPresent(builder::withMaxQueryStringLength);

        getOptionalIntegerFromConf(configuration, "cassandra.query.logger.max.parameter.value.length")
            .ifPresent(builder::withMaxParameterValueLength);

        Optional<Double> percentileLatencyConf = getOptionalDoubleFromConf(configuration, "cassandra.query.slow.query.latency.threshold.percentile");

        if (!percentileLatencyConf.isPresent() && !constantThreshold.isPresent()) {
            percentileLatencyConf = Optional.of(QueryLogger.DEFAULT_SLOW_QUERY_THRESHOLD_PERCENTILE);
        }

        percentileLatencyConf.ifPresent(slowQueryLatencyThresholdPercentile -> {
            PerHostPercentileTracker tracker = PerHostPercentileTracker
                .builder(CASSANDRA_HIGHEST_TRACKABLE_LATENCY_MILLIS)
                .build();

            builder.withDynamicThreshold(tracker, slowQueryLatencyThresholdPercentile);
        });

        return builder.build();
    }

    private static Optional<Integer> getOptionalIntegerFromConf(PropertiesConfiguration configuration, String key) {
        return Optional.ofNullable(configuration.getInteger(key, null));
    }

    private static Optional<Double> getOptionalDoubleFromConf(PropertiesConfiguration configuration, String key) {
        return Optional.ofNullable(configuration.getDouble(key, null));
    }

    private final Optional<Long> constantThreshold;
    private final Optional<PerHostPercentileTracker> percentileTracker;
    private final Optional<Double> slowQueryLatencyThresholdPercentile;
    private final Optional<Integer> maxLoggedParameters;
    private final Optional<Integer> maxParameterValueLength;
    private final Optional<Integer> maxQueryStringLength;

    private QueryLoggerConfiguration(Optional<Long> constantThreshold,
                                     Optional<PerHostPercentileTracker> percentileTracker,
                                     Optional<Double> slowQueryLatencyThresholdPercentile,
                                     Optional<Integer> maxLoggedParameters,
                                     Optional<Integer> maxParameterValueLength,
                                     Optional<Integer> maxQueryStringLength) {
        this.constantThreshold = constantThreshold;
        this.percentileTracker = percentileTracker;
        this.slowQueryLatencyThresholdPercentile = slowQueryLatencyThresholdPercentile;
        this.maxLoggedParameters = maxLoggedParameters;
        this.maxParameterValueLength = maxParameterValueLength;
        this.maxQueryStringLength = maxQueryStringLength;
    }

    public QueryLogger getQueryLogger() {
        QueryLogger.Builder builder = QueryLogger.builder();

        percentileTracker.map(percentileTracker ->
            slowQueryLatencyThresholdPercentile.map(slowQueryLatencyThresholdPercentile ->
                builder.withDynamicThreshold(percentileTracker, slowQueryLatencyThresholdPercentile)
            )
        );

        constantThreshold.ifPresent(builder::withConstantThreshold);
        constantThreshold.ifPresent(builder::withConstantThreshold);
        maxLoggedParameters.ifPresent(builder::withMaxLoggedParameters);
        maxParameterValueLength.ifPresent(builder::withMaxParameterValueLength);
        maxQueryStringLength.ifPresent(builder::withMaxQueryStringLength);

        return builder.build();
    }
}
