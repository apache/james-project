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

import org.apache.james.metrics.es.ESReporterConfiguration;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class TestESMetricReporterModule extends AbstractModule {

    private static final String LOCALHOST = "localhost";
    private static final int DEFAULT_ES_HTTP_PORT = 9200;
    public static final String METRICS_INDEX = "metrics";

    @Override
    protected void configure() {
    }

    @Provides
    @Singleton
    public ESReporterConfiguration provideConfiguration() {
        return ESReporterConfiguration.builder()
            .enabled()
            .onHost(LOCALHOST, DEFAULT_ES_HTTP_PORT)
            .onIndex(METRICS_INDEX)
            .periodInSecond(1L)
            .build();
    }
}
