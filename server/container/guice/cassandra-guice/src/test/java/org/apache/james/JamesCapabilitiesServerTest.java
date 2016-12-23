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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EnumSet;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.jmap.methods.GetMessageListMethod;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.apache.james.modules.TestElasticSearchModule;
import org.apache.james.modules.TestFilesystemModule;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import com.datastax.driver.core.Session;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class JamesCapabilitiesServerTest {

    private GuiceJamesServerImpl server;
    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch = new EmbeddedElasticSearch(temporaryFolder);

    @Rule
    public RuleChain chain = RuleChain.outerRule(temporaryFolder).around(embeddedElasticSearch);

    @After
    public void teardown() {
        server.stop();
    }
    
    private GuiceJamesServerImpl createCassandraJamesServer(final MailboxManager mailboxManager) {
        Module mockMailboxManager = (binder) -> binder.bind(MailboxManager.class).toInstance(mailboxManager);
        
        return new GuiceJamesServerImpl()
            .combineWith(CassandraJamesServerMain.cassandraServerModule)
            .overrideWith(new TestElasticSearchModule(embeddedElasticSearch),
                new TestFilesystemModule(temporaryFolder),
                new TestJMAPServerModule(GetMessageListMethod.DEFAULT_MAXIMUM_LIMIT),
                mockMailboxManager,
                new AbstractModule() {

                    @Override
                    protected void configure() {
                    }

                    @Provides
                    @Singleton
                    Session provideSession(CassandraModule cassandraModule) {
                        CassandraCluster cassandra = CassandraCluster.create(cassandraModule);
                        return cassandra.getConf();
                    }

                });
    }
    
    @Test
    public void startShouldFailWhenNoMoveCapability() throws Exception {
        MailboxManager mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.getSupportedMailboxCapabilities())
            .thenReturn(EnumSet.complementOf(EnumSet.of(MailboxManager.MailboxCapabilities.Move)));
        when(mailboxManager.getSupportedMessageCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.MessageCapabilities.class));
        when(mailboxManager.getSupportedSearchCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.SearchCapabilities.class));

        server = createCassandraJamesServer(mailboxManager);
        
        assertThatThrownBy(() -> server.start()).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void startShouldFailWhenNoAttachmentCapability() throws Exception {
        MailboxManager mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.getSupportedMailboxCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.MailboxCapabilities.class));
        when(mailboxManager.getSupportedMessageCapabilities())
            .thenReturn(EnumSet.complementOf(EnumSet.of(MailboxManager.MessageCapabilities.Attachment)));
        when(mailboxManager.getSupportedSearchCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.SearchCapabilities.class));

        server = createCassandraJamesServer(mailboxManager);

        assertThatThrownBy(() -> server.start()).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void startShouldFailWhenNoTextCapability() throws Exception {
        MailboxManager mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.getSupportedMailboxCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.MailboxCapabilities.class));
        when(mailboxManager.getSupportedMessageCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.MessageCapabilities.class));
        when(mailboxManager.getSupportedSearchCapabilities())
            .thenReturn(EnumSet.complementOf(EnumSet.of(MailboxManager.SearchCapabilities.Text)));

        server = createCassandraJamesServer(mailboxManager);

        assertThatThrownBy(() -> server.start()).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void startShouldFailWhenNoMultimailboxSearchCapability() throws Exception {
        MailboxManager mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.getSupportedMailboxCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.MailboxCapabilities.class));
        when(mailboxManager.getSupportedMessageCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.MessageCapabilities.class));
        when(mailboxManager.getSupportedSearchCapabilities())
            .thenReturn(EnumSet.complementOf(EnumSet.of(MailboxManager.SearchCapabilities.MultimailboxSearch)));

        server = createCassandraJamesServer(mailboxManager);

        assertThatThrownBy(() -> server.start()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void startShouldSucceedWhenRequiredCapabilities() throws Exception {
        MailboxManager mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Move))
            .thenReturn(true);
        when(mailboxManager.getSupportedMessageCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.MessageCapabilities.class));
        when(mailboxManager.getSupportedSearchCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.SearchCapabilities.class));

        server = createCassandraJamesServer(mailboxManager);

        server.start();
    }

}
