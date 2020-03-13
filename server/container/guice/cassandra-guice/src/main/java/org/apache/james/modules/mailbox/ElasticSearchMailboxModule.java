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
import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.es.ElasticSearchConfiguration;
import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.backends.es.ReactorElasticSearchClient;
import org.apache.james.backends.es.RoutingKey;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.mailbox.elasticsearch.ElasticSearchMailboxConfiguration;
import org.apache.james.mailbox.elasticsearch.IndexAttachments;
import org.apache.james.mailbox.elasticsearch.MailboxElasticSearchConstants;
import org.apache.james.mailbox.elasticsearch.MailboxIdRoutingKeyFactory;
import org.apache.james.mailbox.elasticsearch.MailboxIndexCreationUtil;
import org.apache.james.mailbox.elasticsearch.events.ElasticSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.elasticsearch.query.QueryConverter;
import org.apache.james.mailbox.elasticsearch.search.ElasticSearchSearcher;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class ElasticSearchMailboxModule extends AbstractModule {

    static class MailboxIndexCreator implements Startable {

        private final ElasticSearchConfiguration configuration;
        private final ElasticSearchMailboxConfiguration mailboxConfiguration;
        private final ReactorElasticSearchClient client;

        @Inject
        MailboxIndexCreator(ElasticSearchConfiguration configuration,
                            ElasticSearchMailboxConfiguration mailboxConfiguration,
                            ReactorElasticSearchClient client) {
            this.configuration = configuration;
            this.mailboxConfiguration = mailboxConfiguration;
            this.client = client;
        }

        void createIndex() throws IOException {
            MailboxIndexCreationUtil.prepareClient(client,
                mailboxConfiguration.getReadAliasMailboxName(),
                mailboxConfiguration.getWriteAliasMailboxName(),
                mailboxConfiguration.getIndexMailboxName(),
                configuration);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchMailboxModule.class);

    public static final String ELASTICSEARCH_CONFIGURATION_NAME = "elasticsearch";

    @Override
    protected void configure() {
        install(new ElasticSearchQuotaSearcherModule());

        bind(ElasticSearchListeningMessageSearchIndex.class).in(Scopes.SINGLETON);
        bind(MessageSearchIndex.class).to(ElasticSearchListeningMessageSearchIndex.class);
        bind(ListeningMessageSearchIndex.class).to(ElasticSearchListeningMessageSearchIndex.class);

        bind(new TypeLiteral<RoutingKey.Factory<MailboxId>>() {}).to(MailboxIdRoutingKeyFactory.class);

        Multibinder.newSetBinder(binder(), MailboxListener.GroupMailboxListener.class)
            .addBinding()
            .to(ElasticSearchListeningMessageSearchIndex.class);

        Multibinder.newSetBinder(binder(), StartUpCheck.class)
            .addBinding()
            .to(ElasticSearchStartUpCheck.class);
    }

    @Provides
    @Singleton
    @Named(MailboxElasticSearchConstants.InjectionNames.MAILBOX)
    private ElasticSearchIndexer createMailboxElasticSearchIndexer(ReactorElasticSearchClient client,
                                                                   ElasticSearchMailboxConfiguration configuration) {
        return new ElasticSearchIndexer(
            client,
            configuration.getWriteAliasMailboxName());
    }

    @Provides
    @Singleton
    private ElasticSearchSearcher createMailboxElasticSearchSearcher(ReactorElasticSearchClient client,
                                                                     QueryConverter queryConverter,
                                                                     MailboxId.Factory mailboxIdFactory,
                                                                     MessageId.Factory messageIdFactory,
                                                                     ElasticSearchMailboxConfiguration configuration,
                                                                     RoutingKey.Factory<MailboxId> routingKeyFactory) {
        return new ElasticSearchSearcher(
            client,
            queryConverter,
            DEFAULT_SEARCH_SIZE,
            mailboxIdFactory,
            messageIdFactory,
            configuration.getReadAliasMailboxName(), routingKeyFactory);
    }

    @Provides
    @Singleton
    private ElasticSearchConfiguration getElasticSearchConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration(ELASTICSEARCH_CONFIGURATION_NAME);
            return ElasticSearchConfiguration.fromProperties(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + ELASTICSEARCH_CONFIGURATION_NAME + " configuration file. Using {}:{} as contact point",
                ElasticSearchConfiguration.LOCALHOST, ElasticSearchConfiguration.DEFAULT_PORT);
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
    public IndexAttachments provideIndexAttachments(ElasticSearchMailboxConfiguration configuration) {
        return configuration.getIndexAttachment();
    }

    @ProvidesIntoSet
    InitializationOperation createIndex(MailboxIndexCreator instance) {
        return InitilizationOperationBuilder
            .forClass(MailboxIndexCreator.class)
            .init(instance::createIndex);
    }

}
