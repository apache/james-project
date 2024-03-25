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
import org.apache.james.adapter.mailbox.MailboxUserDeletionTaskStep;
import org.apache.james.adapter.mailbox.MailboxUsernameChangeTaskStep;
import org.apache.james.adapter.mailbox.QuotaUsernameChangeTaskStep;
import org.apache.james.adapter.mailbox.UserRepositoryAuthenticator;
import org.apache.james.adapter.mailbox.UserRepositoryAuthorizator;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.blob.api.BlobReferenceSource;
import org.apache.james.events.EventListener;
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
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.indexer.MessageIdReIndexer;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.postgres.DeleteMessageListener;
import org.apache.james.mailbox.postgres.PostgresMailboxAggregateModule;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMailboxManager;
import org.apache.james.mailbox.postgres.PostgresMailboxSessionMapperFactory;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.PostgresThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.postgres.mail.PostgresAttachmentBlobReferenceSource;
import org.apache.james.mailbox.postgres.mail.PostgresMessageBlobReferenceSource;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.apache.james.mailbox.store.MailboxManagerConfiguration;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreAttachmentManager;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.event.MailboxAnnotationListener;
import org.apache.james.mailbox.store.event.MailboxSubscriptionListener;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.user.SubscriptionMapperFactory;
import org.apache.james.modules.data.PostgresCommonModule;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.apache.james.utils.MailboxManagerDefinition;
import org.apache.mailbox.tools.indexer.MessageIdReIndexerImpl;
import org.apache.mailbox.tools.indexer.ReIndexerImpl;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class PostgresMailboxModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new PostgresCommonModule());

        Multibinder<PostgresModule> postgresDataDefinitions = Multibinder.newSetBinder(binder(), PostgresModule.class);
        postgresDataDefinitions.addBinding().toInstance(PostgresMailboxAggregateModule.MODULE);

        install(new PostgresQuotaModule());

        bind(PostgresMailboxSessionMapperFactory.class).in(Scopes.SINGLETON);
        bind(PostgresMailboxManager.class).in(Scopes.SINGLETON);
        bind(NoMailboxPathLocker.class).in(Scopes.SINGLETON);
        bind(StoreSubscriptionManager.class).in(Scopes.SINGLETON);
        bind(UserRepositoryAuthenticator.class).in(Scopes.SINGLETON);
        bind(UserRepositoryAuthorizator.class).in(Scopes.SINGLETON);
        bind(UnionMailboxACLResolver.class).in(Scopes.SINGLETON);
        bind(PostgresMessageId.Factory.class).in(Scopes.SINGLETON);
        bind(PostgresThreadIdGuessingAlgorithm.class).in(Scopes.SINGLETON);
        bind(ReIndexerImpl.class).in(Scopes.SINGLETON);
        bind(SessionProviderImpl.class).in(Scopes.SINGLETON);
        bind(StoreMessageIdManager.class).in(Scopes.SINGLETON);
        bind(StoreRightManager.class).in(Scopes.SINGLETON);

        bind(SubscriptionMapperFactory.class).to(PostgresMailboxSessionMapperFactory.class);
        bind(MessageMapperFactory.class).to(PostgresMailboxSessionMapperFactory.class);
        bind(MailboxMapperFactory.class).to(PostgresMailboxSessionMapperFactory.class);
        bind(MailboxSessionMapperFactory.class).to(PostgresMailboxSessionMapperFactory.class);
        bind(MessageId.Factory.class).to(PostgresMessageId.Factory.class);
        bind(ThreadIdGuessingAlgorithm.class).to(PostgresThreadIdGuessingAlgorithm.class);

        bind(SubscriptionManager.class).to(StoreSubscriptionManager.class);
        bind(MailboxPathLocker.class).to(NoMailboxPathLocker.class);
        bind(Authenticator.class).to(UserRepositoryAuthenticator.class);
        bind(MailboxManager.class).to(PostgresMailboxManager.class);
        bind(StoreMailboxManager.class).to(PostgresMailboxManager.class);
        bind(SessionProvider.class).to(SessionProviderImpl.class);
        bind(Authorizator.class).to(DelegationStoreAuthorizator.class);
        bind(MailboxId.Factory.class).to(PostgresMailboxId.Factory.class);
        bind(MailboxACLResolver.class).to(UnionMailboxACLResolver.class);
        bind(MessageIdManager.class).to(StoreMessageIdManager.class);
        bind(RightManager.class).to(StoreRightManager.class);
        bind(AttachmentManager.class).to(StoreAttachmentManager.class);
        bind(AttachmentContentLoader.class).to(AttachmentManager.class);
        bind(AttachmentMapperFactory.class).to(PostgresMailboxSessionMapperFactory.class);

        bind(ReIndexer.class).to(ReIndexerImpl.class);
        bind(MessageIdReIndexer.class).to(MessageIdReIndexerImpl.class);

        bind(PostgresMessageDAO.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), MailboxManagerDefinition.class).addBinding().to(PostgresMailboxManagerDefinition.class);

        Multibinder.newSetBinder(binder(), EventListener.GroupEventListener.class)
            .addBinding()
            .to(MailboxAnnotationListener.class);

        Multibinder.newSetBinder(binder(), EventListener.GroupEventListener.class)
            .addBinding()
            .to(MailboxSubscriptionListener.class);

        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class)
            .addBinding().to(DeleteMessageListener.class);
        Multibinder.newSetBinder(binder(), DeleteMessageListener.DeletionCallback.class);

        bind(MailboxManager.class).annotatedWith(Names.named(MAILBOXMANAGER_NAME)).to(MailboxManager.class);
        bind(MailboxManagerConfiguration.class).toInstance(MailboxManagerConfiguration.DEFAULT);

        Multibinder<UsernameChangeTaskStep> usernameChangeTaskStepMultibinder = Multibinder.newSetBinder(binder(), UsernameChangeTaskStep.class);
        usernameChangeTaskStepMultibinder.addBinding().to(MailboxUsernameChangeTaskStep.class);
        usernameChangeTaskStepMultibinder.addBinding().to(ACLUsernameChangeTaskStep.class);
        usernameChangeTaskStepMultibinder.addBinding().to(QuotaUsernameChangeTaskStep.class);

        Multibinder<DeleteUserDataTaskStep> deleteUserDataTaskStepMultibinder = Multibinder.newSetBinder(binder(), DeleteUserDataTaskStep.class);
        deleteUserDataTaskStepMultibinder.addBinding().to(MailboxUserDeletionTaskStep.class);

        Multibinder<BlobReferenceSource> blobReferenceSourceMultibinder = Multibinder.newSetBinder(binder(), BlobReferenceSource.class);
        blobReferenceSourceMultibinder.addBinding().to(PostgresMessageBlobReferenceSource.class);
        blobReferenceSourceMultibinder.addBinding().to(PostgresAttachmentBlobReferenceSource.class);
    }

    @Singleton
    private static class PostgresMailboxManagerDefinition extends MailboxManagerDefinition {
        @Inject
        private PostgresMailboxManagerDefinition(PostgresMailboxManager manager) {
            super("postgres-mailboxmanager", manager);
        }
    }
}