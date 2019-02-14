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

import static org.apache.james.mailbox.elasticsearch.search.ElasticSearchSearcher.DEFAULT_SEARCH_SIZE;

import java.io.FileNotFoundException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.backends.es.ClientProviderImpl;
import org.apache.james.backends.es.ElasticSearchConfiguration;
import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.mailbox.elasticsearch.ElasticSearchMailboxConfiguration;
import org.apache.james.mailbox.elasticsearch.IndexAttachments;
import org.apache.james.mailbox.elasticsearch.MailboxElasticSearchConstants;
import org.apache.james.mailbox.elasticsearch.MailboxIndexCreationUtil;
import org.apache.james.mailbox.elasticsearch.events.ElasticSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.elasticsearch.query.QueryConverter;
import org.apache.james.mailbox.elasticsearch.search.ElasticSearchSearcher;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.quota.search.elasticsearch.ElasticSearchQuotaConfiguration;
import org.apache.james.quota.search.elasticsearch.QuotaSearchIndexCreationUtil;
import org.apache.james.utils.PropertiesProvider;
import org.elasticsearch.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ElasticSearchMailboxModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchMailboxModule.class);

    public static final String ELASTICSEARCH_CONFIGURATION_NAME = "elasticsearch";

    @Override
    protected void configure() {
        install(new ElasticSearchQuotaSearcherModule());

        bind(ElasticSearchListeningMessageSearchIndex.class).in(Scopes.SINGLETON);
        bind(MessageSearchIndex.class).to(ElasticSearchListeningMessageSearchIndex.class);
        bind(ListeningMessageSearchIndex.class).to(ElasticSearchListeningMessageSearchIndex.class);

        Multibinder.newSetBinder(binder(), MailboxListener.GroupMailboxListener.class)
            .addBinding()
            .to(ElasticSearchListeningMessageSearchIndex.class);
    }

    @Provides
    @Singleton
    @Named(MailboxElasticSearchConstants.InjectionNames.MAILBOX)
    private ElasticSearchIndexer createMailboxElasticSearchIndexer(Client client,
                                                                   @Named("AsyncExecutor") ExecutorService executor,
                                                                   ElasticSearchMailboxConfiguration configuration) {
        return new ElasticSearchIndexer(
            client,
            executor,
            configuration.getWriteAliasMailboxName(),
            MailboxElasticSearchConstants.MESSAGE_TYPE);
    }

    @Provides
    @Singleton
    private ElasticSearchSearcher createMailboxElasticSearchSearcher(Client client,
                                                                     QueryConverter queryConverter,
                                                                     MailboxId.Factory mailboxIdFactory,
                                                                     MessageId.Factory messageIdFactory,
                                                                     ElasticSearchMailboxConfiguration configuration) {
        return new ElasticSearchSearcher(
            client,
            queryConverter,
            DEFAULT_SEARCH_SIZE,
            mailboxIdFactory,
            messageIdFactory,
            configuration.getReadAliasMailboxName(),
            MailboxElasticSearchConstants.MESSAGE_TYPE);
    }

    @Provides
    @Singleton
    private ElasticSearchConfiguration getElasticSearchConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration(ELASTICSEARCH_CONFIGURATION_NAME);
            return ElasticSearchConfiguration.fromProperties(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + ELASTICSEARCH_CONFIGURATION_NAME + " configuration file. Using 127.0.0.1:9300 as contact point");
            return ElasticSearchConfiguration.DEFAULT_CONFIGURATION;
        }
    }

    @Provides
    @Singleton
    private ElasticSearchMailboxConfiguration getElasticSearchMailboxConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration(ELASTICSEARCH_CONFIGURATION_NAME);
            return ElasticSearchMailboxConfiguration.fromProperties(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + ELASTICSEARCH_CONFIGURATION_NAME + " configuration file. Providing a default ElasticSearchMailboxConfiguration");
            return ElasticSearchMailboxConfiguration.DEFAULT_CONFIGURATION;
        }
    }

    @Provides
    @Singleton
    protected Client provideClient(ElasticSearchConfiguration configuration,
                                   ElasticSearchMailboxConfiguration mailboxConfiguration,
                                   ElasticSearchQuotaConfiguration quotaConfiguration) {

        Duration waitDelay = Duration.ofMillis(configuration.getMinDelay());
        return Mono.fromCallable(() -> connectToCluster(configuration, mailboxConfiguration, quotaConfiguration))
            .doOnError(e -> LOGGER.warn("Error establishing ElasticSearch connection. Next retry scheduled in {} ms", waitDelay, e))
            .retryBackoff(configuration.getMaxRetries(), waitDelay, waitDelay)
            .publishOn(Schedulers.elastic())
            .block();
    }

    private Client connectToCluster(ElasticSearchConfiguration configuration,
                                    ElasticSearchMailboxConfiguration mailboxConfiguration,
                                    ElasticSearchQuotaConfiguration quotaConfiguration) {
        LOGGER.info("Trying to connect to ElasticSearch service at {}", LocalDateTime.now());

        Client client = ClientProviderImpl.fromHosts(configuration.getHosts(), configuration.getClusterName()).get();

        MailboxIndexCreationUtil.prepareClient(client,
            mailboxConfiguration.getReadAliasMailboxName(),
            mailboxConfiguration.getWriteAliasMailboxName(),
            mailboxConfiguration.getIndexMailboxName(),
            configuration);

        QuotaSearchIndexCreationUtil.prepareClient(client,
            quotaConfiguration.getReadAliasQuotaRatioName(),
            quotaConfiguration.getWriteAliasQuotaRatioName(),
            quotaConfiguration.getIndexQuotaRatioName(),
            configuration);

        return client;
    }

    @Provides
    @Singleton
    public IndexAttachments provideIndexAttachments(ElasticSearchMailboxConfiguration configuration) {
        return configuration.getIndexAttachment();
    }

}
