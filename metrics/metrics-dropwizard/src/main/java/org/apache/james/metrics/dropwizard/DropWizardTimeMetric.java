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

package org.apache.james.metrics.dropwizard;

import java.time.Duration;
import java.util.function.Supplier;

import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class DropWizardTimeMetric implements TimeMetric {

    private static final Logger LOGGER = LoggerFactory.getLogger(DropWizardTimeMetric.class);

    static class DropWizardExecutionResult implements ExecutionResult {
        private final String name;
        private final Duration elasped;
        private final Supplier<Duration> p99Supplier;

        DropWizardExecutionResult(String name, Duration elasped, Supplier<Duration> p99Supplier) {
            Preconditions.checkNotNull(elasped);
            Preconditions.checkNotNull(p99Supplier);
            Preconditions.checkNotNull(name);

            this.name = name;
            this.elasped = elasped;
            this.p99Supplier = p99Supplier;
        }

        @Override
        public Duration elasped() {
            return elasped;
        }

        @Override
        public ExecutionResult logWhenExceedP99(Duration thresholdInNanoSeconds) {
            Preconditions.checkNotNull(thresholdInNanoSeconds);
            Duration p99 = p99Supplier.get();
            if (elasped.compareTo(p99) > 0 && elasped.compareTo(thresholdInNanoSeconds) > 0) {
                LOGGER.warn("{} metrics took {} nano seconds to complete, exceeding its {} nano seconds p99",
                    name, elasped, p99);
            }
            return this;
        }
    }

    private final String name;
    private final Timer.Context context;
    private final Timer timer;

    public DropWizardTimeMetric(String name, Timer timer) {
        this.name = name;
        this.timer = timer;
        this.context = this.timer.time();
    }

    @VisibleForTesting
    Timer.Context getContext() {
        return context;
    }

    @VisibleForTesting
    Timer getTimer() {
        return timer;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ExecutionResult stopAndPublish() {
        return new DropWizardExecutionResult(name, Duration.ofNanos(context.stop()),
            () -> Duration.ofNanos(Math.round(timer.getSnapshot().get999thPercentile())));
    }
}
