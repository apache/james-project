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

import javax.annotation.PreDestroy;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;

public class DropWizardMetricFactory implements MetricFactory, Configurable {

    private final MetricRegistry metricRegistry;
    private final JmxReporter jmxReporter;

    public DropWizardMetricFactory() {
        this.metricRegistry = new MetricRegistry();
        this.jmxReporter = JmxReporter.forRegistry(metricRegistry)
            .build();
    }

    @Override
    public Metric generate(String name) {
        return new DropWizardMetric(metricRegistry.counter(name));
    }

    @Override
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        jmxReporter.start();
    }

    @PreDestroy
    public void stop() {
        jmxReporter.stop();
    }

}
