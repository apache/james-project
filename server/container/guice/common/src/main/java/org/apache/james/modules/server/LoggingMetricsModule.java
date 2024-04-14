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

package org.apache.james.modules.server;

import static com.codahale.metrics.Slf4jReporter.LoggingLevel.DEBUG;

import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.james.lifecycle.api.Startable;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.ProvidesIntoSet;

/**
 * This module is intended for logging metrics in stress-test context, it's not meant to be a general purpose
 * metrics module
 */
public class LoggingMetricsModule extends AbstractModule {

    public static final Logger LOGGER = LoggerFactory.getLogger("org.apache.james.metrics");

    @Override
    protected void configure() {
        bind(StartableSlf4jReporter.class).in(Scopes.SINGLETON);
    }

    @ProvidesIntoSet
    InitializationOperation startReporter(StartableSlf4jReporter instance) {
        return InitilizationOperationBuilder
            .forClass(StartableSlf4jReporter.class)
            .init(instance::start);
    }

    @Singleton
    private static class StartableSlf4jReporter implements Startable {
        private final Slf4jReporter reporter;
        private boolean enableLogMetrics;

        @Inject
        StartableSlf4jReporter(MetricRegistry registry) {
            this.reporter = Slf4jReporter.forRegistry(registry)
                .outputTo(LOGGER)
                .withLoggingLevel(DEBUG)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        }

        private void start() {
            enableLogMetrics = LOGGER.isDebugEnabled();
            if (enableLogMetrics) {
                int reportingPeriod = 10;
                reporter.start(reportingPeriod, TimeUnit.SECONDS);
            }
        }

        @PreDestroy
        public void close() {
            if (enableLogMetrics) {
                reporter.close();
            }
        }

    }
}
