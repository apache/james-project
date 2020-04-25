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

import static org.apache.james.modules.mailbox.ElasticSearchMailboxModule.ELASTICSEARCH_CONFIGURATION_NAME;

import java.io.FileNotFoundException;
import java.io.IOException;

import javax.inject.Inject;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.es.ElasticSearchConfiguration;
import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.backends.es.ReactorElasticSearchClient;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.quota.search.QuotaSearcher;
import org.apache.james.quota.search.elasticsearch.ElasticSearchQuotaConfiguration;
import org.apache.james.quota.search.elasticsearch.ElasticSearchQuotaSearcher;
import org.apache.james.quota.search.elasticsearch.QuotaSearchIndexCreationUtil;
import org.apache.james.quota.search.elasticsearch.UserRoutingKeyFactory;
import org.apache.james.quota.search.elasticsearch.events.ElasticSearchQuotaMailboxListener;
import org.apache.james.quota.search.elasticsearch.json.QuotaRatioToElasticSearchJson;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class ElasticSearchQuotaSearcherModule extends AbstractModule {

    static class ElasticSearchQuotaIndexCreator implements Startable {
        private final ElasticSearchConfiguration configuration;
        private final ElasticSearchQuotaConfiguration quotaConfiguration;
        private final ReactorElasticSearchClient client;

        @Inject
        ElasticSearchQuotaIndexCreator(ElasticSearchConfiguration configuration,
                                       ElasticSearchQuotaConfiguration quotaConfiguration,
                                       ReactorElasticSearchClient client) {
            this.configuration = configuration;
            this.quotaConfiguration = quotaConfiguration;
            this.client = client;
        }

        void createIndex() throws IOException {
            QuotaSearchIndexCreationUtil.prepareClient(client,
                quotaConfiguration.getReadAliasQuotaRatioName(),
                quotaConfiguration.getWriteAliasQuotaRatioName(),
                quotaConfiguration.getIndexQuotaRatioName(),
                configuration);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchQuotaSearcherModule.class);

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), MailboxListener.ReactiveGroupMailboxListener.class)
            .addBinding()
            .to(ElasticSearchQuotaMailboxListener.class);
    }

    @Provides
    @Singleton
    public QuotaSearcher provideSearcher(ReactorElasticSearchClient client, ElasticSearchQuotaConfiguration configuration) {
        return new ElasticSearchQuotaSearcher(client,
            configuration.getReadAliasQuotaRatioName());
    }

    @Provides
    @Singleton
    private ElasticSearchQuotaConfiguration getElasticSearchQuotaConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration(ELASTICSEARCH_CONFIGURATION_NAME);
            return ElasticSearchQuotaConfiguration.fromProperties(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + ELASTICSEARCH_CONFIGURATION_NAME + " configuration file. Providing a default ElasticSearchQuotaConfiguration");
            return ElasticSearchQuotaConfiguration.DEFAULT_CONFIGURATION;
        }
    }

    @Provides
    @Singleton
    public ElasticSearchQuotaMailboxListener provideListener(ReactorElasticSearchClient client,
                                                             ElasticSearchQuotaConfiguration configuration) {
        return new ElasticSearchQuotaMailboxListener(
            new ElasticSearchIndexer(client,
                configuration.getWriteAliasQuotaRatioName()),
                new QuotaRatioToElasticSearchJson(),
            new UserRoutingKeyFactory());
    }

    @ProvidesIntoSet
    InitializationOperation createIndex(ElasticSearchQuotaIndexCreator instance) {
        return InitilizationOperationBuilder
            .forClass(ElasticSearchQuotaIndexCreator.class)
            .init(instance::createIndex);
    }
}
