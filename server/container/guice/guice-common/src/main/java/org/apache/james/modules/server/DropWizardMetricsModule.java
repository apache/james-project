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

import org.apache.james.lifecycle.api.Startable;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.dropwizard.DropWizardGaugeRegistry;
import org.apache.james.metrics.dropwizard.DropWizardJVMMetrics;
import org.apache.james.metrics.dropwizard.DropWizardMetricFactory;
import org.apache.james.utils.InitialisationOperation;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class DropWizardMetricsModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(MetricRegistry.class).in(Scopes.SINGLETON);
        bind(DropWizardMetricFactory.class).in(Scopes.SINGLETON);
        bind(DropWizardGaugeRegistry.class).in(Scopes.SINGLETON);
        bind(DropWizardJVMMetrics.class).in(Scopes.SINGLETON);
        bind(MetricFactory.class).to(DropWizardMetricFactory.class);

        bind(GaugeRegistry.class).to(DropWizardGaugeRegistry.class);

        Multibinder.newSetBinder(binder(), InitialisationOperation.class).addBinding().to(DropWizardInitialisationOperation.class);
    }

    @Singleton
    public static class DropWizardInitialisationOperation implements InitialisationOperation, Startable {
        private final DropWizardMetricFactory dropWizardMetricFactory;
        private final DropWizardJVMMetrics dropWizardJVMMetrics;

        @Inject
        public DropWizardInitialisationOperation(DropWizardMetricFactory dropWizardMetricFactory, DropWizardJVMMetrics dropWizardJVMMetrics) {
            this.dropWizardMetricFactory = dropWizardMetricFactory;
            this.dropWizardJVMMetrics = dropWizardJVMMetrics;
        }

        @Override
        public void initModule() {
            dropWizardMetricFactory.start();
            dropWizardJVMMetrics.start();
        }

        @Override
        public Class<? extends Startable> forClass() {
            return DropWizardInitialisationOperation.class;
        }
    }
}
