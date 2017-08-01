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

package org.apache.james.jmap.cassandra.cucumber;

import java.util.Arrays;

import javax.inject.Inject;

import org.apache.activemq.store.PersistenceAdapter;
import org.apache.activemq.store.memory.MemoryPersistenceAdapter;
import org.apache.james.CassandraJamesServerMain;
import org.apache.james.GuiceJamesServer;
import org.apache.james.backends.cassandra.EmbeddedCassandra;
import org.apache.james.backends.es.EmbeddedElasticSearch;
import org.apache.james.jmap.methods.integration.cucumber.MainStepdefs;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.elasticsearch.MailboxElasticsearchConstants;
import org.apache.james.modules.CassandraJmapServerModule;
import org.junit.rules.TemporaryFolder;

import com.github.fge.lambdas.runnable.ThrowingRunnable;

import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.runtime.java.guice.ScenarioScoped;

@ScenarioScoped
public class CassandraStepdefs {

    private final MainStepdefs mainStepdefs;
    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch = new EmbeddedElasticSearch(temporaryFolder, MailboxElasticsearchConstants.MAILBOX_INDEX);
    private EmbeddedCassandra cassandra = EmbeddedCassandra.createStartServer();

    @Inject
    private CassandraStepdefs(MainStepdefs mainStepdefs) {
        this.mainStepdefs = mainStepdefs;
    }

    @Before
    public void init() throws Exception {
        temporaryFolder.create();
        embeddedElasticSearch.before();
        mainStepdefs.messageIdFactory = new CassandraMessageId.Factory();
        mainStepdefs.jmapServer = new GuiceJamesServer()
                .combineWith(CassandraJamesServerMain.cassandraServerModule, CassandraJamesServerMain.protocols)
                .overrideWith(new CassandraJmapServerModule(temporaryFolder, embeddedElasticSearch, cassandra))
                .overrideWith((binder) -> binder.bind(PersistenceAdapter.class).to(MemoryPersistenceAdapter.class));
        mainStepdefs.awaitMethod = () -> embeddedElasticSearch.awaitForElasticSearch();
        mainStepdefs.init();
    }

    @After
    public void tearDown() {
        ignoreFailures(mainStepdefs::tearDown,
                () -> embeddedElasticSearch.after(),
                () -> temporaryFolder.delete());
    }

    private void ignoreFailures(ThrowingRunnable... cleaners) {
        Arrays.stream(cleaners)
            .forEach(this::runSwallowingException);
    }
    
    private void runSwallowingException(Runnable run) {
        try {
            run.run();
        } catch (Exception e) {
            // ignore
        }
    }
}
