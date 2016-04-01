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

import java.io.File;
import java.util.function.Supplier;

import javax.inject.Singleton;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.EmbeddedCassandra;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.junit.rules.TemporaryFolder;

import com.datastax.driver.core.Session;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class CassandraJmapServerModule extends AbstractModule {

    private static final int LIMIT_TO_3_MESSAGES = 3;
    private final Supplier<File> fileSupplier;
    private final EmbeddedElasticSearch embeddedElasticSearch;
    private final EmbeddedCassandra cassandra;

    public CassandraJmapServerModule(Supplier<File> fileSupplier, EmbeddedElasticSearch embeddedElasticSearch, EmbeddedCassandra cassandra) {
        this.fileSupplier = fileSupplier;
        this.embeddedElasticSearch = embeddedElasticSearch;
        this.cassandra = cassandra;
    }

    public CassandraJmapServerModule(TemporaryFolder temporaryFolder, EmbeddedElasticSearch embeddedElasticSearch, EmbeddedCassandra cassandra) {
        this(temporaryFolder::getRoot, embeddedElasticSearch, cassandra);
    }


    @Override
    protected void configure() {
        install(new TestElasticSearchModule(embeddedElasticSearch));
        install(new TestFilesystemModule(fileSupplier));
        install(new TestJMAPServerModule(LIMIT_TO_3_MESSAGES));
        bind(EmbeddedCassandra.class).toInstance(cassandra);
    }
    
    @Provides
    @Singleton
    Session provideSession(CassandraCluster initializedCassandra) {
        return initializedCassandra.getConf();
    }
}
