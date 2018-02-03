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

package org.apache.james.modules.mailbox;

import javax.inject.Singleton;

import org.apache.james.adapter.mailbox.store.UserRepositoryAuthenticator;
import org.apache.james.adapter.mailbox.store.UserRepositoryAuthorizator;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.BlobManager;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.RightManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.JsoupTextExtractor;
import org.apache.james.mailbox.inmemory.mail.InMemoryModSeqProvider;
import org.apache.james.mailbox.inmemory.mail.InMemoryUidProvider;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.StoreAttachmentManager;
import org.apache.james.mailbox.store.StoreBlobManager;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.mailbox.store.user.SubscriptionMapperFactory;
import org.apache.james.modules.Names;
import org.apache.james.utils.MailboxManagerDefinition;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;

public class MemoryMailboxModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new DefaultEventModule());
        install(new MemoryQuotaModule());

        bind(MessageMapperFactory.class).to(InMemoryMailboxSessionMapperFactory.class);
        bind(MailboxMapperFactory.class).to(InMemoryMailboxSessionMapperFactory.class);
        bind(AttachmentMapperFactory.class).to(InMemoryMailboxSessionMapperFactory.class);
        bind(MailboxSessionMapperFactory.class).to(InMemoryMailboxSessionMapperFactory.class);
        bind(ModSeqProvider.class).to(InMemoryModSeqProvider.class);
        bind(UidProvider.class).to(InMemoryUidProvider.class);
        bind(MailboxId.Factory.class).to(InMemoryId.Factory.class);
        bind(MessageId.Factory.class).to(InMemoryMessageId.Factory.class);

        bind(BlobManager.class).to(StoreBlobManager.class);
        bind(SubscriptionManager.class).to(StoreSubscriptionManager.class);
        bind(SubscriptionMapperFactory.class).to(InMemoryMailboxSessionMapperFactory.class);
        bind(MailboxSessionMapperFactory.class).to(InMemoryMailboxSessionMapperFactory.class);
        bind(MailboxPathLocker.class).to(JVMMailboxPathLocker.class);
        bind(Authenticator.class).to(UserRepositoryAuthenticator.class);
        bind(Authorizator.class).to(UserRepositoryAuthorizator.class);
        bind(MailboxManager.class).to(InMemoryMailboxManager.class);
        bind(MessageIdManager.class).to(StoreMessageIdManager.class);
        bind(AttachmentManager.class).to(StoreAttachmentManager.class);

        bind(MessageSearchIndex.class).to(SimpleMessageSearchIndex.class);
        bind(TextExtractor.class).to(JsoupTextExtractor.class);
        bind(RightManager.class).to(StoreRightManager.class);

        bind(StoreBlobManager.class).in(Scopes.SINGLETON);
        bind(InMemoryMailboxSessionMapperFactory.class).in(Scopes.SINGLETON);
        bind(InMemoryModSeqProvider.class).in(Scopes.SINGLETON);
        bind(InMemoryUidProvider.class).in(Scopes.SINGLETON);
        bind(StoreSubscriptionManager.class).in(Scopes.SINGLETON);
        bind(JVMMailboxPathLocker.class).in(Scopes.SINGLETON);
        bind(UserRepositoryAuthenticator.class).in(Scopes.SINGLETON);
        bind(UserRepositoryAuthorizator.class).in(Scopes.SINGLETON);
        bind(InMemoryMailboxManager.class).in(Scopes.SINGLETON);
        bind(InMemoryMessageId.Factory.class).in(Scopes.SINGLETON);
        bind(StoreMessageIdManager.class).in(Scopes.SINGLETON);
        bind(MailboxEventDispatcher.class).in(Scopes.SINGLETON);
        bind(StoreAttachmentManager.class).in(Scopes.SINGLETON);
        bind(StoreRightManager.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), MailboxManagerDefinition.class)
            .addBinding()
            .to(MemoryMailboxManagerDefinition.class);
    }

    @Provides
    @Named(Names.MAILBOXMANAGER_NAME)
    @Singleton
    public MailboxManager provideMailboxManager(InMemoryMailboxManager mailboxManager, ListeningCurrentQuotaUpdater quotaUpdater,
                                                QuotaManager quotaManager, QuotaRootResolver quotaRootResolver) throws MailboxException {
        mailboxManager.setQuotaRootResolver(quotaRootResolver);
        mailboxManager.setQuotaManager(quotaManager);
        mailboxManager.setQuotaUpdater(quotaUpdater);
        mailboxManager.init();
        return mailboxManager;
    }

    @Singleton
    private static class MemoryMailboxManagerDefinition extends MailboxManagerDefinition {
        @Inject
        private MemoryMailboxManagerDefinition(InMemoryMailboxManager manager) {
            super("memory-mailboxmanager", manager);
        }
    }
}