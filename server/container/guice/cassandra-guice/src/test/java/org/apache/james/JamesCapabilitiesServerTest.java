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

import static org.apache.james.CassandraJamesServerMain.ALL_BUT_JMX_CASSANDRA_MODULE;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.EnumSet;

import org.apache.activemq.store.PersistenceAdapter;
import org.apache.activemq.store.memory.MemoryPersistenceAdapter;
import org.apache.james.backends.es.EmbeddedElasticSearch;
import org.apache.james.jmap.methods.GetMessageListMethod;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.elasticsearch.MailboxElasticSearchConstants;
import org.apache.james.modules.TestElasticSearchModule;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.server.core.configuration.Configuration;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import com.google.inject.Module;

public class JamesCapabilitiesServerTest {

    private GuiceJamesServer server;
    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch = new EmbeddedElasticSearch(temporaryFolder, MailboxElasticSearchConstants.DEFAULT_MAILBOX_INDEX);

    @ClassRule
    public static DockerCassandraRule cassandraServer = new DockerCassandraRule();
    
    @Rule
    public RuleChain chain = RuleChain.outerRule(temporaryFolder).around(embeddedElasticSearch);

    @After
    public void teardown() {
        server.stop();
    }
    
    private GuiceJamesServer createCassandraJamesServer(final MailboxManager mailboxManager) throws IOException {
        Module mockMailboxManager = (binder) -> binder.bind(MailboxManager.class).toInstance(mailboxManager);
        Configuration configuration = Configuration.builder()
            .workingDirectory(temporaryFolder.newFolder())
            .configurationFromClasspath()
            .build();

        return new GuiceJamesServer(configuration)
            .combineWith(ALL_BUT_JMX_CASSANDRA_MODULE)
            .overrideWith((binder) -> binder.bind(PersistenceAdapter.class).to(MemoryPersistenceAdapter.class))
            .overrideWith(new TestElasticSearchModule(embeddedElasticSearch),
                cassandraServer.getModule(),
                new TestJMAPServerModule(GetMessageListMethod.DEFAULT_MAXIMUM_LIMIT),
                mockMailboxManager);
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
    public void startShouldFailWhenNoACLCapability() throws Exception {
        MailboxManager mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.getSupportedMailboxCapabilities())
            .thenReturn(EnumSet.complementOf(EnumSet.of(MailboxManager.MailboxCapabilities.ACL)));
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
            .thenReturn(EnumSet.allOf(MailboxManager.MessageCapabilities.class));
        when(mailboxManager.getSupportedSearchCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.SearchCapabilities.class));

        server = createCassandraJamesServer(mailboxManager);

        assertThatThrownBy(() -> server.start()).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void startShouldFailWhenNoAttachmentSearchCapability() throws Exception {
        MailboxManager mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.getSupportedMailboxCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.MailboxCapabilities.class));
        when(mailboxManager.getSupportedMessageCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.MessageCapabilities.class));
        when(mailboxManager.getSupportedSearchCapabilities())
            .thenReturn(EnumSet.complementOf(EnumSet.of(MailboxManager.SearchCapabilities.Attachment)));

        server = createCassandraJamesServer(mailboxManager);

        assertThatThrownBy(() -> server.start()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void startShouldFailWhenNoAttachmentFileNameSearchCapability() throws Exception {
        MailboxManager mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.getSupportedMailboxCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.MailboxCapabilities.class));
        when(mailboxManager.getSupportedMessageCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.MessageCapabilities.class));
        when(mailboxManager.getSupportedSearchCapabilities())
            .thenReturn(EnumSet.complementOf(EnumSet.of(MailboxManager.SearchCapabilities.AttachmentFileName)));

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
    public void startShouldFailWhenNoUniqueIDCapability() throws Exception {
        MailboxManager mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.getSupportedMailboxCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.MailboxCapabilities.class));
        when(mailboxManager.getSupportedMessageCapabilities())
            .thenReturn(EnumSet.complementOf(EnumSet.of(MailboxManager.MessageCapabilities.UniqueID)));
        when(mailboxManager.getSupportedSearchCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.SearchCapabilities.class));

        server = createCassandraJamesServer(mailboxManager);

        assertThatThrownBy(() -> server.start()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void startShouldSucceedWhenRequiredCapabilities() throws Exception {
        MailboxManager mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Move))
            .thenReturn(true);
        when(mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.ACL))
            .thenReturn(true);
        when(mailboxManager.getSupportedMessageCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.MessageCapabilities.class));
        when(mailboxManager.getSupportedSearchCapabilities())
            .thenReturn(EnumSet.allOf(MailboxManager.SearchCapabilities.class));

        server = createCassandraJamesServer(mailboxManager);

        server.start();
    }

}
