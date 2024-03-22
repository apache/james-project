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

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.apache.james.metrics.api.Gauge;
import org.apache.james.metrics.api.GaugeRegistry;

import com.codahale.metrics.DefaultSettableGauge;
import com.codahale.metrics.MetricRegistry;

public class DropWizardGaugeRegistry implements GaugeRegistry {
    private final MetricRegistry metricRegistry;

    @Inject
    public DropWizardGaugeRegistry(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    @Override
    public <T> GaugeRegistry register(String name, Gauge<T> gauge) {
        metricRegistry.gauge(name, () -> gauge::get);
        return this;
    }

    @PreDestroy
    public void shutDown() {
        metricRegistry.getGauges().keySet().forEach(metricRegistry::remove);
    }

    @Override
    public <T> SettableGauge<T> settableGauge(String name) {
        DefaultSettableGauge<T> gauge = new DefaultSettableGauge<>();
        metricRegistry.register(name, gauge);
        return gauge::setValue;
    }
}
