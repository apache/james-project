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
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.CassandraSubscriptionManager;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraBlobsDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraModSeqProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUidProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.BatchSizes;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.StoreAttachmentManager;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.modules.Names;
import org.apache.james.utils.MailboxManagerDefinition;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;

public class CassandraMailboxModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new DefaultEventModule());
        install(new CassandraQuotaModule());

        bind(CassandraMailboxSessionMapperFactory.class).in(Scopes.SINGLETON);
        bind(CassandraMailboxManager.class).in(Scopes.SINGLETON);
        bind(NoMailboxPathLocker.class).in(Scopes.SINGLETON);
        bind(CassandraSubscriptionManager.class).in(Scopes.SINGLETON);
        bind(CassandraModSeqProvider.class).in(Scopes.SINGLETON);
        bind(CassandraUidProvider.class).in(Scopes.SINGLETON);
        bind(UserRepositoryAuthenticator.class).in(Scopes.SINGLETON);
        bind(UserRepositoryAuthorizator.class).in(Scopes.SINGLETON);
        bind(CassandraId.Factory.class).in(Scopes.SINGLETON);
        bind(CassandraMessageId.Factory.class).in(Scopes.SINGLETON);
        bind(CassandraMessageIdDAO.class).in(Scopes.SINGLETON);
        bind(CassandraMessageIdToImapUidDAO.class).in(Scopes.SINGLETON);
        bind(MailboxEventDispatcher.class).in(Scopes.SINGLETON);
        bind(StoreMessageIdManager.class).in(Scopes.SINGLETON);
        bind(StoreAttachmentManager.class).in(Scopes.SINGLETON);
        bind(CassandraMailboxMapper.class).in(Scopes.SINGLETON);
        bind(CassandraMessageDAO.class).in(Scopes.SINGLETON);
        bind(CassandraBlobsDAO.class).in(Scopes.SINGLETON);

        bind(MessageMapperFactory.class).to(CassandraMailboxSessionMapperFactory.class);
        bind(MailboxMapperFactory.class).to(CassandraMailboxSessionMapperFactory.class);
        bind(AttachmentMapperFactory.class).to(CassandraMailboxSessionMapperFactory.class);
        bind(MailboxSessionMapperFactory.class).to(CassandraMailboxSessionMapperFactory.class);

        bind(ModSeqProvider.class).to(CassandraModSeqProvider.class);
        bind(UidProvider.class).to(CassandraUidProvider.class);
        bind(SubscriptionManager.class).to(CassandraSubscriptionManager.class);
        bind(MailboxPathLocker.class).to(NoMailboxPathLocker.class);
        bind(Authenticator.class).to(UserRepositoryAuthenticator.class);
        bind(Authorizator.class).to(UserRepositoryAuthorizator.class);
        bind(MailboxManager.class).to(CassandraMailboxManager.class);
        bind(MailboxId.Factory.class).to(CassandraId.Factory.class);
        bind(MessageId.Factory.class).to(CassandraMessageId.Factory.class);
        bind(MessageIdManager.class).to(StoreMessageIdManager.class);
        bind(AttachmentManager.class).to(StoreAttachmentManager.class);

        Multibinder<CassandraModule> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraDataDefinitions.addBinding().to(org.apache.james.mailbox.cassandra.modules.CassandraAclModule.class);
        cassandraDataDefinitions.addBinding().to(org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterModule.class);
        cassandraDataDefinitions.addBinding().to(org.apache.james.mailbox.cassandra.modules.CassandraMailboxRecentsModule.class);
        cassandraDataDefinitions.addBinding().to(org.apache.james.mailbox.cassandra.modules.CassandraFirstUnseenModule.class);
        cassandraDataDefinitions.addBinding().to(org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule.class);
        cassandraDataDefinitions.addBinding().to(org.apache.james.mailbox.cassandra.modules.CassandraMessageModule.class);
        cassandraDataDefinitions.addBinding().to(org.apache.james.mailbox.cassandra.modules.CassandraBlobModule.class);
        cassandraDataDefinitions.addBinding().to(org.apache.james.mailbox.cassandra.modules.CassandraSubscriptionModule.class);
        cassandraDataDefinitions.addBinding().to(org.apache.james.mailbox.cassandra.modules.CassandraUidModule.class);
        cassandraDataDefinitions.addBinding().to(org.apache.james.mailbox.cassandra.modules.CassandraModSeqModule.class);
        cassandraDataDefinitions.addBinding().to(org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule.class);
        cassandraDataDefinitions.addBinding().to(org.apache.james.mailbox.cassandra.modules.CassandraAnnotationModule.class);

        cassandraDataDefinitions.addBinding().to(org.apache.james.mailbox.cassandra.modules.CassandraApplicableFlagsModule.class);

        cassandraDataDefinitions.addBinding().to(org.apache.james.mailbox.cassandra.modules.CassandraDeletedMessageModule.class);


        Multibinder.newSetBinder(binder(), MailboxManagerDefinition.class).addBinding().to(CassandraMailboxManagerDefinition.class);
    }

    @Provides @Named(Names.MAILBOXMANAGER_NAME) @Singleton
    public MailboxManager provideMailboxManager(CassandraMailboxManager cassandraMailboxManager, ListeningCurrentQuotaUpdater quotaUpdater,
                                                QuotaManager quotaManager, QuotaRootResolver quotaRootResolver, BatchSizes batchSizes) throws MailboxException {
        cassandraMailboxManager.setQuotaUpdater(quotaUpdater);
        cassandraMailboxManager.setQuotaManager(quotaManager);
        cassandraMailboxManager.setQuotaRootResolver(quotaRootResolver);
        cassandraMailboxManager.setBatchSizes(batchSizes);
        cassandraMailboxManager.init();
        return cassandraMailboxManager;
    }
    
    @Singleton
    private static class CassandraMailboxManagerDefinition extends MailboxManagerDefinition {
        @Inject
        private CassandraMailboxManagerDefinition(CassandraMailboxManager manager) {
            super("cassandra-mailboxmanager", manager);
        }
    }
    

}
