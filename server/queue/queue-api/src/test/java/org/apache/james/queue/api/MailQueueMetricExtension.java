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

import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.mockito.Mockito;

public class MailQueueMetricExtension implements BeforeEachCallback, ParameterResolver {

    public class MailQueueMetricTestSystem {
        private final GaugeRegistry spyGaugeRegistry;
        private final RecordingMetricFactory metricFactory;

        public MailQueueMetricTestSystem() {
            spyGaugeRegistry = Mockito.spy(new NoopGaugeRegistry());
            metricFactory = new RecordingMetricFactory();
        }

        public GaugeRegistry getSpyGaugeRegistry() {
            return spyGaugeRegistry;
        }

        public RecordingMetricFactory getMetricFactory() {
            return metricFactory;
        }
    }

    private MailQueueMetricTestSystem testSystem;

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        testSystem = new MailQueueMetricTestSystem();
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
