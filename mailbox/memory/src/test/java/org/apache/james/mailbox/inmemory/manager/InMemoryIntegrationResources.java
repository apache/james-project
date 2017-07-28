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

package org.apache.james.mailbox.inmemory.manager;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.InMemoryMessageIdManager;
import org.apache.james.mailbox.inmemory.quota.InMemoryCurrentQuotaManager;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.manager.IntegrationResources;
import org.apache.james.mailbox.manager.ManagerTestResources;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.mailbox.store.quota.StoreQuotaManager;

public class InMemoryIntegrationResources implements IntegrationResources {

    private SimpleGroupMembershipResolver groupMembershipResolver;
    private DefaultQuotaRootResolver quotaRootResolver;

    @Override
    public MailboxManager createMailboxManager(GroupMembershipResolver groupMembershipResolver) throws MailboxException {
        FakeAuthenticator fakeAuthenticator = new FakeAuthenticator();
        fakeAuthenticator.addUser(ManagerTestResources.USER, ManagerTestResources.USER_PASS);
        InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();
        final StoreMailboxManager manager = new InMemoryMailboxManager(
            mailboxSessionMapperFactory,
            fakeAuthenticator,
            FakeAuthorizator.defaultReject(),
            new NoMailboxPathLocker(),
            new UnionMailboxACLResolver(),
            groupMembershipResolver,
            new MessageParser(),
            new InMemoryMessageId.Factory());
        manager.init();
        return manager;
    }

    @Override
    public MessageIdManager createMessageIdManager(MailboxManager mailboxManager) {
        return new InMemoryMessageIdManager(mailboxManager);
    }
    
    @Override
    public QuotaManager createQuotaManager(MaxQuotaManager maxQuotaManager, MailboxManager mailboxManager) throws Exception {

        QuotaRootResolver quotaRootResolver =  createQuotaRootResolver(mailboxManager);

        InMemoryCurrentQuotaManager currentQuotaManager = new InMemoryCurrentQuotaManager(
            new CurrentQuotaCalculator(((StoreMailboxManager)mailboxManager).getMapperFactory(), quotaRootResolver),
            mailboxManager
        );

        ListeningCurrentQuotaUpdater listeningCurrentQuotaUpdater = new ListeningCurrentQuotaUpdater(currentQuotaManager, quotaRootResolver);
        StoreQuotaManager quotaManager = new StoreQuotaManager(currentQuotaManager, maxQuotaManager);
        quotaManager.setCalculateWhenUnlimited(false);
        ((StoreMailboxManager) mailboxManager).setQuotaManager(quotaManager);
        mailboxManager.addGlobalListener(listeningCurrentQuotaUpdater, null);
        return quotaManager;
    }

    @Override
    public MaxQuotaManager createMaxQuotaManager() throws Exception {
        return new InMemoryPerUserMaxQuotaManager();
    }

    @Override
    public GroupMembershipResolver createGroupMembershipResolver() throws Exception {
        groupMembershipResolver = new SimpleGroupMembershipResolver();
        return groupMembershipResolver;
    }

    @Override
    public DefaultQuotaRootResolver createQuotaRootResolver(MailboxManager mailboxManager) throws Exception {
        if (quotaRootResolver == null) {
            quotaRootResolver = new DefaultQuotaRootResolver(((StoreMailboxManager) mailboxManager).getMapperFactory());
        }
        return quotaRootResolver;
    }

    @Override
    public void init() throws MailboxException {
    }

    @Override
    public void clean() throws MailboxException {
    }

}
