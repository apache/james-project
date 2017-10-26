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
import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.backends.es.ElasticSearchConstants;
import org.apache.james.backends.es.AliasName;
import org.apache.james.backends.es.ClientProviderImpl;
import org.apache.james.backends.es.IndexCreationFactory;
import org.apache.james.backends.es.IndexName;
import org.apache.james.backends.es.NodeMappingFactory;
import org.apache.james.backends.es.TypeName;
import org.apache.james.mailbox.elasticsearch.IndexAttachments;
import org.apache.james.mailbox.elasticsearch.MailboxElasticSearchConstants;
import org.apache.james.mailbox.elasticsearch.MailboxMappingFactory;
import org.apache.james.mailbox.elasticsearch.events.ElasticSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.utils.PropertiesProvider;
import org.apache.james.utils.RetryExecutorUtil;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.nurkiewicz.asyncretry.AsyncRetryExecutor;

public class ElasticSearchMailboxModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchMailboxModule.class);

    public static final String ELASTICSEARCH_CONFIGURATION_NAME = "elasticsearch";
    private static final String LOCALHOST = "127.0.0.1";

    @Override
    protected void configure() {
        bind(TypeName.class).toInstance(MailboxElasticSearchConstants.MESSAGE_TYPE);
        bind(ElasticSearchListeningMessageSearchIndex.class).in(Scopes.SINGLETON);
        bind(MessageSearchIndex.class).to(ElasticSearchListeningMessageSearchIndex.class);
        bind(ListeningMessageSearchIndex.class).to(ElasticSearchListeningMessageSearchIndex.class);
    }

    @Provides
    @Singleton
    private ElasticSearchConfiguration getElasticSearchConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            PropertiesConfiguration configuration = propertiesProvider.getConfiguration(ELASTICSEARCH_CONFIGURATION_NAME);
            return ElasticSearchConfiguration.fromProperties(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + ELASTICSEARCH_CONFIGURATION_NAME + " configuration file. Using 127.0.0.1:9300 as contact point");
            PropertiesConfiguration configuration = new PropertiesConfiguration();
            configuration.addProperty(ElasticSearchConfiguration.ELASTICSEARCH_HOSTS, LOCALHOST);
            return ElasticSearchConfiguration.fromProperties(configuration);
        }
    }

    @Provides
    protected IndexName provideIndexName(ElasticSearchConfiguration configuration) {
        return configuration.getIndexName();
    }

    @Provides @Named(ElasticSearchConstants.READ_ALIAS)
    protected AliasName provideReadAliasName(ElasticSearchConfiguration configuration) {
        return configuration.getReadAliasName();
    }

    @Provides @Named(ElasticSearchConstants.WRITE_ALIAS)
    protected AliasName provideWriteAliasName(ElasticSearchConfiguration configuration) {
        return configuration.getWriteAliasName();
    }

    @Provides
    @Singleton
    protected IndexCreationFactory provideIndexCreationFactory(ElasticSearchConfiguration configuration) {
        return new IndexCreationFactory()
            .useIndex(configuration.getIndexName())
            .addAlias(configuration.getReadAliasName())
            .addAlias(configuration.getWriteAliasName())
            .nbShards(configuration.getNbShards())
            .nbReplica(configuration.getNbReplica());
    }

    @Provides
    @Singleton
    protected Client provideClient(ElasticSearchConfiguration configuration,
                                           IndexCreationFactory indexCreationFactory,
                                           AsyncRetryExecutor executor) throws ExecutionException, InterruptedException {

        return RetryExecutorUtil.retryOnExceptions(executor, configuration.getMaxRetries(), configuration.getMinDelay(), NoNodeAvailableException.class)
            .getWithRetry(context -> connectToCluster(configuration, indexCreationFactory))
            .get();
    }

    private Client connectToCluster(ElasticSearchConfiguration configuration, IndexCreationFactory indexCreationFactory) {
        LOGGER.info("Trying to connect to ElasticSearch service at {}", LocalDateTime.now());

        Client client = ClientProviderImpl.fromHosts(configuration.getHosts()).get();

        indexCreationFactory.createIndexAndAliases(client);
        return NodeMappingFactory.applyMapping(client,
            configuration.getIndexName(),
            MailboxElasticSearchConstants.MESSAGE_TYPE,
            MailboxMappingFactory.getMappingContent());
    }

    @Provides
    @Singleton
    public IndexAttachments provideIndexAttachments(ElasticSearchConfiguration configuration) {
        return configuration.getIndexAttachment();
    }

}
