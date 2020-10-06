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

import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.dropwizard.DropWizardGaugeRegistry;
import org.apache.james.metrics.dropwizard.DropWizardJVMMetrics;
import org.apache.james.metrics.dropwizard.DropWizardMetricFactory;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.ProvidesIntoSet;

public class DropWizardMetricsModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new LoggingMetricsModule());
        bind(DropWizardMetricFactory.class).in(Scopes.SINGLETON);
        bind(DropWizardGaugeRegistry.class).in(Scopes.SINGLETON);
        bind(DropWizardJVMMetrics.class).in(Scopes.SINGLETON);
        bind(MetricFactory.class).to(DropWizardMetricFactory.class);

        bind(MetricRegistry.class).toInstance(new MetricRegistry());
        bind(GaugeRegistry.class).to(DropWizardGaugeRegistry.class);
    }

    @ProvidesIntoSet
    InitializationOperation startMetricFactory(DropWizardMetricFactory instance) {
        return InitilizationOperationBuilder
            .forClass(DropWizardMetricFactory.class)
            .init(instance::start);
    }

    @ProvidesIntoSet
    InitializationOperation startJVMMetrics(DropWizardJVMMetrics instance) {
        return InitilizationOperationBuilder
            .forClass(DropWizardJVMMetrics.class)
            .init(instance::start);
    }
}
