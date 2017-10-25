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
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import javax.inject.Singleton;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.backends.es.AliasName;
import org.apache.james.backends.es.ClientProviderImpl;
import org.apache.james.backends.es.IndexCreationFactory;
import org.apache.james.backends.es.IndexName;
import org.apache.james.backends.es.NodeMappingFactory;
import org.apache.james.backends.es.TypeName;
import org.apache.james.mailbox.elasticsearch.IndexAttachments;
import org.apache.james.mailbox.elasticsearch.MailboxElasticsearchConstants;
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

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.nurkiewicz.asyncretry.AsyncRetryExecutor;

public class ElasticSearchMailboxModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchMailboxModule.class);

    public static final String ELASTICSEARCH_CONFIGURATION_NAME = "elasticsearch";
    public static final String ELASTICSEARCH_HOSTS = "elasticsearch.hosts";
    public static final String ELASTICSEARCH_MASTER_HOST = "elasticsearch.masterHost";
    public static final String ELASTICSEARCH_PORT = "elasticsearch.port";
    private static final int DEFAULT_CONNECTION_MAX_RETRIES = 7;
    private static final int DEFAULT_CONNECTION_MIN_DELAY = 3000;
    private static final boolean DEFAULT_INDEX_ATTACHMENTS = true;
    private static final int DEFAULT_NB_SHARDS = 1;
    private static final int DEFAULT_NB_REPLICA = 0;
    private static final String LOCALHOST = "127.0.0.1";

    @Override
    protected void configure() {
        bind(TypeName.class).toInstance(MailboxElasticsearchConstants.MESSAGE_TYPE);
        bind(ElasticSearchListeningMessageSearchIndex.class).in(Scopes.SINGLETON);
        bind(MessageSearchIndex.class).to(ElasticSearchListeningMessageSearchIndex.class);
        bind(ListeningMessageSearchIndex.class).to(ElasticSearchListeningMessageSearchIndex.class);
    }

    @Provides
    protected IndexName provideIndexName(ElasticSearchConfiguration elasticSearchConfiguration)
            throws ConfigurationException {
        try {
            return Optional.ofNullable(elasticSearchConfiguration.getConfiguration()
                .getString("elasticsearch.index.name"))
                .map(IndexName::new)
                .orElse(MailboxElasticsearchConstants.DEFAULT_MAILBOX_INDEX);
        } catch (FileNotFoundException e) {
            LOGGER.info("Could not find ElasticSearch configuration file. Using default index {}", MailboxElasticsearchConstants.DEFAULT_MAILBOX_INDEX.getValue());
            return MailboxElasticsearchConstants.DEFAULT_MAILBOX_INDEX;
        }
    }

    @Provides
    protected AliasName provideAliasName(ElasticSearchConfiguration elasticSearchConfiguration)
            throws ConfigurationException {
        try {
            return Optional.ofNullable(elasticSearchConfiguration.getConfiguration()
                .getString("elasticsearch.alias.name"))
                .map(AliasName::new)
                .orElse(MailboxElasticsearchConstants.DEFAULT_MAILBOX_ALIAS);
        } catch (FileNotFoundException e) {
            LOGGER.info("Could not find ElasticSearch configuration file. Using default alias {}", MailboxElasticsearchConstants.DEFAULT_MAILBOX_INDEX.getValue());
            return MailboxElasticsearchConstants.DEFAULT_MAILBOX_ALIAS;
        }
    }

    @Provides
    @Singleton
    protected Client provideClientProvider(ElasticSearchConfiguration elasticSearchConfiguration,
                                           IndexName indexName, AliasName aliasName,
                                           AsyncRetryExecutor executor) throws ConfigurationException, FileNotFoundException, ExecutionException, InterruptedException {
        PropertiesConfiguration propertiesReader = elasticSearchConfiguration.getConfiguration();
        int maxRetries = propertiesReader.getInt("elasticsearch.retryConnection.maxRetries", DEFAULT_CONNECTION_MAX_RETRIES);
        int minDelay = propertiesReader.getInt("elasticsearch.retryConnection.minDelay", DEFAULT_CONNECTION_MIN_DELAY);

        return RetryExecutorUtil.retryOnExceptions(executor, maxRetries, minDelay, NoNodeAvailableException.class)
            .getWithRetry(context -> connectToCluster(propertiesReader, indexName, aliasName))
            .get();
    }

    private Client createIndexAndMapping(Client client, IndexName indexName, AliasName aliasName, PropertiesConfiguration propertiesReader) {
        IndexCreationFactory.createIndexAndAlias(client,
            indexName,
            aliasName,
            propertiesReader.getInt(ELASTICSEARCH_CONFIGURATION_NAME + ".nb.shards", DEFAULT_NB_SHARDS),
            propertiesReader.getInt(ELASTICSEARCH_CONFIGURATION_NAME + ".nb.replica", DEFAULT_NB_REPLICA));
        NodeMappingFactory.applyMapping(client,
            MailboxElasticsearchConstants.DEFAULT_MAILBOX_INDEX,
            MailboxElasticsearchConstants.MESSAGE_TYPE,
            MailboxMappingFactory.getMappingContent());
        return client;
    }

    private Client connectToCluster(PropertiesConfiguration propertiesReader, IndexName indexName, AliasName aliasName)
            throws ConfigurationException {
        LOGGER.info("Trying to connect to ElasticSearch service at {}", LocalDateTime.now());

        return createIndexAndMapping(createClient(propertiesReader), indexName, aliasName, propertiesReader);
    }

    @Provides
    @Singleton
    private ElasticSearchConfiguration getElasticSearchConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            PropertiesConfiguration configuration = propertiesProvider.getConfiguration(ELASTICSEARCH_CONFIGURATION_NAME);
            return () -> configuration;
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + ELASTICSEARCH_CONFIGURATION_NAME + " configuration file. Using 127.0.0.1:9300 as contact point");
            PropertiesConfiguration propertiesConfiguration = new PropertiesConfiguration();
            propertiesConfiguration.addProperty(ELASTICSEARCH_HOSTS, LOCALHOST);
            return () -> propertiesConfiguration;
        }
    }

    private Client createClient(PropertiesConfiguration propertiesReader) throws ConfigurationException {
        Optional<String> monoHostAddress = Optional.ofNullable(propertiesReader.getString(ELASTICSEARCH_MASTER_HOST, null));
        Optional<Integer> monoHostPort = Optional.ofNullable(propertiesReader.getInteger(ELASTICSEARCH_PORT, null));
        Optional<String> multiHosts = Optional.ofNullable(propertiesReader.getString(ELASTICSEARCH_HOSTS, null));

        validateHostsConfigurationOptions(monoHostAddress, monoHostPort, multiHosts);

        if (monoHostAddress.isPresent()) {
            return ClientProviderImpl.forHost(monoHostAddress.get(), monoHostPort.get()).get();
        } else {
            return ClientProviderImpl.fromHostsString(multiHosts.get()).get();
        }
    }

    @VisibleForTesting
    static void validateHostsConfigurationOptions(Optional<String> monoHostAddress,
                                                          Optional<Integer> monoHostPort,
                                                          Optional<String> multiHosts) throws ConfigurationException {
        if (monoHostAddress.isPresent() != monoHostPort.isPresent()) {
            throw new ConfigurationException(ELASTICSEARCH_MASTER_HOST + " and " + ELASTICSEARCH_PORT + " should be specified together");
        }
        if (multiHosts.isPresent() && monoHostAddress.isPresent()) {
            throw new ConfigurationException("You should choose between mono host set up and " + ELASTICSEARCH_HOSTS);
        }
        if (!multiHosts.isPresent() && !monoHostAddress.isPresent()) {
            throw new ConfigurationException("You should specify either (" + ELASTICSEARCH_MASTER_HOST + " and " + ELASTICSEARCH_PORT + ") or " + ELASTICSEARCH_HOSTS);
        }
    }

    @Provides
    @Singleton
    public IndexAttachments provideIndexAttachments(PropertiesConfiguration configuration) {
        if (configuration.getBoolean(ELASTICSEARCH_CONFIGURATION_NAME + ".indexAttachments", DEFAULT_INDEX_ATTACHMENTS)) {
            return IndexAttachments.YES;
        }
        return IndexAttachments.NO;
    }

}
