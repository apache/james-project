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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.jmap.methods.GetMessageListMethod;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.apache.james.modules.TestElasticSearchModule;
import org.apache.james.modules.TestFilesystemModule;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import com.datastax.driver.core.Session;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class JamesCapabilitiesServerTest {

    private GuiceJamesServer<CassandraId> server;
    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch = new EmbeddedElasticSearch(temporaryFolder);

    @Rule
    public RuleChain chain = RuleChain.outerRule(temporaryFolder).around(embeddedElasticSearch);

    @Test(expected=IllegalArgumentException.class)
    public void startShouldFailWhenNoMoveCapability() throws Exception {
        MailboxManager mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.getSupportedCapabilities())
            .thenReturn(ImmutableList.of(MailboxManager.Capabilities.Basic));
        server = createCassandraJamesServer(mailboxManager);

        server.start();

        // In case of non-failure
        server.stop();
    }

    @Test
    public void startShouldSucceedWhenMoveCapability() throws Exception {
        MailboxManager mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.getSupportedCapabilities())
            .thenReturn(ImmutableList.of(MailboxManager.Capabilities.Move));
        server = createCassandraJamesServer(mailboxManager);

        server.start();

        server.stop();
    }

    private GuiceJamesServer<CassandraId> createCassandraJamesServer(final MailboxManager mailboxManager) {
        return new GuiceJamesServer<>(CassandraJamesServerMain.cassandraId)
            .combineWith(CassandraJamesServerMain.cassandraServerModule)
            .overrideWith(new TestElasticSearchModule(embeddedElasticSearch),
                new TestFilesystemModule(temporaryFolder),
                new TestJMAPServerModule(GetMessageListMethod.DEFAULT_MAXIMUM_LIMIT),
                new AbstractModule() {

                    @Override
                    protected void configure() {
                        bind(MailboxManager.class).toInstance(mailboxManager);
                    }

                    @Provides
                    @Singleton
                    Session provideSession(CassandraModule cassandraModule) {
                        CassandraCluster cassandra = CassandraCluster.create(cassandraModule);
                        return cassandra.getConf();
                    }
                });
    }
}
