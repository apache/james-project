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

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.metrics.es.v7.ESMetricReporter;
import org.apache.james.metrics.es.v7.ESReporterConfiguration;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;

public class ElasticSearchMetricReporterModule extends AbstractModule {
    private static final String ELASTICSEARCH_CONFIGURATION_NAME = "elasticsearch";
    private static final String ELASTICSEARCH_MASTER_HOST = "elasticsearch.masterHost";

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchMetricReporterModule.class);

    public static final boolean DEFAULT_DISABLE = false;
    public static final int DEFAULT_ES_HTTP_PORT = 9200;

    @Provides
    @Singleton
    public ESReporterConfiguration provideConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration propertiesReader = propertiesProvider.getConfiguration(ELASTICSEARCH_CONFIGURATION_NAME);

            if (isMetricEnable(propertiesReader)) {
                return ESReporterConfiguration.builder()
                    .enabled()
                    .onHost(locateHost(propertiesReader),
                        propertiesReader.getInt("elasticsearch.http.port", DEFAULT_ES_HTTP_PORT))
                    .onIndex(propertiesReader.getString("elasticsearch.metrics.reports.index", null))
                    .periodInSecond(propertiesReader.getLong("elasticsearch.metrics.reports.period", null))
                    .build();
            }
        } catch (FileNotFoundException e) {
            LOGGER.info("Can not locate " + ELASTICSEARCH_CONFIGURATION_NAME + " configuration");
        }
        return ESReporterConfiguration.builder()
            .disabled()
            .build();
    }

    private String locateHost(Configuration propertiesReader) {
        return propertiesReader.getString("elasticsearch.http.host",
            propertiesReader.getString(ELASTICSEARCH_MASTER_HOST));
    }

    private boolean isMetricEnable(Configuration propertiesReader) {
        return propertiesReader.getBoolean("elasticsearch.metrics.reports.enabled", DEFAULT_DISABLE);
    }

    @ProvidesIntoSet
    InitializationOperation startReporter(ESMetricReporter instance) {
        return InitilizationOperationBuilder
            .forClass(ESMetricReporter.class)
            .init(instance::start);
    }
}
