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
package org.apache.james.mpt.imapmailbox.cassandra.host;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.TestCassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAnnotationModule;
import org.apache.james.mailbox.cassandra.modules.CassandraApplicableFlagsModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.cassandra.modules.CassandraDeletedMessageModule;
import org.apache.james.mailbox.cassandra.modules.CassandraFirstUnseenModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxRecentsModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.cassandra.modules.CassandraModSeqModule;
import org.apache.james.mailbox.cassandra.modules.CassandraQuotaModule;
import org.apache.james.mailbox.cassandra.modules.CassandraSubscriptionModule;
import org.apache.james.mailbox.cassandra.modules.CassandraUidModule;
import org.apache.james.mailbox.cassandra.quota.CassandraCurrentQuotaManager;
import org.apache.james.mailbox.cassandra.quota.CassandraGlobalMaxQuotaDao;
import org.apache.james.mailbox.cassandra.quota.CassandraPerDomainMaxQuotaDao;
import org.apache.james.mailbox.cassandra.quota.CassandraPerUserMaxQuotaDao;
import org.apache.james.mailbox.cassandra.quota.CassandraPerUserMaxQuotaManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.event.DefaultDelegatingMailboxListener;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.mailbox.store.quota.StoreQuotaManager;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.host.JamesImapHostSystem;

public class CassandraHostSystem extends JamesImapHostSystem {

    private static final ImapFeatures IMAP_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT,
        Feature.MOVE_SUPPORT,
        Feature.USER_FLAGS_SUPPORT,
        Feature.QUOTA_SUPPORT,
        Feature.ANNOTATION_SUPPORT);

    private final CassandraModule mailboxModule = new CassandraModuleComposite(
            new CassandraAclModule(),
            new CassandraMailboxModule(),
            new CassandraMessageModule(),
            new CassandraBlobModule(),
            new CassandraMailboxCounterModule(),
            new CassandraMailboxRecentsModule(),
            new CassandraFirstUnseenModule(),
            new CassandraDeletedMessageModule(),
            new CassandraUidModule(),
            new CassandraModSeqModule(),
            new CassandraSubscriptionModule(),
            new CassandraQuotaModule(),
            new CassandraAttachmentModule(),
            new CassandraAnnotationModule(),
            new CassandraApplicableFlagsModule());

    private final String cassandraHost;
    private final int cassandraPort;
    private CassandraMailboxManager mailboxManager;
    private CassandraCluster cassandra;
    private CassandraPerUserMaxQuotaManager perUserMaxQuotaManager;
    
    public CassandraHostSystem(String cassandraHost, int cassandraPort) {
        this.cassandraHost = cassandraHost;
        this.cassandraPort = cassandraPort;
    }

    @Override
    public void beforeTest() throws Exception {
        super.beforeTest();
        cassandra = CassandraCluster.create(mailboxModule, cassandraHost, cassandraPort);
        com.datastax.driver.core.Session session = cassandra.getConf();
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();
        CassandraMailboxSessionMapperFactory mapperFactory = TestCassandraMailboxSessionMapperFactory.forTests(
            cassandra.getConf(),
            cassandra.getTypesProvider(),
            messageIdFactory);

        DefaultDelegatingMailboxListener delegatingMailboxListener = new DefaultDelegatingMailboxListener();
        MailboxEventDispatcher mailboxEventDispatcher = new MailboxEventDispatcher(delegatingMailboxListener);
        StoreRightManager storeRightManager = new StoreRightManager(mapperFactory, new UnionMailboxACLResolver(), new SimpleGroupMembershipResolver(), mailboxEventDispatcher);

        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mapperFactory, storeRightManager);
        mailboxManager = new CassandraMailboxManager(mapperFactory, authenticator, authorizator,
            new JVMMailboxPathLocker(), new MessageParser(), messageIdFactory,
            mailboxEventDispatcher, delegatingMailboxListener, annotationManager, storeRightManager);
        QuotaRootResolver quotaRootResolver = new DefaultUserQuotaRootResolver(mapperFactory);

        perUserMaxQuotaManager = new CassandraPerUserMaxQuotaManager(
            new CassandraPerUserMaxQuotaDao(session),
            new CassandraPerDomainMaxQuotaDao(cassandra.getConf()),
            new CassandraGlobalMaxQuotaDao(session));

        CassandraCurrentQuotaManager currentQuotaManager = new CassandraCurrentQuotaManager(session);

        StoreQuotaManager quotaManager = new StoreQuotaManager(currentQuotaManager, perUserMaxQuotaManager);

        ListeningCurrentQuotaUpdater quotaUpdater = new ListeningCurrentQuotaUpdater(currentQuotaManager, quotaRootResolver, mailboxEventDispatcher, quotaManager);

        mailboxManager.setQuotaRootResolver(quotaRootResolver);
        mailboxManager.setQuotaManager(quotaManager);
        mailboxManager.setQuotaUpdater(quotaUpdater);

        mailboxManager.init();

        SubscriptionManager subscriptionManager = new StoreSubscriptionManager(mapperFactory);

        configure(new DefaultImapDecoderFactory().buildImapDecoder(),
                new DefaultImapEncoderFactory().buildImapEncoder(),
                DefaultImapProcessorFactory.createDefaultProcessor(mailboxManager, subscriptionManager, quotaManager, quotaRootResolver, new DefaultMetricFactory()));
    }

    @Override
    public void afterTest() throws Exception {
        super.afterTest();
        cassandra.close();
    }
    
    @Override
    public boolean supports(Feature... features) {
        return IMAP_FEATURES.supports(features);
    }

    @Override
    public void setQuotaLimits(QuotaCount maxMessageQuota, QuotaSize maxStorageQuota) throws MailboxException {
        perUserMaxQuotaManager.setGlobalMaxMessage(maxMessageQuota);
        perUserMaxQuotaManager.setGlobalMaxStorage(maxStorageQuota);
    }

    @Override
    public MailboxManager getMailboxManager() {
        return mailboxManager;
    }
}
