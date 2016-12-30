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
import java.util.concurrent.ExecutionException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang.NotImplementedException;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.metrics.dropwizard.DropWizardMetricFactory;
import org.apache.james.metrics.dropwizard.ESMetricReporter;
import org.apache.james.metrics.dropwizard.ESReporterConfiguration;
import org.apache.james.modules.mailbox.ElasticSearchMailboxModule;
import org.apache.james.utils.ConfigurationPerformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class ESMetricReporterModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(ESMetricReporterModule.class);
    public static final boolean DEFAULT_DISABLE = false;
    public static final int DEFAULT_ES_HTTP_PORT = 9200;

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(ESMetricReporterStarter.class);
    }

    @Provides
    public ESReporterConfiguration provideConfiguration(FileSystem fileSystem) throws ConfigurationException {
        try {
            PropertiesConfiguration propertiesReader = getPropertiesConfiguration(fileSystem);

            if (isMetricEnable(propertiesReader)) {
                return ESReporterConfiguration.enabled(
                    propertiesReader.getString(ElasticSearchMailboxModule.ELASTICSEARCH_MASTER_HOST),
                    propertiesReader.getInt("elasticsearch.http.port", DEFAULT_ES_HTTP_PORT),
                    Optional.fromNullable(propertiesReader.getString("elasticsearch.metrics.reports.index", null)),
                    Optional.fromNullable(propertiesReader.getLong("elasticsearch.metrics.reports.period", null)));
            }
        } catch (FileNotFoundException e) {
            LOGGER.info("Can not locate " + ElasticSearchMailboxModule.ES_CONFIG_FILE);
        }
        return ESReporterConfiguration.disabled();
    }

    private boolean isMetricEnable(PropertiesConfiguration propertiesReader) {
        return propertiesReader.getBoolean("elasticsearch.metrics.reports.enabled", DEFAULT_DISABLE);
    }

    private PropertiesConfiguration getPropertiesConfiguration(FileSystem fileSystem) throws ConfigurationException, FileNotFoundException {
        return new PropertiesConfiguration(
                    fileSystem.getFile(ElasticSearchMailboxModule.ES_CONFIG_FILE));
    }

    @Provides
    public ESMetricReporter provideReporter(DropWizardMetricFactory metricFactory, ESReporterConfiguration configuration) throws ConfigurationException, ExecutionException, InterruptedException {
        return metricFactory.provideEsReporter(configuration);
    }

    @Singleton
    public static class ESMetricReporterStarter implements ConfigurationPerformer, Configurable {

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
        public List<Class<? extends Configurable>> forClasses() {
            return ImmutableList.of(ESMetricReporterStarter.class);
        }

        @Override
        public void configure(HierarchicalConfiguration config) throws ConfigurationException {
            throw new NotImplementedException();
        }
    }

}
