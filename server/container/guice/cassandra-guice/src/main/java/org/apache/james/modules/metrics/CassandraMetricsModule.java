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

package org.apache.james.modules.metrics;

import java.util.List;

import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.utils.ConfigurationPerformer;

import com.codahale.metrics.MetricRegistry;
import com.datastax.driver.core.Session;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class CassandraMetricsModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(CassandraMetricsInjector.class)
            .in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class)
            .addBinding()
            .to(CassandraMetricsInjector.class);
    }

    public static class CassandraMetricsInjector implements ConfigurationPerformer {

        private final MetricRegistry metricRegistry;
        private final Session session;

        @Inject
        public CassandraMetricsInjector(MetricRegistry metricRegistry, Session session) {
            this.metricRegistry = metricRegistry;
            this.session = session;
        }

        @Override
        public void initModule() {
            metricRegistry.registerAll(
                session.getCluster()
                    .getMetrics()
                    .getRegistry());
        }

        @Override
        public List<Class<? extends Configurable>> forClasses() {
            return ImmutableList.of();
        }
    }
}
