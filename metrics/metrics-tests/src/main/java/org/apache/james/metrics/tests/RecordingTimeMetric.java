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

package org.apache.james.metrics.tests;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.james.metrics.api.TimeMetric;

import com.google.common.base.Stopwatch;

public class RecordingTimeMetric implements TimeMetric {
    static class DefaultExecutionResult implements ExecutionResult {
        private final Duration elasped;

        DefaultExecutionResult(Duration elasped) {
            this.elasped = elasped;
        }

        @Override
        public Duration elasped() {
            return elasped;
        }

        @Override
        public ExecutionResult logWhenExceedP99(Duration thresholdInNanoSeconds) {
            return this;
        }
    }

    private final String name;
    private final Stopwatch stopwatch = Stopwatch.createStarted();
    private final Consumer<Duration> publishCallback;

    RecordingTimeMetric(String name, Consumer<Duration> publishCallback) {
        this.name = name;
        this.publishCallback = publishCallback;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ExecutionResult stopAndPublish() {
        Duration elapsed = Duration.ofNanos(stopwatch.elapsed(TimeUnit.NANOSECONDS));
        publishCallback.accept(elapsed);
        return new DefaultExecutionResult(elapsed);
    }
}
