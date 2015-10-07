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
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.quota.InMemoryCurrentQuotaManager;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.manager.IntegrationResources;
import org.apache.james.mailbox.manager.ManagerTestResources;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.MockAuthenticator;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.mailbox.store.quota.StoreQuotaManager;

public class InMemoryIntegrationResources implements IntegrationResources {

    private SimpleGroupMembershipResolver groupMembershipResolver;
    private DefaultQuotaRootResolver quotaRootResolver;

    @Override
    public MailboxManager createMailboxManager(GroupMembershipResolver groupMembershipResolver) throws MailboxException {
        MockAuthenticator mockAuthenticator = new MockAuthenticator();
        mockAuthenticator.addUser(ManagerTestResources.USER, ManagerTestResources.USER_PASS);
        MailboxSessionMapperFactory<InMemoryId> factory = new InMemoryMailboxSessionMapperFactory();
        final StoreMailboxManager<InMemoryId> manager = new StoreMailboxManager<InMemoryId>(
            factory,
            mockAuthenticator,
            new NoMailboxPathLocker(),
            new UnionMailboxACLResolver(),
            groupMembershipResolver);
        manager.init();
        return manager;
    }

    @SuppressWarnings("unchecked")
    @Override
    public QuotaManager createQuotaManager(MaxQuotaManager maxQuotaManager, MailboxManager mailboxManager) throws Exception {
        StoreQuotaManager quotaManager = new StoreQuotaManager();
        quotaManager.setCalculateWhenUnlimited(false);

        QuotaRootResolver quotaRootResolver =  createQuotaRootResolver(mailboxManager);

        InMemoryCurrentQuotaManager currentQuotaManager = new InMemoryCurrentQuotaManager(
            new CurrentQuotaCalculator(((StoreMailboxManager<InMemoryId>)mailboxManager).getMapperFactory(), quotaRootResolver),
            mailboxManager
        );

        ListeningCurrentQuotaUpdater listeningCurrentQuotaUpdater = new ListeningCurrentQuotaUpdater();
        listeningCurrentQuotaUpdater.setQuotaRootResolver(quotaRootResolver);
        listeningCurrentQuotaUpdater.setCurrentQuotaManager(currentQuotaManager);

        quotaManager.setCurrentQuotaManager(currentQuotaManager);
        quotaManager.setMaxQuotaManager(maxQuotaManager);
        ((StoreMailboxManager<InMemoryId>) mailboxManager).setQuotaManager(quotaManager);
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

    @SuppressWarnings("unchecked")
    @Override
    public DefaultQuotaRootResolver createQuotaRootResolver(MailboxManager mailboxManager) throws Exception {
        if (quotaRootResolver == null) {
            quotaRootResolver = new DefaultQuotaRootResolver(((StoreMailboxManager<InMemoryId>) mailboxManager).getMapperFactory());
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
