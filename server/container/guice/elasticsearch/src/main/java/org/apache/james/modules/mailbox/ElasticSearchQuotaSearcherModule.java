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
import org.apache.james.backends.opensearch.ElasticSearchConfiguration;
import org.apache.james.backends.opensearch.ElasticSearchIndexer;
import org.apache.james.backends.opensearch.ReactorElasticSearchClient;
import org.apache.james.events.EventListener;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.quota.search.QuotaSearcher;
import org.apache.james.quota.search.opensearch.OpenSearchQuotaConfiguration;
import org.apache.james.quota.search.opensearch.OpenSearchQuotaSearcher;
import org.apache.james.quota.search.opensearch.QuotaSearchIndexCreationUtil;
import org.apache.james.quota.search.opensearch.UserRoutingKeyFactory;
import org.apache.james.quota.search.opensearch.events.OpenSearchQuotaMailboxListener;
import org.apache.james.quota.search.opensearch.json.QuotaRatioToOpenSearchJson;
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
        private final OpenSearchQuotaConfiguration quotaConfiguration;
        private final ReactorElasticSearchClient client;

        @Inject
        ElasticSearchQuotaIndexCreator(ElasticSearchConfiguration configuration,
                                       OpenSearchQuotaConfiguration quotaConfiguration,
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
        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class)
            .addBinding()
            .to(OpenSearchQuotaMailboxListener.class);
    }

    @Provides
    @Singleton
    public QuotaSearcher provideSearcher(ReactorElasticSearchClient client, OpenSearchQuotaConfiguration configuration) {
        return new OpenSearchQuotaSearcher(client,
            configuration.getReadAliasQuotaRatioName());
    }

    @Provides
    @Singleton
    private OpenSearchQuotaConfiguration getElasticSearchQuotaConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration(ELASTICSEARCH_CONFIGURATION_NAME);
            return OpenSearchQuotaConfiguration.fromProperties(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + ELASTICSEARCH_CONFIGURATION_NAME + " configuration file. Providing a default ElasticSearchQuotaConfiguration");
            return OpenSearchQuotaConfiguration.DEFAULT_CONFIGURATION;
        }
    }

    @Provides
    @Singleton
    public OpenSearchQuotaMailboxListener provideListener(ReactorElasticSearchClient client,
                                                          OpenSearchQuotaConfiguration configuration,
                                                          QuotaRootResolver quotaRootResolver) {
        return new OpenSearchQuotaMailboxListener(
            new ElasticSearchIndexer(client,
                configuration.getWriteAliasQuotaRatioName()),
                new QuotaRatioToOpenSearchJson(quotaRootResolver),
            new UserRoutingKeyFactory(), quotaRootResolver);
    }

    @ProvidesIntoSet
    InitializationOperation createIndex(ElasticSearchQuotaIndexCreator instance) {
        return InitilizationOperationBuilder
            .forClass(ElasticSearchQuotaIndexCreator.class)
            .init(instance::createIndex);
    }
}
