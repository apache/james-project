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
import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.metrics.es.ESMetricReporter;
import org.apache.james.metrics.es.ESReporterConfiguration;
import org.apache.james.utils.ConfigurationPerformer;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class ElasticSearchMetricReporterModule extends AbstractModule {
    private static final String ELASTICSEARCH_CONFIGURATION_NAME = "elasticsearch";
    private static final String ELASTICSEARCH_MASTER_HOST = "elasticsearch.masterHost";

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchMetricReporterModule.class);

    public static final boolean DEFAULT_DISABLE = false;
    public static final int DEFAULT_ES_HTTP_PORT = 9200;

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(ESMetricReporterStarter.class);
    }

    @Provides
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

    @Singleton
    public static class ESMetricReporterStarter implements ConfigurationPerformer {

        private final ESMetricReporter esMetricReporter;

        @Inject
        public ESMetricReporterStarter(ESMetricReporter esMetricReporter) {
            this.esMetricReporter = esMetricReporter;
        }

        @Override
        public void initModule() {
            esMetricReporter.start();
        }

        @Override
        public List<Class<? extends Startable>> forClasses() {
            return ImmutableList.of(ESMetricReporter.class);
        }
    }

}
