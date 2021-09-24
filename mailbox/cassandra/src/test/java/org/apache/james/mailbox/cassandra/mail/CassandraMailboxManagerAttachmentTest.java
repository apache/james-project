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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.AttachmentContentLoader;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.TestCassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.store.AbstractMailboxManagerAttachmentTest;
import org.apache.james.mailbox.store.MailboxManagerConfiguration;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.NaiveThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMailboxManagerAttachmentTest extends AbstractMailboxManagerAttachmentTest {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MailboxAggregateModule.MODULE);

    private CassandraMailboxSessionMapperFactory mailboxSessionMapperFactory;
    private CassandraMailboxManager mailboxManager;
    private CassandraMailboxManager parseFailingMailboxManager;

    @BeforeEach
    void init() throws Exception {
        initSystemUnderTest();
        super.setUp();
    }

    private void initSystemUnderTest() throws Exception {
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();
        ThreadIdGuessingAlgorithm threadIdGuessingAlgorithm = new NaiveThreadIdGuessingAlgorithm();

        mailboxSessionMapperFactory = TestCassandraMailboxSessionMapperFactory.forTests(
            cassandraCluster.getCassandraCluster(),
            messageIdFactory);
        Authenticator noAuthenticator = null;
        Authorizator noAuthorizator = null;
        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        StoreRightManager storeRightManager = new StoreRightManager(mailboxSessionMapperFactory, new UnionMailboxACLResolver(), eventBus);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mailboxSessionMapperFactory, storeRightManager);

        SessionProviderImpl sessionProvider = new SessionProviderImpl(noAuthenticator, noAuthorizator);
        QuotaComponents quotaComponents = QuotaComponents.disabled(sessionProvider, mailboxSessionMapperFactory);
        AttachmentContentLoader attachmentContentLoader = null;
        MessageSearchIndex index = new SimpleMessageSearchIndex(mailboxSessionMapperFactory, mailboxSessionMapperFactory, new DefaultTextExtractor(), attachmentContentLoader);

        mailboxManager = new CassandraMailboxManager(mailboxSessionMapperFactory, sessionProvider, new NoMailboxPathLocker(), new MessageParser(),
            messageIdFactory, eventBus, annotationManager, storeRightManager, quotaComponents,
            index, MailboxManagerConfiguration.DEFAULT, PreDeletionHooks.NO_PRE_DELETION_HOOK, threadIdGuessingAlgorithm);
        MessageParser failingMessageParser = mock(MessageParser.class);
        when(failingMessageParser.retrieveAttachments(any(InputStream.class)))
            .thenThrow(new RuntimeException("Message parser set to fail"));
        parseFailingMailboxManager = new CassandraMailboxManager(mailboxSessionMapperFactory, sessionProvider,
            new NoMailboxPathLocker(), failingMessageParser, messageIdFactory,
            eventBus, annotationManager, storeRightManager, quotaComponents, index, MailboxManagerConfiguration.DEFAULT, PreDeletionHooks.NO_PRE_DELETION_HOOK, threadIdGuessingAlgorithm);
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
