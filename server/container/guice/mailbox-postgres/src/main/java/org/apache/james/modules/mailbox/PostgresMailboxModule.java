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
import org.apache.james.adapter.mailbox.MailboxUserDeletionTaskStep;
import org.apache.james.adapter.mailbox.MailboxUsernameChangeTaskStep;
import org.apache.james.adapter.mailbox.QuotaUsernameChangeTaskStep;
import org.apache.james.adapter.mailbox.UserRepositoryAuthenticator;
import org.apache.james.adapter.mailbox.UserRepositoryAuthorizator;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.events.EventListener;
import org.apache.james.mailbox.AttachmentContentLoader;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.postgres.JPAAttachmentContentLoader;
import org.apache.james.mailbox.postgres.PostgresMailboxAggregateModule;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMailboxSessionMapperFactory;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.PostgresMailboxManager;
import org.apache.james.mailbox.store.MailboxManagerConfiguration;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.event.MailboxAnnotationListener;
import org.apache.james.mailbox.store.event.MailboxSubscriptionListener;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.NaiveThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.mailbox.store.user.SubscriptionMapperFactory;
import org.apache.james.modules.BlobMemoryModule;
import org.apache.james.modules.data.JPAEntityManagerModule;
import org.apache.james.modules.data.PostgresCommonModule;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.apache.james.utils.MailboxManagerDefinition;
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
        install(new BlobMemoryModule());

        Multibinder<PostgresModule> postgresDataDefinitions = Multibinder.newSetBinder(binder(), PostgresModule.class);
        postgresDataDefinitions.addBinding().toInstance(PostgresMailboxAggregateModule.MODULE);

        install(new PostgresQuotaModule());
        install(new JPAQuotaSearchModule());
        install(new JPAEntityManagerModule());

        bind(PostgresMailboxSessionMapperFactory.class).in(Scopes.SINGLETON);
        bind(PostgresMailboxManager.class).in(Scopes.SINGLETON);
        bind(NoMailboxPathLocker.class).in(Scopes.SINGLETON);
        bind(StoreSubscriptionManager.class).in(Scopes.SINGLETON);
        bind(UserRepositoryAuthenticator.class).in(Scopes.SINGLETON);
        bind(UserRepositoryAuthorizator.class).in(Scopes.SINGLETON);
        bind(UnionMailboxACLResolver.class).in(Scopes.SINGLETON);
        bind(PostgresMessageId.Factory.class).in(Scopes.SINGLETON);
        bind(NaiveThreadIdGuessingAlgorithm.class).in(Scopes.SINGLETON);
        bind(ReIndexerImpl.class).in(Scopes.SINGLETON);
        bind(SessionProviderImpl.class).in(Scopes.SINGLETON);

        bind(SubscriptionMapperFactory.class).to(PostgresMailboxSessionMapperFactory.class);
        bind(MessageMapperFactory.class).to(PostgresMailboxSessionMapperFactory.class);
        bind(MailboxMapperFactory.class).to(PostgresMailboxSessionMapperFactory.class);
        bind(MailboxSessionMapperFactory.class).to(PostgresMailboxSessionMapperFactory.class);
        bind(MessageId.Factory.class).to(PostgresMessageId.Factory.class);
        bind(ThreadIdGuessingAlgorithm.class).to(NaiveThreadIdGuessingAlgorithm.class);

        bind(SubscriptionManager.class).to(StoreSubscriptionManager.class);
        bind(MailboxPathLocker.class).to(NoMailboxPathLocker.class);
        bind(Authenticator.class).to(UserRepositoryAuthenticator.class);
        bind(MailboxManager.class).to(PostgresMailboxManager.class);
        bind(StoreMailboxManager.class).to(PostgresMailboxManager.class);
        bind(SessionProvider.class).to(SessionProviderImpl.class);
        bind(Authorizator.class).to(UserRepositoryAuthorizator.class);
        bind(MailboxId.Factory.class).to(PostgresMailboxId.Factory.class);
        bind(MailboxACLResolver.class).to(UnionMailboxACLResolver.class);
        bind(AttachmentContentLoader.class).to(JPAAttachmentContentLoader.class);

        bind(ReIndexer.class).to(ReIndexerImpl.class);

        Multibinder.newSetBinder(binder(), MailboxManagerDefinition.class).addBinding().to(PostgresMailboxManagerDefinition.class);

        Multibinder.newSetBinder(binder(), EventListener.GroupEventListener.class)
            .addBinding()
            .to(MailboxAnnotationListener.class);

        Multibinder.newSetBinder(binder(), EventListener.GroupEventListener.class)
            .addBinding()
            .to(MailboxSubscriptionListener.class);

        bind(MailboxManager.class).annotatedWith(Names.named(MAILBOXMANAGER_NAME)).to(MailboxManager.class);
        bind(MailboxManagerConfiguration.class).toInstance(MailboxManagerConfiguration.DEFAULT);

        Multibinder<UsernameChangeTaskStep> usernameChangeTaskStepMultibinder = Multibinder.newSetBinder(binder(), UsernameChangeTaskStep.class);
        usernameChangeTaskStepMultibinder.addBinding().to(MailboxUsernameChangeTaskStep.class);
        usernameChangeTaskStepMultibinder.addBinding().to(ACLUsernameChangeTaskStep.class);
        usernameChangeTaskStepMultibinder.addBinding().to(QuotaUsernameChangeTaskStep.class);

        Multibinder<DeleteUserDataTaskStep> deleteUserDataTaskStepMultibinder = Multibinder.newSetBinder(binder(), DeleteUserDataTaskStep.class);
        deleteUserDataTaskStepMultibinder.addBinding().to(MailboxUserDeletionTaskStep.class);
    }

    @Singleton
    private static class PostgresMailboxManagerDefinition extends MailboxManagerDefinition {
        @Inject
        private PostgresMailboxManagerDefinition(PostgresMailboxManager manager) {
            super("postgres-mailboxmanager", manager);
        }
    }
}