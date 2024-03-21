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

import static org.apache.james.mailbox.opensearch.search.OpenSearchSearcher.DEFAULT_SEARCH_SIZE;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.james.backends.opensearch.OpenSearchConfiguration;
import org.apache.james.backends.opensearch.OpenSearchIndexer;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.apache.james.backends.opensearch.RoutingKey;
import org.apache.james.events.EventListener;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.opensearch.IndexAttachments;
import org.apache.james.mailbox.opensearch.IndexHeaders;
import org.apache.james.mailbox.opensearch.MailboxIdRoutingKeyFactory;
import org.apache.james.mailbox.opensearch.MailboxIndexCreationUtil;
import org.apache.james.mailbox.opensearch.MailboxOpenSearchConstants;
import org.apache.james.mailbox.opensearch.OpenSearchMailboxConfiguration;
import org.apache.james.mailbox.opensearch.events.OpenSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.opensearch.query.QueryConverter;
import org.apache.james.mailbox.opensearch.search.OpenSearchSearcher;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class OpenSearchMailboxModule extends AbstractModule {

    static class MailboxIndexCreator implements Startable {

        private final OpenSearchConfiguration configuration;
        private final OpenSearchMailboxConfiguration mailboxConfiguration;
        private final ReactorOpenSearchClient client;

        @Inject
        MailboxIndexCreator(OpenSearchConfiguration configuration,
                            OpenSearchMailboxConfiguration mailboxConfiguration,
                            ReactorOpenSearchClient client) {
            this.configuration = configuration;
            this.mailboxConfiguration = mailboxConfiguration;
            this.client = client;
        }

        void createIndex() {
            MailboxIndexCreationUtil.prepareClient(client,
                mailboxConfiguration.getReadAliasMailboxName(),
                mailboxConfiguration.getWriteAliasMailboxName(),
                mailboxConfiguration.getIndexMailboxName(),
                configuration);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchMailboxModule.class);

    public static final String OPENSEARCH_CONFIGURATION_NAME = "opensearch";

    @Override
    protected void configure() {
        install(new OpenSearchMailboxConfigurationModule());
        install(new OpenSearchQuotaSearcherModule());

        bind(OpenSearchListeningMessageSearchIndex.class).in(Scopes.SINGLETON);
        bind(MessageSearchIndex.class).to(OpenSearchListeningMessageSearchIndex.class);
        bind(ListeningMessageSearchIndex.class).to(OpenSearchListeningMessageSearchIndex.class);

        bind(new TypeLiteral<RoutingKey.Factory<MailboxId>>() {}).to(MailboxIdRoutingKeyFactory.class);

        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class)
            .addBinding()
            .to(OpenSearchListeningMessageSearchIndex.class);

        Multibinder.newSetBinder(binder(), StartUpCheck.class)
            .addBinding()
            .to(OpenSearchStartUpCheck.class);
    }

    @Provides
    @Singleton
    private OpenSearchSearcher createMailboxOpenSearchSearcher(ReactorOpenSearchClient client,
                                                               QueryConverter queryConverter,
                                                               OpenSearchMailboxConfiguration configuration,
                                                               RoutingKey.Factory<MailboxId> routingKeyFactory) {
        return new OpenSearchSearcher(
            client,
            queryConverter,
            DEFAULT_SEARCH_SIZE,
            configuration.getReadAliasMailboxName(), routingKeyFactory);
    }

    @Provides
    @Singleton
    @Named(MailboxOpenSearchConstants.InjectionNames.MAILBOX)
    private OpenSearchIndexer createMailboxOpenSearchIndexer(ReactorOpenSearchClient client,
                                                                OpenSearchMailboxConfiguration configuration) {
        return new OpenSearchIndexer(
            client,
            configuration.getWriteAliasMailboxName());
    }

    @Provides
    @Singleton
    public IndexAttachments provideIndexAttachments(OpenSearchMailboxConfiguration configuration) {
        return configuration.getIndexAttachment();
    }

    @Provides
    @Singleton
    public IndexHeaders provideIndexHeaders(OpenSearchMailboxConfiguration configuration) {
        return configuration.getIndexHeaders();
    }

    @ProvidesIntoSet
    InitializationOperation createIndex(MailboxIndexCreator instance) {
        return InitilizationOperationBuilder
            .forClass(MailboxIndexCreator.class)
            .init(instance::createIndex);
    }
}
