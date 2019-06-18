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

import org.apache.james.adapter.mailbox.store.UserRepositoryAuthenticator;
import org.apache.james.adapter.mailbox.store.UserRepositoryAuthorizator;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.JPAMailboxSessionMapperFactory;
import org.apache.james.mailbox.jpa.JPASubscriptionManager;
import org.apache.james.mailbox.jpa.mail.JPAModSeqProvider;
import org.apache.james.mailbox.jpa.mail.JPAUidProvider;
import org.apache.james.mailbox.jpa.openjpa.OpenJPAMailboxManager;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.MailboxManagerConfiguration;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.event.MailboxAnnotationListener;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.modules.data.JPAEntityManagerModule;
import org.apache.james.utils.MailboxManagerDefinition;
import org.apache.mailbox.tools.indexer.ReIndexerImpl;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class JPAMailboxModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new JpaQuotaModule());
        install(new JPAQuotaSearchModule());
        install(new JPAEntityManagerModule());

        bind(JPAMailboxSessionMapperFactory.class).in(Scopes.SINGLETON);
        bind(OpenJPAMailboxManager.class).in(Scopes.SINGLETON);
        bind(JVMMailboxPathLocker.class).in(Scopes.SINGLETON);
        bind(JPASubscriptionManager.class).in(Scopes.SINGLETON);
        bind(JPAModSeqProvider.class).in(Scopes.SINGLETON);
        bind(JPAUidProvider.class).in(Scopes.SINGLETON);
        bind(UserRepositoryAuthenticator.class).in(Scopes.SINGLETON);
        bind(UserRepositoryAuthorizator.class).in(Scopes.SINGLETON);
        bind(JPAId.Factory.class).in(Scopes.SINGLETON);
        bind(SimpleGroupMembershipResolver.class).in(Scopes.SINGLETON);
        bind(UnionMailboxACLResolver.class).in(Scopes.SINGLETON);
        bind(DefaultMessageId.Factory.class).in(Scopes.SINGLETON);
        bind(ReIndexerImpl.class).in(Scopes.SINGLETON);

        bind(MessageMapperFactory.class).to(JPAMailboxSessionMapperFactory.class);
        bind(MailboxMapperFactory.class).to(JPAMailboxSessionMapperFactory.class);
        bind(MailboxSessionMapperFactory.class).to(JPAMailboxSessionMapperFactory.class);
        bind(MessageId.Factory.class).to(DefaultMessageId.Factory.class);

        bind(ModSeqProvider.class).to(JPAModSeqProvider.class);
        bind(UidProvider.class).to(JPAUidProvider.class);
        bind(SubscriptionManager.class).to(JPASubscriptionManager.class);
        bind(MailboxPathLocker.class).to(JVMMailboxPathLocker.class);
        bind(Authenticator.class).to(UserRepositoryAuthenticator.class);
        bind(MailboxManager.class).to(OpenJPAMailboxManager.class);
        bind(StoreMailboxManager.class).to(OpenJPAMailboxManager.class);
        bind(Authorizator.class).to(UserRepositoryAuthorizator.class);
        bind(MailboxId.Factory.class).to(JPAId.Factory.class);
        bind(GroupMembershipResolver.class).to(SimpleGroupMembershipResolver.class);
        bind(MailboxACLResolver.class).to(UnionMailboxACLResolver.class);

        bind(ReIndexer.class).to(ReIndexerImpl.class);
        
        Multibinder.newSetBinder(binder(), MailboxManagerDefinition.class).addBinding().to(JPAMailboxManagerDefinition.class);

        Multibinder.newSetBinder(binder(), MailboxListener.GroupMailboxListener.class)
            .addBinding()
            .to(MailboxAnnotationListener.class);

        bind(MailboxManager.class).annotatedWith(Names.named(MAILBOXMANAGER_NAME)).to(MailboxManager.class);
        bind(MailboxManagerConfiguration.class).toInstance(MailboxManagerConfiguration.DEFAULT);
    }
    
    @Singleton
    private static class JPAMailboxManagerDefinition extends MailboxManagerDefinition {
        @Inject
        private JPAMailboxManagerDefinition(OpenJPAMailboxManager manager) {
            super("jpa-mailboxmanager", manager);
        }
    }
}