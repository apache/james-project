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

package org.apache.james.examples;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.apache.james.utils.UserDefinedStartable;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;

public class GraphiteExporter implements UserDefinedStartable {
    private final MetricRegistry metricRegistry;

    @Inject
    public GraphiteExporter(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    public void start() {
        Graphite graphite = new Graphite(new InetSocketAddress("graphite", 2003));
        GraphiteReporter reporter = GraphiteReporter.forRegistry(metricRegistry)
            .convertRatesTo(TimeUnit.SECONDS)
            .convertDurationsTo(TimeUnit.MILLISECONDS)
            .filter(MetricFilter.ALL)
            .build(graphite);
        reporter.start(1, TimeUnit.SECONDS);
    }
}
