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

import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.dropwizard.DropWizardMetricFactory;
import org.apache.james.utils.ConfigurationPerformer;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class DropWizardMetricsModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(DropWizardMetricFactory.class).in(Scopes.SINGLETON);
        bind(MetricFactory.class).to(DropWizardMetricFactory.class);

        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(DropWizardConfigurationPerformer.class);
    }

    @Singleton
    public static class DropWizardConfigurationPerformer implements ConfigurationPerformer {
        public static final HierarchicalConfiguration NO_CONFIGURATION = null;

        private final DropWizardMetricFactory metricFactory;

        @Inject
        public DropWizardConfigurationPerformer(DropWizardMetricFactory metricFactory) {
            this.metricFactory = metricFactory;
        }

        @Override
        public void initModule() {
            try {
                metricFactory.configure(NO_CONFIGURATION);
            } catch (ConfigurationException e) {
                throw Throwables.propagate(e);
            }
        }

        @Override
        public List<Class<? extends Configurable>> forClasses() {
            return ImmutableList.of(DropWizardMetricFactory.class);
        }
    }

}
