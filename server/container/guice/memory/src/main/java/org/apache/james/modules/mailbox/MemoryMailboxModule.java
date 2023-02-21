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

import static org.apache.james.modules.Names.MAILBOXMANAGER_NAME;

import javax.inject.Singleton;

import org.apache.james.adapter.mailbox.ACLUsernameChangeTaskStep;
import org.apache.james.adapter.mailbox.DelegationStoreAuthorizator;
import org.apache.james.adapter.mailbox.MailboxUsernameChangeTaskStep;
import org.apache.james.adapter.mailbox.UserRepositoryAuthenticator;
import org.apache.james.events.EventListener;
import org.apache.james.jmap.api.change.EmailChangeRepository;
import org.apache.james.jmap.api.change.Limit;
import org.apache.james.jmap.api.change.MailboxChangeRepository;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.memory.change.MemoryEmailChangeRepository;
import org.apache.james.jmap.memory.change.MemoryMailboxChangeRepository;
import org.apache.james.mailbox.AttachmentContentLoader;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.RightManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.mail.InMemoryModSeqProvider;
import org.apache.james.mailbox.inmemory.mail.InMemoryUidProvider;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.MailboxManagerConfiguration;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreAttachmentManager;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.event.MailboxAnnotationListener;
import org.apache.james.mailbox.store.extractor.JsoupTextExtractor;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.SearchThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.mailbox.store.user.SubscriptionMapperFactory;
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.apache.james.utils.MailboxManagerDefinition;
import org.apache.james.vault.memory.metadata.MemoryDeletedMessageMetadataVault;
import org.apache.james.vault.metadata.DeletedMessageMetadataVault;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class MemoryMailboxModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new DefaultEventModule());
        install(new MemoryDeadLetterModule());
        install(new MemoryQuotaModule());
        install(new MemoryQuotaSearchModule());

        bind(MessageMapperFactory.class).to(InMemoryMailboxSessionMapperFactory.class);
        bind(MailboxMapperFactory.class).to(InMemoryMailboxSessionMapperFactory.class);
        bind(AttachmentMapperFactory.class).to(InMemoryMailboxSessionMapperFactory.class);
        bind(MailboxSessionMapperFactory.class).to(InMemoryMailboxSessionMapperFactory.class);
        bind(ModSeqProvider.class).to(InMemoryModSeqProvider.class);
        bind(UidProvider.class).to(InMemoryUidProvider.class);
        bind(MailboxId.Factory.class).to(InMemoryId.Factory.class);
        bind(MessageId.Factory.class).to(InMemoryMessageId.Factory.class);
        bind(ThreadIdGuessingAlgorithm.class).to(SearchThreadIdGuessingAlgorithm.class);
        bind(State.Factory.class).to(State.DefaultFactory.class);

        bind(SubscriptionManager.class).to(StoreSubscriptionManager.class);
        bind(SubscriptionMapperFactory.class).to(InMemoryMailboxSessionMapperFactory.class);
        bind(MailboxSessionMapperFactory.class).to(InMemoryMailboxSessionMapperFactory.class);
        bind(MailboxPathLocker.class).to(JVMMailboxPathLocker.class);
        bind(Authenticator.class).to(UserRepositoryAuthenticator.class);
        bind(Authorizator.class).to(DelegationStoreAuthorizator.class);
        bind(MailboxManager.class).to(InMemoryMailboxManager.class);
        bind(StoreMailboxManager.class).to(InMemoryMailboxManager.class);
        bind(MailboxChangeRepository.class).to(MemoryMailboxChangeRepository.class);
        bind(EmailChangeRepository.class).to(MemoryEmailChangeRepository.class);
        bind(MessageIdManager.class).to(StoreMessageIdManager.class);
        bind(AttachmentManager.class).to(StoreAttachmentManager.class);
        bind(SessionProvider.class).to(SessionProviderImpl.class);

        bind(MessageSearchIndex.class).to(SimpleMessageSearchIndex.class);
        bind(TextExtractor.class).to(JsoupTextExtractor.class);
        bind(RightManager.class).to(StoreRightManager.class);
        bind(AttachmentContentLoader.class).to(AttachmentManager.class);

        bind(DeletedMessageMetadataVault.class).to(MemoryDeletedMessageMetadataVault.class);

        bind(InMemoryMailboxSessionMapperFactory.class).in(Scopes.SINGLETON);
        bind(InMemoryModSeqProvider.class).in(Scopes.SINGLETON);
        bind(InMemoryUidProvider.class).in(Scopes.SINGLETON);
        bind(StoreSubscriptionManager.class).in(Scopes.SINGLETON);
        bind(JVMMailboxPathLocker.class).in(Scopes.SINGLETON);
        bind(UserRepositoryAuthenticator.class).in(Scopes.SINGLETON);
        bind(DelegationStoreAuthorizator.class).in(Scopes.SINGLETON);
        bind(InMemoryMailboxManager.class).in(Scopes.SINGLETON);
        bind(MemoryMailboxChangeRepository.class).in(Scopes.SINGLETON);
        bind(MemoryEmailChangeRepository.class).in(Scopes.SINGLETON);
        bind(InMemoryMessageId.Factory.class).in(Scopes.SINGLETON);
        bind(StoreMessageIdManager.class).in(Scopes.SINGLETON);
        bind(StoreAttachmentManager.class).in(Scopes.SINGLETON);
        bind(StoreRightManager.class).in(Scopes.SINGLETON);
        bind(MemoryDeletedMessageMetadataVault.class).in(Scopes.SINGLETON);
        bind(SessionProviderImpl.class).in(Scopes.SINGLETON);

        bind(Limit.class).annotatedWith(Names.named(MemoryEmailChangeRepository.LIMIT_NAME)).toInstance(Limit.of(256));
        bind(Limit.class).annotatedWith(Names.named(MemoryMailboxChangeRepository.LIMIT_NAME)).toInstance(Limit.of(256));

        Multibinder.newSetBinder(binder(), MailboxManagerDefinition.class)
            .addBinding()
            .to(MemoryMailboxManagerDefinition.class);

        Multibinder.newSetBinder(binder(), EventListener.GroupEventListener.class)
            .addBinding()
            .to(MailboxAnnotationListener.class);

        bind(MailboxManager.class).annotatedWith(Names.named(MAILBOXMANAGER_NAME)).to(MailboxManager.class);
        bind(MailboxManagerConfiguration.class).toInstance(MailboxManagerConfiguration.DEFAULT);

        Multibinder<UsernameChangeTaskStep> usernameChangeTaskStepMultibinder = Multibinder.newSetBinder(binder(), UsernameChangeTaskStep.class);
        usernameChangeTaskStepMultibinder.addBinding().to(MailboxUsernameChangeTaskStep.class);
        usernameChangeTaskStepMultibinder.addBinding().to(ACLUsernameChangeTaskStep.class);
    }

    @Singleton
    private static class MemoryMailboxManagerDefinition extends MailboxManagerDefinition {
        @Inject
        private MemoryMailboxManagerDefinition(InMemoryMailboxManager manager) {
            super("memory-mailboxmanager", manager);
        }
    }
}