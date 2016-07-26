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
import java.util.concurrent.ExecutionException;

import javax.inject.Singleton;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.elasticsearch.ClientProvider;
import org.apache.james.mailbox.elasticsearch.ClientProviderImpl;
import org.apache.james.mailbox.elasticsearch.IndexCreationFactory;
import org.apache.james.mailbox.elasticsearch.NodeMappingFactory;
import org.apache.james.mailbox.elasticsearch.events.ElasticSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.store.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.tika.extractor.TikaTextExtractor;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.NoNodeAvailableException;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.nurkiewicz.asyncretry.AsyncRetryExecutor;

public class ElasticSearchMailboxModule extends AbstractModule {

    private static final int DEFAULT_CONNECTION_MAX_RETRIES = 7;
    private static final int DEFAULT_CONNECTION_MIN_DELAY = 3000;

    @Override
    protected void configure() {
        bind(ElasticSearchListeningMessageSearchIndex.class).in(Scopes.SINGLETON);
        bind(MessageSearchIndex.class).to(ElasticSearchListeningMessageSearchIndex.class);
        bind(ListeningMessageSearchIndex.class).to(ElasticSearchListeningMessageSearchIndex.class);

        bind(TikaTextExtractor.class).in(Scopes.SINGLETON);
        bind(TextExtractor.class).to(TikaTextExtractor.class);
    }

    @Provides
    @Singleton
    protected Client provideClientProvider(FileSystem fileSystem, AsyncRetryExecutor executor) throws ConfigurationException, FileNotFoundException, ExecutionException, InterruptedException {
        PropertiesConfiguration propertiesReader = new PropertiesConfiguration(fileSystem.getFile(FileSystem.FILE_PROTOCOL_AND_CONF + "elasticsearch.properties"));

        ClientProvider clientProvider = new ClientProviderImpl(propertiesReader.getString("elasticsearch.masterHost"),
                propertiesReader.getInt("elasticsearch.port"));
        Client client = getRetryer(executor, propertiesReader)
                .getWithRetry(ctx -> clientProvider.get()).get();
        IndexCreationFactory.createIndex(client,
            propertiesReader.getInt("elasticsearch.nb.shards"),
            propertiesReader.getInt("elasticsearch.nb.replica"));
        NodeMappingFactory.applyMapping(client);
        return client;
    }

    private static AsyncRetryExecutor getRetryer(AsyncRetryExecutor executor, PropertiesConfiguration configuration) {
        return executor
                .withProportionalJitter()
                .retryOn(NoNodeAvailableException.class)
                .withMaxRetries(configuration.getInt("elasticsearch.retryConnection.maxRetries", DEFAULT_CONNECTION_MAX_RETRIES))
                .withMinDelay(configuration.getInt("elasticsearch.retryConnection.minDelay", DEFAULT_CONNECTION_MIN_DELAY));
    }

}
