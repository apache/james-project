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
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxModule;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.mail.CassandraModSeqProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUidProvider;
import org.apache.james.mailbox.cassandra.quota.CassandraCurrentQuotaManager;
import org.apache.james.mailbox.cassandra.quota.CassandraPerUserMaxQuotaManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.MockAuthenticator;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.mailbox.store.quota.StoreQuotaManager;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.james.mpt.imapmailbox.MailboxCreationDelegate;

public class CassandraHostSystem extends JamesImapHostSystem {

    private static final ImapFeatures IMAP_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT);
    
    private final CassandraMailboxManager mailboxManager;
    private final MockAuthenticator userManager;
    private final CassandraModule mailboxModule;
    private CassandraCluster cassandraClusterSingleton;

    public CassandraHostSystem() throws Exception {
        mailboxModule = new CassandraMailboxModule();
        cassandraClusterSingleton = CassandraCluster.create(mailboxModule);
        userManager = new MockAuthenticator();
        com.datastax.driver.core.Session session = cassandraClusterSingleton.getConf();
        CassandraModSeqProvider modSeqProvider = new CassandraModSeqProvider(session);
        CassandraUidProvider uidProvider = new CassandraUidProvider(session);

        CassandraMailboxSessionMapperFactory mapperFactory = new CassandraMailboxSessionMapperFactory(uidProvider, modSeqProvider, session, new CassandraTypesProvider(mailboxModule, session));
        
        mailboxManager = new CassandraMailboxManager(mapperFactory, userManager, new JVMMailboxPathLocker());
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
