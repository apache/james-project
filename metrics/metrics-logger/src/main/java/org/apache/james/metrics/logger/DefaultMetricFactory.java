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
package org.apache.james.metrics.logger;

import static org.apache.james.metrics.api.TimeMetric.ExecutionResult.DEFAULT_100_MS_THRESHOLD;

import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DefaultMetricFactory implements MetricFactory {

    public static final Logger LOGGER = LoggerFactory.getLogger(DefaultMetricFactory.class);

    @Override
    public Metric generate(String name) {
        return new DefaultMetric(name);
    }

    @Override
    public TimeMetric timer(String name) {
        return new DefaultTimeMetric(name);
    }

    @Override
    public <T> Publisher<T> decoratePublisherWithTimerMetric(String name, Publisher<T> publisher) {
        return Mono.fromCallable(() -> timer(name))
            .flatMapMany(timer ->  Flux.from(publisher)
                .doOnComplete(timer::stopAndPublish));
    }

    @Override
    public <T> Publisher<T> decoratePublisherWithTimerMetricLogP99(String name, Publisher<T> publisher) {
        return Mono.fromCallable(() -> timer(name))
            .flatMapMany(timer ->  Flux.from(publisher)
                .doOnComplete(() -> timer.stopAndPublish().logWhenExceedP99(DEFAULT_100_MS_THRESHOLD)));
    }
}
