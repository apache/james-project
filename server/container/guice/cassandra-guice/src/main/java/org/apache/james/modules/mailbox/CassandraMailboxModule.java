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

import org.apache.james.adapter.mailbox.UserRepositoryAuthenticator;
import org.apache.james.adapter.mailbox.UserRepositoryAuthorizator;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.mailbox.AttachmentContentLoader;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.Authenticator;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.BlobManager;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.RightManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.DeleteMessageListener;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraACLDAOV1;
import org.apache.james.mailbox.cassandra.mail.CassandraACLDAOV2;
import org.apache.james.mailbox.cassandra.mail.CassandraACLMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraApplicableFlagDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentOwnerDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraDeletedMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraFirstUnseenDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxCounterDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathDAOImpl;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathV2DAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathV3DAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxRecentsDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV3;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraModSeqProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUidProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUserMailboxRightsDAO;
import org.apache.james.mailbox.cassandra.mail.eventsourcing.acl.ACLModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAnnotationModule;
import org.apache.james.mailbox.cassandra.modules.CassandraApplicableFlagsModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.cassandra.modules.CassandraDeletedMessageModule;
import org.apache.james.mailbox.cassandra.modules.CassandraFirstUnseenModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxRecentsModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.cassandra.modules.CassandraModSeqModule;
import org.apache.james.mailbox.cassandra.modules.CassandraSubscriptionModule;
import org.apache.james.mailbox.cassandra.modules.CassandraUidModule;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.indexer.MessageIdReIndexer;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreAttachmentManager;
import org.apache.james.mailbox.store.StoreBlobManager;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.event.MailboxAnnotationListener;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.user.SubscriptionMapperFactory;
import org.apache.james.utils.MailboxManagerDefinition;
import org.apache.mailbox.tools.indexer.MessageIdReIndexerImpl;
import org.apache.mailbox.tools.indexer.ReIndexerImpl;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class CassandraMailboxModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new DefaultEventModule());
        install(new CassandraQuotaModule());
        install(new CassandraDeadLetterModule());

        bind(CassandraApplicableFlagDAO.class).in(Scopes.SINGLETON);
        bind(CassandraAttachmentDAOV2.class).in(Scopes.SINGLETON);
        bind(CassandraAttachmentMessageIdDAO.class).in(Scopes.SINGLETON);
        bind(CassandraAttachmentOwnerDAO.class).in(Scopes.SINGLETON);
        bind(CassandraDeletedMessageDAO.class).in(Scopes.SINGLETON);
        bind(CassandraFirstUnseenDAO.class).in(Scopes.SINGLETON);
        bind(CassandraMailboxCounterDAO.class).in(Scopes.SINGLETON);
        bind(CassandraMailboxDAO.class).in(Scopes.SINGLETON);
        bind(CassandraACLDAOV1.class).in(Scopes.SINGLETON);
        bind(CassandraACLDAOV2.class).in(Scopes.SINGLETON);
        bind(CassandraMailboxPathDAOImpl.class).in(Scopes.SINGLETON);
        bind(CassandraMailboxPathV2DAO.class).in(Scopes.SINGLETON);
        bind(CassandraMailboxPathV3DAO.class).in(Scopes.SINGLETON);
        bind(CassandraMailboxRecentsDAO.class).in(Scopes.SINGLETON);
        bind(CassandraMessageDAO.class).in(Scopes.SINGLETON);
        bind(CassandraMessageDAOV3.class).in(Scopes.SINGLETON);
        bind(CassandraMessageIdDAO.class).in(Scopes.SINGLETON);
        bind(CassandraMessageIdToImapUidDAO.class).in(Scopes.SINGLETON);
        bind(CassandraUserMailboxRightsDAO.class).in(Scopes.SINGLETON);

        bind(CassandraMailboxManager.class).in(Scopes.SINGLETON);
        bind(StoreSubscriptionManager.class).in(Scopes.SINGLETON);
        bind(StoreAttachmentManager.class).in(Scopes.SINGLETON);
        bind(StoreBlobManager.class).in(Scopes.SINGLETON);
        bind(StoreMessageIdManager.class).in(Scopes.SINGLETON);
        bind(StoreRightManager.class).in(Scopes.SINGLETON);
        bind(SessionProviderImpl.class).in(Scopes.SINGLETON);

        bind(CassandraACLMapper.class).in(Scopes.SINGLETON);
        bind(CassandraMailboxMapper.class).in(Scopes.SINGLETON);

        bind(CassandraId.Factory.class).in(Scopes.SINGLETON);
        bind(CassandraMailboxSessionMapperFactory.class).in(Scopes.SINGLETON);
        bind(CassandraMessageId.Factory.class).in(Scopes.SINGLETON);
        bind(CassandraModSeqProvider.class).in(Scopes.SINGLETON);
        bind(CassandraUidProvider.class).in(Scopes.SINGLETON);
        bind(NoMailboxPathLocker.class).in(Scopes.SINGLETON);
        bind(UserRepositoryAuthenticator.class).in(Scopes.SINGLETON);
        bind(UserRepositoryAuthorizator.class).in(Scopes.SINGLETON);

        bind(ReIndexerImpl.class).in(Scopes.SINGLETON);
        bind(MessageIdReIndexerImpl.class).in(Scopes.SINGLETON);

        bind(BlobManager.class).to(StoreBlobManager.class);
        bind(MessageMapperFactory.class).to(CassandraMailboxSessionMapperFactory.class);
        bind(MailboxMapperFactory.class).to(CassandraMailboxSessionMapperFactory.class);
        bind(AttachmentMapperFactory.class).to(CassandraMailboxSessionMapperFactory.class);
        bind(MailboxSessionMapperFactory.class).to(CassandraMailboxSessionMapperFactory.class);
        bind(SubscriptionMapperFactory.class).to(CassandraMailboxSessionMapperFactory.class);

        bind(ModSeqProvider.class).to(CassandraModSeqProvider.class);
        bind(UidProvider.class).to(CassandraUidProvider.class);
        bind(SubscriptionManager.class).to(StoreSubscriptionManager.class);
        bind(MailboxPathLocker.class).to(NoMailboxPathLocker.class);
        bind(Authenticator.class).to(UserRepositoryAuthenticator.class);
        bind(Authorizator.class).to(UserRepositoryAuthorizator.class);
        bind(MailboxManager.class).to(CassandraMailboxManager.class);
        bind(StoreMailboxManager.class).to(CassandraMailboxManager.class);
        bind(MailboxId.Factory.class).to(CassandraId.Factory.class);
        bind(MessageId.Factory.class).to(CassandraMessageId.Factory.class);
        bind(MessageIdManager.class).to(StoreMessageIdManager.class);
        bind(AttachmentManager.class).to(StoreAttachmentManager.class);
        bind(RightManager.class).to(StoreRightManager.class);
        bind(SessionProvider.class).to(SessionProviderImpl.class);
        bind(AttachmentContentLoader.class).to(AttachmentManager.class);

        bind(ReIndexer.class).to(ReIndexerImpl.class);
        bind(MessageIdReIndexer.class).to(MessageIdReIndexerImpl.class);

        Multibinder<CassandraModule> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraDataDefinitions.addBinding().toInstance(CassandraAclModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraAttachmentModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraMessageModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraMailboxCounterModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraAnnotationModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraApplicableFlagsModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraDeletedMessageModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraFirstUnseenModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraMailboxRecentsModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraModSeqModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraSubscriptionModule.MODULE);
        cassandraDataDefinitions.addBinding().toInstance(CassandraUidModule.MODULE);

        Multibinder.newSetBinder(binder(), MailboxManagerDefinition.class).addBinding().to(CassandraMailboxManagerDefinition.class);

        Multibinder<MailboxListener.GroupMailboxListener> mailboxListeners = Multibinder.newSetBinder(binder(), MailboxListener.GroupMailboxListener.class);
        mailboxListeners.addBinding().to(MailboxAnnotationListener.class);
        mailboxListeners.addBinding().to(DeleteMessageListener.class);

        bind(MailboxManager.class).annotatedWith(Names.named(MAILBOXMANAGER_NAME)).to(MailboxManager.class);

        Multibinder.newSetBinder(binder(), new TypeLiteral<EventDTOModule<? extends Event, ? extends EventDTO>>() {})
            .addBinding().toInstance(ACLModule.ACL_UPDATE);
    }
    
    @Singleton
    private static class CassandraMailboxManagerDefinition extends MailboxManagerDefinition {
        @Inject
        private CassandraMailboxManagerDefinition(CassandraMailboxManager manager) {
            super("cassandra-mailboxmanager", manager);
        }
    }
}
