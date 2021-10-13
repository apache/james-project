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

package org.apache.james.modules;

import javax.inject.Singleton;

import org.apache.james.metrics.es.v7.ESReporterConfiguration;
import org.apache.james.util.Host;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class TestDockerESMetricReporterModule extends AbstractModule {

    public static final String METRICS_INDEX = "metrics";

    private final Host esHttpHost;

    public TestDockerESMetricReporterModule(Host esHttpHost) {
        this.esHttpHost = esHttpHost;
    }

    @Override
    protected void configure() {
    }

    @Provides
    @Singleton
    public ESReporterConfiguration provideConfiguration() {
        return ESReporterConfiguration.builder()
            .enabled()
            .onHost(esHttpHost.getHostName(), esHttpHost.getPort())
            .onIndex(METRICS_INDEX)
            .periodInSecond(1L)
            .build();
    }
}
