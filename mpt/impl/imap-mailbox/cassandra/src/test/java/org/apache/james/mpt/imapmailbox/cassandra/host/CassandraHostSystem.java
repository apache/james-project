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
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraModSeqProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUidProvider;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAnnotationModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.cassandra.modules.CassandraModSeqModule;
import org.apache.james.mailbox.cassandra.modules.CassandraQuotaModule;
import org.apache.james.mailbox.cassandra.modules.CassandraSubscriptionModule;
import org.apache.james.mailbox.cassandra.modules.CassandraUidModule;
import org.apache.james.mailbox.cassandra.quota.CassandraCurrentQuotaManager;
import org.apache.james.mailbox.cassandra.quota.CassandraPerUserMaxQuotaManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.mailbox.store.quota.StoreQuotaManager;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.james.mpt.imapmailbox.MailboxCreationDelegate;

public class CassandraHostSystem extends JamesImapHostSystem {

    private static final ImapFeatures IMAP_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT,
        Feature.MOVE_SUPPORT,
        Feature.USER_FLAGS_SUPPORT,
        Feature.QUOTA_SUPPORT,
        Feature.ANNOTATION_SUPPORT);
    
    private final CassandraMailboxManager mailboxManager;
    private final FakeAuthenticator userManager;
    private final CassandraCluster cassandraClusterSingleton;

    public CassandraHostSystem() throws Exception {
        CassandraModule mailboxModule = new CassandraModuleComposite(
            new CassandraAclModule(),
            new CassandraMailboxModule(),
            new CassandraMessageModule(),
            new CassandraMailboxCounterModule(),
            new CassandraUidModule(),
            new CassandraModSeqModule(),
            new CassandraSubscriptionModule(),
            new CassandraQuotaModule(),
            new CassandraAttachmentModule(),
            new CassandraAnnotationModule());
        cassandraClusterSingleton = CassandraCluster.create(mailboxModule);
        userManager = new FakeAuthenticator();
        com.datastax.driver.core.Session session = cassandraClusterSingleton.getConf();
        CassandraModSeqProvider modSeqProvider = new CassandraModSeqProvider(session);
        CassandraUidProvider uidProvider = new CassandraUidProvider(session);
        CassandraTypesProvider typesProvider = new CassandraTypesProvider(mailboxModule, session);
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();
        CassandraMessageIdToImapUidDAO imapUidDAO = new CassandraMessageIdToImapUidDAO(session, messageIdFactory);
        CassandraMessageDAO messageDAO = new CassandraMessageDAO(session, typesProvider, messageIdFactory, imapUidDAO);
        CassandraMessageIdDAO messageIdDAO = new CassandraMessageIdDAO(session, messageIdFactory);

        CassandraMailboxSessionMapperFactory mapperFactory = new CassandraMailboxSessionMapperFactory(uidProvider, modSeqProvider, 
                session, typesProvider, messageDAO, messageIdDAO, imapUidDAO);
        
        mailboxManager = new CassandraMailboxManager(mapperFactory, userManager, new JVMMailboxPathLocker(), new MessageParser(), messageIdFactory); 
        QuotaRootResolver quotaRootResolver = new DefaultQuotaRootResolver(mapperFactory);

        CassandraPerUserMaxQuotaManager perUserMaxQuotaManager = new CassandraPerUserMaxQuotaManager(session);
        perUserMaxQuotaManager.setDefaultMaxMessage(4096);
        perUserMaxQuotaManager.setDefaultMaxStorage(5L * 1024L * 1024L * 1024L);

        CassandraCurrentQuotaManager currentQuotaManager = new CassandraCurrentQuotaManager(session);

        StoreQuotaManager quotaManager = new StoreQuotaManager();
        quotaManager.setMaxQuotaManager(perUserMaxQuotaManager);
        quotaManager.setCurrentQuotaManager(currentQuotaManager);

        ListeningCurrentQuotaUpdater quotaUpdater = new ListeningCurrentQuotaUpdater();
        quotaUpdater.setCurrentQuotaManager(currentQuotaManager);
        quotaUpdater.setQuotaRootResolver(quotaRootResolver);

        mailboxManager.setQuotaRootResolver(quotaRootResolver);
        mailboxManager.setQuotaManager(quotaManager);
        mailboxManager.setQuotaUpdater(quotaUpdater);

        mailboxManager.init();

        SubscriptionManager subscriptionManager = new StoreSubscriptionManager(mapperFactory);

        configure(new DefaultImapDecoderFactory().buildImapDecoder(),
                new DefaultImapEncoderFactory().buildImapEncoder(),
                DefaultImapProcessorFactory.createDefaultProcessor(mailboxManager, subscriptionManager, quotaManager, quotaRootResolver));
        cassandraClusterSingleton.ensureAllTables();
    }

    @Override
    protected void resetData() throws Exception {
        cassandraClusterSingleton.clearAllTables();
    }

    public boolean addUser(String user, String password) {
        userManager.addUser(user, password);
        return true;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    @Override
    public void createMailbox(MailboxPath mailboxPath) throws Exception{
        new MailboxCreationDelegate(mailboxManager).createMailbox(mailboxPath);
    }
    
    @Override
    public boolean supports(Feature... features) {
        return IMAP_FEATURES.supports(features);
    }
    
}
