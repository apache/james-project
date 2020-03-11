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

import static org.apache.james.metrics.api.TimeMetric.ExecutionResult.DEFAULT_100_MS_THRESHOLD;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.james.lifecycle.api.Startable;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.reactivestreams.Publisher;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jmx.JmxReporter;

import reactor.core.publisher.Flux;

public class DropWizardMetricFactory implements MetricFactory, Startable {

    private final MetricRegistry metricRegistry;
    private final JmxReporter jmxReporter;

    @Inject
    public DropWizardMetricFactory(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
        this.jmxReporter = JmxReporter.forRegistry(metricRegistry)
            .build();
    }

    @Override
    public Metric generate(String name) {
        return new DropWizardMetric(metricRegistry.counter(name), name);
    }

    @Override
    public TimeMetric timer(String name) {
        return new DropWizardTimeMetric(name, metricRegistry.timer(name));
    }

    @Override
    public <T> Publisher<T> runPublishingTimerMetric(String name, Publisher<T> publisher) {
        TimeMetric timer = timer(name);
        return Flux.from(publisher).doOnComplete(timer::stopAndPublish);
    }

    @Override
    public <T> Publisher<T> runPublishingTimerMetricLogP99(String name, Publisher<T> publisher) {
        TimeMetric timer = timer(name);
        return Flux.from(publisher)
            .doOnComplete(() -> timer.stopAndPublish().logWhenExceedP99(DEFAULT_100_MS_THRESHOLD));
    }

    @PostConstruct
    public void start() {
        jmxReporter.start();
    }

    @PreDestroy
    public void stop() {
        jmxReporter.stop();
    }

}
