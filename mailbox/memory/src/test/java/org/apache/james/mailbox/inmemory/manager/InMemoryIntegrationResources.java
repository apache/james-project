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

import java.util.function.BiFunction;

import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.quota.InMemoryCurrentQuotaManager;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.manager.IntegrationResources;
import org.apache.james.mailbox.manager.ManagerTestResources;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.DefaultDelegatingMailboxListener;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.mailbox.store.quota.StoreQuotaManager;

import com.google.common.base.Throwables;

public class InMemoryIntegrationResources implements IntegrationResources<StoreMailboxManager> {

    private SimpleGroupMembershipResolver groupMembershipResolver;
    private DefaultUserQuotaRootResolver quotaRootResolver;
    private InMemoryCurrentQuotaManager currentQuotaManager;

    @Override
    public InMemoryMailboxManager createMailboxManager(GroupMembershipResolver groupMembershipResolver) throws MailboxException {
        return createMailboxManager(groupMembershipResolver,
            ((storeRightManager, inMemoryMailboxSessionMapperFactory) ->
                new StoreMailboxAnnotationManager(
                    inMemoryMailboxSessionMapperFactory,
                    storeRightManager)));
    }

    public InMemoryMailboxManager createMailboxManager(GroupMembershipResolver groupMembershipResolver,
                                                    int limitAnnotationCount, int limitAnnotationSize) throws MailboxException {
        return createMailboxManager(groupMembershipResolver,
            ((storeRightManager, inMemoryMailboxSessionMapperFactory) ->
             new StoreMailboxAnnotationManager(
                 inMemoryMailboxSessionMapperFactory,
                 storeRightManager,
                 limitAnnotationCount,
                 limitAnnotationSize)));
    }

    private InMemoryMailboxManager createMailboxManager(GroupMembershipResolver groupMembershipResolver,
                                                    BiFunction<StoreRightManager, InMemoryMailboxSessionMapperFactory, StoreMailboxAnnotationManager> annotationManagerBiFunction) throws MailboxException {
        FakeAuthenticator fakeAuthenticator = new FakeAuthenticator();
        fakeAuthenticator.addUser(ManagerTestResources.USER, ManagerTestResources.USER_PASS);
        fakeAuthenticator.addUser(ManagerTestResources.OTHER_USER, ManagerTestResources.OTHER_USER_PASS);
        InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();
        DefaultDelegatingMailboxListener delegatingListener = new DefaultDelegatingMailboxListener();
        MailboxEventDispatcher mailboxEventDispatcher = new MailboxEventDispatcher(delegatingListener);
        StoreRightManager storeRightManager = new StoreRightManager(mailboxSessionMapperFactory, new UnionMailboxACLResolver(),
                                                                    groupMembershipResolver, mailboxEventDispatcher);
        StoreMailboxAnnotationManager annotationManager = annotationManagerBiFunction
            .apply(storeRightManager, mailboxSessionMapperFactory);

        InMemoryMailboxManager manager = new InMemoryMailboxManager(
            mailboxSessionMapperFactory,
            fakeAuthenticator,
            FakeAuthorizator.defaultReject(),
            new JVMMailboxPathLocker(),
            new MessageParser(),
            new InMemoryMessageId.Factory(),
            mailboxEventDispatcher,
            delegatingListener,
            annotationManager,
            storeRightManager);
        manager.init();
        try {
            createQuotaManager(createMaxQuotaManager(), manager);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return manager;
    }

    public StoreMailboxManager createMailboxManager(GroupMembershipResolver groupMembershipResolver,
                                                    Authenticator authenticator, Authorizator authorizator) throws MailboxException {
        InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();
        DefaultDelegatingMailboxListener delegatingListener = new DefaultDelegatingMailboxListener();
        MailboxEventDispatcher mailboxEventDispatcher = new MailboxEventDispatcher(delegatingListener);
        StoreRightManager storeRightManager = new StoreRightManager(mailboxSessionMapperFactory, new UnionMailboxACLResolver(), groupMembershipResolver, mailboxEventDispatcher);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mailboxSessionMapperFactory, storeRightManager);

        StoreMailboxManager manager = new InMemoryMailboxManager(
            mailboxSessionMapperFactory,
            authenticator,
            authorizator,
            new NoMailboxPathLocker(),
            new MessageParser(),
            new InMemoryMessageId.Factory(),
            mailboxEventDispatcher,
            delegatingListener,
            annotationManager,
            storeRightManager);
        manager.init();
        return manager;
    }

    @Override
    public MessageIdManager createMessageIdManager(StoreMailboxManager mailboxManager) {
        return new StoreMessageIdManager(
            mailboxManager,
            mailboxManager.getMapperFactory(),
            mailboxManager.getEventDispatcher(),
            new InMemoryMessageId.Factory(),
            mailboxManager.getQuotaManager(),
            mailboxManager.getQuotaRootResolver());
    }
    
    @Override
    public QuotaManager createQuotaManager(MaxQuotaManager maxQuotaManager, StoreMailboxManager mailboxManager) throws Exception {

        QuotaRootResolver quotaRootResolver =  createQuotaRootResolver(mailboxManager);

        InMemoryCurrentQuotaManager currentQuotaManager = createCurrentQuotaManager(mailboxManager);

        StoreQuotaManager quotaManager = new StoreQuotaManager(currentQuotaManager, maxQuotaManager);
        ListeningCurrentQuotaUpdater listeningCurrentQuotaUpdater = new ListeningCurrentQuotaUpdater(currentQuotaManager, quotaRootResolver, mailboxManager.getEventDispatcher(), quotaManager);
        mailboxManager.setQuotaManager(quotaManager);
        mailboxManager.addGlobalListener(listeningCurrentQuotaUpdater, null);
        return quotaManager;
    }

    @Override
    public InMemoryCurrentQuotaManager createCurrentQuotaManager(StoreMailboxManager mailboxManager) {
        if (currentQuotaManager == null) {
            currentQuotaManager = new InMemoryCurrentQuotaManager(
                    new CurrentQuotaCalculator(mailboxManager.getMapperFactory(), createQuotaRootResolver(mailboxManager)),
                    mailboxManager);
        }
        return currentQuotaManager;
    }

    @Override
    public MaxQuotaManager createMaxQuotaManager() {
        return new InMemoryPerUserMaxQuotaManager();
    }

    @Override
    public GroupMembershipResolver createGroupMembershipResolver() {
        groupMembershipResolver = new SimpleGroupMembershipResolver();
        return groupMembershipResolver;
    }

    @Override
    public DefaultUserQuotaRootResolver createQuotaRootResolver(StoreMailboxManager mailboxManager) {
        if (quotaRootResolver == null) {
            quotaRootResolver = new DefaultUserQuotaRootResolver(mailboxManager.getMapperFactory());
        }
        return quotaRootResolver;
    }

    @Override
    public void init() {
    }

    @Override
    public void clean() {
    }

}
