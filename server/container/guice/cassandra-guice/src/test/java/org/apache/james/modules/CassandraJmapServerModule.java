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

package org.apache.james.modules;

import javax.inject.Singleton;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.es.EmbeddedElasticSearch;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;

import com.datastax.driver.core.Session;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Names;

public class CassandraJmapServerModule extends AbstractModule {

    private static final int LIMIT_TO_3_MESSAGES = 3;
    private final EmbeddedElasticSearch embeddedElasticSearch;
    private final String cassandraHost;
    private final int cassandraPort;

    public CassandraJmapServerModule(EmbeddedElasticSearch embeddedElasticSearch, String cassandraHost, int cassandraPort) {
        this.embeddedElasticSearch = embeddedElasticSearch;
        this.cassandraHost = cassandraHost;
        this.cassandraPort = cassandraPort;
    }

    @Override
    protected void configure() {
        install(new TestElasticSearchModule(embeddedElasticSearch));
        install(new TestJMAPServerModule(LIMIT_TO_3_MESSAGES));
        install(binder -> binder.bind(TextExtractor.class).to(DefaultTextExtractor.class));
        install(binder -> binder.bindConstant().annotatedWith(Names.named("cassandraHost")).to(cassandraHost));
        install(binder -> binder.bindConstant().annotatedWith(Names.named("cassandraPort")).to(cassandraPort));
    }
    
    @Provides
    @Singleton
    Session provideSession(CassandraCluster initializedCassandra) {
        return initializedCassandra.getConf();
    }
}
