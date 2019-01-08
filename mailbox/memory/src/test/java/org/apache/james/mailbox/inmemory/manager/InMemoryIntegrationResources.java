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
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.SessionProvider;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.DefaultDelegatingMailboxListener;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.quota.StoreQuotaManager;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;

public class InMemoryIntegrationResources implements IntegrationResources<StoreMailboxManager> {

    public static class Resources {
        private final InMemoryMailboxManager mailboxManager;
        private final StoreRightManager storeRightManager;
        private final MessageId.Factory messageIdFactory;

        Resources(InMemoryMailboxManager mailboxManager, StoreRightManager storeRightManager, MessageId.Factory messageIdFactory) {
            this.mailboxManager = mailboxManager;
            this.storeRightManager = storeRightManager;
            this.messageIdFactory = messageIdFactory;
        }

        public InMemoryMailboxManager getMailboxManager() {
            return mailboxManager;
        }

        public InMemoryCurrentQuotaManager getCurrentQuotaManager() {
            return (InMemoryCurrentQuotaManager) mailboxManager.getQuotaComponents().getMaxQuotaManager();
        }

        public StoreRightManager getStoreRightManager() {
            return storeRightManager;
        }

        public MessageId.Factory getMessageIdFactory() {
            return messageIdFactory;
        }
    }

    private SimpleGroupMembershipResolver groupMembershipResolver;

    @Override
    public InMemoryMailboxManager createMailboxManager(GroupMembershipResolver groupMembershipResolver) throws MailboxException {
        return createResources(groupMembershipResolver).mailboxManager;
    }

    public Resources createResources(GroupMembershipResolver groupMembershipResolver) throws MailboxException {
        return createResources(groupMembershipResolver,
            MailboxConstants.DEFAULT_LIMIT_ANNOTATIONS_ON_MAILBOX,
            MailboxConstants.DEFAULT_LIMIT_ANNOTATION_SIZE);
    }

    public Resources createResources(GroupMembershipResolver groupMembershipResolver,
                                     int limitAnnotationCount, int limitAnnotationSize) throws MailboxException {
        FakeAuthenticator fakeAuthenticator = new FakeAuthenticator();
        fakeAuthenticator.addUser(ManagerTestResources.USER, ManagerTestResources.USER_PASS);
        fakeAuthenticator.addUser(ManagerTestResources.OTHER_USER, ManagerTestResources.OTHER_USER_PASS);
        InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();
        DefaultDelegatingMailboxListener delegatingListener = new DefaultDelegatingMailboxListener();
        StoreRightManager storeRightManager = new StoreRightManager(mailboxSessionMapperFactory, new UnionMailboxACLResolver(),
            groupMembershipResolver, delegatingListener);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mailboxSessionMapperFactory, storeRightManager, limitAnnotationCount, limitAnnotationSize);

        SessionProvider sessionProvider = new SessionProvider(fakeAuthenticator, FakeAuthorizator.defaultReject());
        QuotaComponents quotaComponents = createQuotaComponents(mailboxSessionMapperFactory, delegatingListener, sessionProvider);
        MessageSearchIndex index = new SimpleMessageSearchIndex(mailboxSessionMapperFactory, mailboxSessionMapperFactory, new DefaultTextExtractor());

        InMemoryMailboxManager manager = new InMemoryMailboxManager(
            mailboxSessionMapperFactory,
            sessionProvider,
            new JVMMailboxPathLocker(),
            new MessageParser(),
            new InMemoryMessageId.Factory(),
            delegatingListener,
            annotationManager,
            storeRightManager,
            quotaComponents,
            index);
        manager.init();
        try {
            return new Resources(manager, storeRightManager, new InMemoryMessageId.Factory());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private QuotaComponents createQuotaComponents(InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory, DefaultDelegatingMailboxListener delegatingListener, SessionProvider sessionProvider) {
        MaxQuotaManager maxQuotaManager = new InMemoryPerUserMaxQuotaManager();
        QuotaRootResolver quotaRootResolver =  new DefaultUserQuotaRootResolver(sessionProvider, mailboxSessionMapperFactory);
        InMemoryCurrentQuotaManager currentQuotaManager = new InMemoryCurrentQuotaManager(new CurrentQuotaCalculator(mailboxSessionMapperFactory, quotaRootResolver), sessionProvider);
        StoreQuotaManager quotaManager = new StoreQuotaManager(currentQuotaManager, maxQuotaManager);
        ListeningCurrentQuotaUpdater listeningCurrentQuotaUpdater = new ListeningCurrentQuotaUpdater(currentQuotaManager, quotaRootResolver, delegatingListener, quotaManager);
        return new QuotaComponents(maxQuotaManager, quotaManager, quotaRootResolver, listeningCurrentQuotaUpdater);
    }

    public StoreMailboxManager createMailboxManager(GroupMembershipResolver groupMembershipResolver,
                                                    Authenticator authenticator, Authorizator authorizator) throws MailboxException {
        InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();
        DefaultDelegatingMailboxListener delegatingListener = new DefaultDelegatingMailboxListener();
        StoreRightManager storeRightManager = new StoreRightManager(mailboxSessionMapperFactory, new UnionMailboxACLResolver(), groupMembershipResolver, delegatingListener);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mailboxSessionMapperFactory, storeRightManager);

        SessionProvider sessionProvider = new SessionProvider(authenticator, authorizator);
        QuotaComponents quotaComponents = createQuotaComponents(mailboxSessionMapperFactory, delegatingListener, sessionProvider);

        MessageSearchIndex index = new SimpleMessageSearchIndex(mailboxSessionMapperFactory, mailboxSessionMapperFactory, new DefaultTextExtractor());

        StoreMailboxManager manager = new InMemoryMailboxManager(
            mailboxSessionMapperFactory,
            sessionProvider,
            new NoMailboxPathLocker(),
            new MessageParser(),
            new InMemoryMessageId.Factory(),
            delegatingListener,
            annotationManager,
            storeRightManager,
            quotaComponents,
            index);
        manager.init();
        return manager;
    }

    @Override
    public MessageIdManager createMessageIdManager(StoreMailboxManager mailboxManager) {
        return createMessageIdManager(mailboxManager, new InMemoryMessageId.Factory());
    }

    public MessageIdManager createMessageIdManager(StoreMailboxManager mailboxManager, MessageId.Factory factory) {
        QuotaComponents quotaComponents = mailboxManager.getQuotaComponents();
        return new StoreMessageIdManager(
            mailboxManager,
            mailboxManager.getMapperFactory(),
            mailboxManager.getDelegationListener(),
            factory,
            quotaComponents.getQuotaManager(),
            quotaComponents.getQuotaRootResolver());
    }
    
    @Override
    public QuotaManager retrieveQuotaManager(StoreMailboxManager mailboxManager) {
        return mailboxManager.getQuotaComponents().getQuotaManager();
    }

    @Override
    public MaxQuotaManager retrieveMaxQuotaManager(StoreMailboxManager mailboxManager) {
        return mailboxManager.getQuotaComponents().getMaxQuotaManager();
    }

    @Override
    public DefaultUserQuotaRootResolver retrieveQuotaRootResolver(StoreMailboxManager mailboxManager) {
        return (DefaultUserQuotaRootResolver) mailboxManager.getQuotaComponents().getQuotaRootResolver();
    }

    @Override
    public GroupMembershipResolver createGroupMembershipResolver() {
        groupMembershipResolver = new SimpleGroupMembershipResolver();
        return groupMembershipResolver;
    }

    @Override
    public void init() {
    }

    @Override
    public void clean() {
    }

}
