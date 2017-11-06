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
package org.apache.james.mailbox.cassandra.mail;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.TestCassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraApplicableFlagsModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.cassandra.modules.CassandraBlobModule;
import org.apache.james.mailbox.cassandra.modules.CassandraDeletedMessageModule;
import org.apache.james.mailbox.cassandra.modules.CassandraFirstUnseenModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxRecentsModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.cassandra.modules.CassandraModSeqModule;
import org.apache.james.mailbox.cassandra.modules.CassandraUidModule;
import org.apache.james.mailbox.store.AbstractMailboxManagerAttachmentTest;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.DefaultDelegatingMailboxListener;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;

public class CassandraMailboxManagerAttachmentTest extends AbstractMailboxManagerAttachmentTest {

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();

    private CassandraMailboxSessionMapperFactory mailboxSessionMapperFactory;
    private CassandraMailboxManager mailboxManager;
    private CassandraMailboxManager parseFailingMailboxManager;
    private CassandraCluster cassandra;

    @Before
    public void init() throws Exception {
        
        CassandraModuleComposite modules = 
                new CassandraModuleComposite(
                    new CassandraAclModule(),
                    new CassandraMailboxModule(),
                    new CassandraMessageModule(),
                    new CassandraBlobModule(),
                    new CassandraMailboxCounterModule(),
                    new CassandraMailboxRecentsModule(),
                    new CassandraFirstUnseenModule(),
                    new CassandraDeletedMessageModule(),
                    new CassandraModSeqModule(),
                    new CassandraUidModule(),
                    new CassandraAttachmentModule(),
                    new CassandraApplicableFlagsModule());
        cassandra = CassandraCluster.create(modules, cassandraServer.getIp(), cassandraServer.getBindingPort());
        initSystemUnderTest();
        super.setUp();
    }

    @After
    public void tearDown() {
        cassandra.close();
    }

    
    private void initSystemUnderTest() throws Exception {
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();

        mailboxSessionMapperFactory = TestCassandraMailboxSessionMapperFactory.forTests(
            cassandra.getConf(),
            cassandra.getTypesProvider(),
            messageIdFactory);
        Authenticator noAuthenticator = null;
        Authorizator noAuthorizator = null;
        DefaultDelegatingMailboxListener delegatingMailboxListener = new DefaultDelegatingMailboxListener();
        MailboxEventDispatcher mailboxEventDispatcher = new MailboxEventDispatcher(delegatingMailboxListener);
        StoreRightManager storeRightManager = new StoreRightManager(mailboxSessionMapperFactory, new UnionMailboxACLResolver(), new SimpleGroupMembershipResolver());
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mailboxSessionMapperFactory, storeRightManager);

        mailboxManager = new CassandraMailboxManager(mailboxSessionMapperFactory,
            noAuthenticator, noAuthorizator, new NoMailboxPathLocker(), new MessageParser(),
            messageIdFactory, mailboxEventDispatcher, delegatingMailboxListener, annotationManager, storeRightManager);
        mailboxManager.init();
        MessageParser failingMessageParser = mock(MessageParser.class);
        when(failingMessageParser.retrieveAttachments(any()))
            .thenThrow(new RuntimeException("Message parser set to fail"));
        parseFailingMailboxManager = new CassandraMailboxManager(mailboxSessionMapperFactory, noAuthenticator, noAuthorizator,
            new NoMailboxPathLocker(), failingMessageParser, messageIdFactory,
            mailboxEventDispatcher, delegatingMailboxListener, annotationManager, storeRightManager);
        parseFailingMailboxManager.init();
    }

    @Override
    protected MailboxManager getMailboxManager() {
        return mailboxManager;
    }

    @Override
    protected MailboxSessionMapperFactory getMailboxSessionMapperFactory() {
        return mailboxSessionMapperFactory;
    }

    @Override
    protected MailboxManager getParseFailingMailboxManager() {
        return parseFailingMailboxManager;
    }

    @Override
    protected AttachmentMapperFactory getAttachmentMapperFactory() {
        return mailboxSessionMapperFactory;
    }
}
