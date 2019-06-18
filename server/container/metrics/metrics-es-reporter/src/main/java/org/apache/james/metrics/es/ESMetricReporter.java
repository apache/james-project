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

package org.apache.james.metrics.es;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.james.lifecycle.api.Startable;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.linagora.elasticsearch.metrics.ElasticsearchReporter;

public class ESMetricReporter implements Startable {

    private final Optional<ElasticsearchReporter> reporter;
    private final ESReporterConfiguration esReporterConfiguration;

    @Inject
    public ESMetricReporter(ESReporterConfiguration esReporterConfiguration, MetricRegistry registry) {
        this.reporter = getReporter(esReporterConfiguration, registry);
        this.esReporterConfiguration = esReporterConfiguration;
    }

    private Optional<ElasticsearchReporter> getReporter(ESReporterConfiguration esReporterConfiguration, MetricRegistry registry) {
        if (esReporterConfiguration.isEnabled()) {
            try {
                return Optional.of(ElasticsearchReporter.forRegistry(registry)
                    .hosts(esReporterConfiguration.getHostWithPort())
                    .index(esReporterConfiguration.getIndex())
                    .build());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }   
        return Optional.empty();
    }

    public void start() {
        reporter.ifPresent(elasticsearchReporter ->
            elasticsearchReporter.start(esReporterConfiguration.getPeriodInSecond(), TimeUnit.SECONDS));
    }

    @PreDestroy
    public void stop() {
        reporter.ifPresent(ScheduledReporter::stop);
    }
}
