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

package org.apache.james;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.jmap.methods.GetMessageListMethod;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.apache.james.modules.TestElasticSearchModule;
import org.apache.james.modules.TestFilesystemModule;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import com.datastax.driver.core.Session;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class CassandraJamesServerTest extends AbstractJmapJamesServerTest {

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch = new EmbeddedElasticSearch(temporaryFolder);
    private CassandraCluster cassandra;

    @Rule
    public RuleChain chain = RuleChain.outerRule(temporaryFolder).around(embeddedElasticSearch);

    @Override
    protected GuiceJmapJamesServer createJamesServer() {
        return new GuiceJmapJamesServer()
                .combineWith(CassandraJamesServerMain.cassandraServerModule)
                .overrideWith(new TestElasticSearchModule(embeddedElasticSearch),
                        new TestFilesystemModule(temporaryFolder),
                        new TestJMAPServerModule(GetMessageListMethod.DEFAULT_MAXIMUM_LIMIT),
                        new AbstractModule() {
                    
                    @Override
                    protected void configure() {
                    }
                    
                    @Provides
                    @Singleton
                    Session provideSession(CassandraModule cassandraModule) {
                        cassandra = CassandraCluster.create(cassandraModule);
                        return cassandra.getConf();
                    }
                });
    }

    @Override
    protected void clean() {
        cassandra.clearAllTables();
    }
    

}
