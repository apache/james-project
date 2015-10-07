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

package org.apache.james.mpt.imapmailbox.inmemory.host;

import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.quota.InMemoryCurrentQuotaManager;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.MockAuthenticator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.mailbox.store.quota.StoreQuotaManager;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.james.mpt.imapmailbox.MailboxCreationDelegate;

public class InMemoryHostSystem extends JamesImapHostSystem {

    private StoreMailboxManager<InMemoryId> mailboxManager;
    private MockAuthenticator userManager;

    public static JamesImapHostSystem build() throws Exception {
        return new InMemoryHostSystem();
    }
    
    private InMemoryHostSystem() throws MailboxException {
        initFields();
    }
    
    public boolean addUser(String user, String password) throws Exception {
        userManager.addUser(user, password);
        return true;
    }

    @Override
    protected void resetData() throws Exception {
        initFields();
    }
    
    private void initFields() throws MailboxException {
        userManager = new MockAuthenticator();
        InMemoryMailboxSessionMapperFactory factory = new InMemoryMailboxSessionMapperFactory();
        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();

        mailboxManager = new StoreMailboxManager<InMemoryId>(factory, userManager, aclResolver, groupMembershipResolver);
        QuotaRootResolver quotaRootResolver = new DefaultQuotaRootResolver(factory);

        InMemoryPerUserMaxQuotaManager perUserMaxQuotaManager = new InMemoryPerUserMaxQuotaManager();
        perUserMaxQuotaManager.setDefaultMaxMessage(4096);
        perUserMaxQuotaManager.setDefaultMaxStorage(5L * 1024L * 1024L * 1024L);

        InMemoryCurrentQuotaManager currentQuotaManager = new InMemoryCurrentQuotaManager(
            new CurrentQuotaCalculator(factory, quotaRootResolver),
            mailboxManager);

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

        final ImapProcessor defaultImapProcessorFactory = DefaultImapProcessorFactory.createDefaultProcessor(mailboxManager, new StoreSubscriptionManager(factory), quotaManager, quotaRootResolver);
        configure(new DefaultImapDecoderFactory().buildImapDecoder(),
                new DefaultImapEncoderFactory().buildImapEncoder(),
                defaultImapProcessorFactory);
    }

    @Override
    public void createMailbox(MailboxPath mailboxPath) throws Exception{
        new MailboxCreationDelegate(mailboxManager).createMailbox(mailboxPath);
    }
}
