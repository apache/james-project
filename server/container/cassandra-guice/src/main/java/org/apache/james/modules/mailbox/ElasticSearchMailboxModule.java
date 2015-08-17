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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.elasticsearch.*;
import org.apache.james.mailbox.elasticsearch.events.ElasticSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.store.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.tika.extractor.TikaTextExtractor;
import org.apache.james.utils.PropertiesReader;

import javax.inject.Singleton;

public class ElasticSearchMailboxModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(new TypeLiteral<MessageSearchIndex<CassandraId>>(){}).to(new TypeLiteral<ElasticSearchListeningMessageSearchIndex<CassandraId>>() {});
        bind(TextExtractor.class).to(TikaTextExtractor.class);
        bind(new TypeLiteral<MessageSearchIndex<CassandraId>>() {})
            .to(new TypeLiteral<ElasticSearchListeningMessageSearchIndex<CassandraId>>() {});
    }

    @Provides
    @Singleton
    protected ClientProvider provideClientProvider() {
        PropertiesReader propertiesReader = new PropertiesReader("elasticsearch.properties");
        ClientProvider clientProvider = new ClientProviderImpl(propertiesReader.getProperty("elasticsearch.masterHost"),
            Integer.parseInt(propertiesReader.getProperty("elasticsearch.port")));
        IndexCreationFactory.createIndex(clientProvider,
            Integer.parseInt(propertiesReader.getProperty("elasticsearch.nb.shards")),
            Integer.parseInt(propertiesReader.getProperty("elasticsearch.nb.replica")));
        NodeMappingFactory.applyMapping(clientProvider);
        return clientProvider;
    }

}
