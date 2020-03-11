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

package org.apache.james.metrics.api;

import static org.apache.james.metrics.api.TimeMetric.ExecutionResult.DEFAULT_100_MS_THRESHOLD;

import java.util.function.Supplier;

import org.reactivestreams.Publisher;

public interface MetricFactory {

    Metric generate(String name);

    TimeMetric timer(String name);

    default <T> T runPublishingTimerMetric(String name, Supplier<T> operation) {
        TimeMetric timer = timer(name);
        try {
            return operation.get();
        } finally {
            timer.stopAndPublish();
        }
    }

    default <T> T runPublishingTimerMetricLogP99(String name, Supplier<T> operation) {
        TimeMetric timer = timer(name);
        try {
            return operation.get();
        } finally {
            timer.stopAndPublish().logWhenExceedP99(DEFAULT_100_MS_THRESHOLD);
        }
    }

    <T> Publisher<T> runPublishingTimerMetric(String name, Publisher<T> publisher);

    <T> Publisher<T> runPublishingTimerMetricLogP99(String name, Publisher<T> publisher);

    default void runPublishingTimerMetric(String name, Runnable runnable) {
        runPublishingTimerMetric(name, () -> {
            runnable.run();
            return null;
        });
    }

}
