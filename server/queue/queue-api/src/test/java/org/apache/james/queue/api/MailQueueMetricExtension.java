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

package org.apache.james.queue.api;

import static org.apache.james.queue.api.MailQueue.DEQUEUED_METRIC_NAME_PREFIX;
import static org.apache.james.queue.api.MailQueue.DEQUEUED_TIMER_METRIC_NAME_PREFIX;
import static org.apache.james.queue.api.MailQueue.ENQUEUED_METRIC_NAME_PREFIX;
import static org.apache.james.queue.api.MailQueue.ENQUEUED_TIMER_METRIC_NAME_PREFIX;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.mockito.Mockito;

public class MailQueueMetricExtension implements BeforeEachCallback, ParameterResolver {

    public class MailQueueMetricTestSystem {
        private final Metric spyEnqueuedMailsMetric;
        private final Metric spyDequeuedMailsMetric;
        private final TimeMetric spyEnqueuedMailsTimeMetric;
        private final TimeMetric spyDequeuedMailsTimeMetric;
        private final GaugeRegistry spyGaugeRegistry;
        private final MetricFactory spyMetricFactory;

        public MailQueueMetricTestSystem() {
            spyEnqueuedMailsMetric = spy(new NoopMetricFactory.NoopMetric());
            spyDequeuedMailsMetric = spy(new NoopMetricFactory.NoopMetric());
            spyEnqueuedMailsTimeMetric = spy(new NoopMetricFactory.NoopTimeMetric());
            spyDequeuedMailsTimeMetric = spy(new NoopMetricFactory.NoopTimeMetric());
            spyGaugeRegistry = Mockito.spy(new NoopGaugeRegistry());
            spyMetricFactory = Mockito.spy(new NoopMetricFactory());
        }

        public Metric getSpyEnqueuedMailsMetric() {
            return spyEnqueuedMailsMetric;
        }

        public Metric getSpyDequeuedMailsMetric() {
            return spyDequeuedMailsMetric;
        }

        public GaugeRegistry getSpyGaugeRegistry() {
            return spyGaugeRegistry;
        }

        public MetricFactory getSpyMetricFactory() {
            return spyMetricFactory;
        }

        public TimeMetric getSpyEnqueuedMailsTimeMetric() {
            return spyEnqueuedMailsTimeMetric;
        }

        public TimeMetric getSpyDequeuedMailsTimeMetric() {
            return spyDequeuedMailsTimeMetric;
        }
    }

    private MailQueueMetricTestSystem testSystem;

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        testSystem = new MailQueueMetricTestSystem();

        when(testSystem.spyMetricFactory.generate(startsWith(ENQUEUED_METRIC_NAME_PREFIX)))
            .thenReturn(testSystem.spyEnqueuedMailsMetric);
        when(testSystem.spyMetricFactory.generate(startsWith(DEQUEUED_METRIC_NAME_PREFIX)))
            .thenReturn(testSystem.spyDequeuedMailsMetric);

        when(testSystem.spyMetricFactory.timer(startsWith(ENQUEUED_TIMER_METRIC_NAME_PREFIX)))
            .thenReturn(testSystem.spyEnqueuedMailsTimeMetric);
        when(testSystem.spyMetricFactory.timer(startsWith(DEQUEUED_TIMER_METRIC_NAME_PREFIX)))
            .thenReturn(testSystem.spyDequeuedMailsTimeMetric);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == MailQueueMetricTestSystem.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return testSystem;
    }
}
