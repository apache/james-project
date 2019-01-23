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
import java.util.concurrent.ExecutorService;

import javax.inject.Named;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.quota.search.QuotaSearcher;
import org.apache.james.quota.search.elasticsearch.ElasticSearchQuotaConfiguration;
import org.apache.james.quota.search.elasticsearch.ElasticSearchQuotaSearcher;
import org.apache.james.quota.search.elasticsearch.QuotaRatioElasticSearchConstants;
import org.apache.james.quota.search.elasticsearch.events.ElasticSearchQuotaMailboxListener;
import org.apache.james.quota.search.elasticsearch.json.QuotaRatioToElasticSearchJson;
import org.apache.james.utils.PropertiesProvider;
import org.elasticsearch.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class ElasticSearchQuotaSearcherModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchQuotaSearcherModule.class);

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), MailboxListener.GroupMailboxListener.class)
            .addBinding()
            .to(ElasticSearchQuotaMailboxListener.class);
    }

    @Provides
    @Singleton
    public QuotaSearcher provideSearcher(Client client, ElasticSearchQuotaConfiguration configuration) {
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
    public ElasticSearchQuotaMailboxListener provideListener(Client client,
                                                             @Named("AsyncExecutor") ExecutorService executor,
                                                             ElasticSearchQuotaConfiguration configuration) {
        return new ElasticSearchQuotaMailboxListener(
            new ElasticSearchIndexer(client,
                executor,
                configuration.getWriteAliasQuotaRatioName(),
                QuotaRatioElasticSearchConstants.QUOTA_RATIO_TYPE),
                new QuotaRatioToElasticSearchJson());
    }
}
