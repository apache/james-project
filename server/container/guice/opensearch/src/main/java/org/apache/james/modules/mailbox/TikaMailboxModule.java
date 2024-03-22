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

package org.apache.james.modules.mailbox;

import java.io.FileNotFoundException;
import java.net.URISyntaxException;

import jakarta.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.extractor.JsoupTextExtractor;
import org.apache.james.mailbox.tika.CachingTextExtractor;
import org.apache.james.mailbox.tika.ContentTypeFilteringTextExtractor;
import org.apache.james.mailbox.tika.TikaConfiguration;
import org.apache.james.mailbox.tika.TikaHttpClient;
import org.apache.james.mailbox.tika.TikaHttpClientImpl;
import org.apache.james.mailbox.tika.TikaTextExtractor;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;

public class TikaMailboxModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(TikaMailboxModule.class);

    private static final String TIKA_CONFIGURATION_NAME = "tika";

    @Override
    protected void configure() {
        bind(TikaTextExtractor.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    protected TikaHttpClient provideTikaHttpClient(TikaConfiguration tikaConfiguration) throws URISyntaxException {
        return new TikaHttpClientImpl(tikaConfiguration);
    }

    @Provides
    @Singleton
    private TikaConfiguration getTikaConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration(TIKA_CONFIGURATION_NAME);

            return TikaConfigurationReader.readTikaConfiguration(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find {} configuration file. Disabling Tika.", TIKA_CONFIGURATION_NAME);
            return TikaConfiguration.builder()
                .disabled()
                .build();
        }
    }

    @Provides
    @Singleton
    private TextExtractor provideTextExtractor(TikaTextExtractor textExtractor, TikaConfiguration configuration,
                                               MetricFactory metricFactory, GaugeRegistry gaugeRegistry) {
        if (configuration.isEnabled() && configuration.isCacheEnabled()) {
            LOGGER.info("Tika cache has been enabled.");
            return new ContentTypeFilteringTextExtractor(
                new CachingTextExtractor(
                    textExtractor,
                    configuration.getCacheEvictionPeriod(),
                    configuration.getCacheWeightInBytes(),
                    metricFactory,
                    gaugeRegistry), configuration.getContentTypeBlacklist());
        }
        if (configuration.isEnabled()) {
            return new ContentTypeFilteringTextExtractor(textExtractor, configuration.getContentTypeBlacklist());
        }
        LOGGER.info("Tika text extraction has been disabled." +
            " Using JsoupTextExtractor instead. " +
            "No complex extraction will be done.");
        return new JsoupTextExtractor();
    }

}
