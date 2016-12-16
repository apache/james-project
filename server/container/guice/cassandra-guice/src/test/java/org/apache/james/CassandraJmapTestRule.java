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
import org.apache.james.backends.cassandra.EmbeddedCassandra;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.apache.james.modules.CassandraJmapServerModule;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.google.inject.Module;


public class CassandraJmapTestRule implements TestRule {

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch = new EmbeddedElasticSearch(temporaryFolder);
    private EmbeddedCassandra cassandra = EmbeddedCassandra.createStartServer();

    public RuleChain chain = RuleChain
        .outerRule(temporaryFolder)
        .around(embeddedElasticSearch);

    public JmapJamesServer jmapServer() {
        return new JmapJamesServer()
                    .combineWith(CassandraJamesServerMain.cassandraServerModule)
                    .overrideWith(new CassandraJmapServerModule(temporaryFolder, embeddedElasticSearch, cassandra));
    }

    public JmapJamesServer jmapServer(Module additional) {
        return new JmapJamesServer()
            .combineWith(CassandraJamesServerMain.cassandraServerModule, additional)
            .overrideWith(new CassandraJmapServerModule(temporaryFolder, embeddedElasticSearch, cassandra));
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return chain.apply(base, description);
    }
    

    public EmbeddedElasticSearch getElasticSearch() {
        return embeddedElasticSearch;
    }
    
    public void await() {
        embeddedElasticSearch.awaitForElasticSearch();
    }
}
