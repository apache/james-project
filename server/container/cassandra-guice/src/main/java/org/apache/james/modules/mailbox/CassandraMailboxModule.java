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
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.CassandraSubscriptionManager;
import org.apache.james.mailbox.cassandra.mail.CassandraModSeqProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUidProvider;
import org.apache.james.mailbox.elasticsearch.events.ElasticSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.mail.MessageMapperFactory;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.search.MessageSearchIndex;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

public class CassandraMailboxModule extends AbstractModule {

    public static final String MAILBOXMANAGER_NAME = "mailboxmanager";

    @Override
    protected void configure() {
        bind(new TypeLiteral<MessageSearchIndex<CassandraId>>(){}).to(new TypeLiteral<ElasticSearchListeningMessageSearchIndex<CassandraId>>(){});

        bind(new TypeLiteral<MessageSearchIndex<CassandraId>>(){}).to(new TypeLiteral<ElasticSearchListeningMessageSearchIndex<CassandraId>>(){});

        bind(SubscriptionManager.class).to(CassandraSubscriptionManager.class);
        bind(new TypeLiteral<MessageMapperFactory<CassandraId>>(){}).to(CassandraMailboxSessionMapperFactory.class);
        bind(MailboxSessionMapperFactory.class).to(CassandraMailboxSessionMapperFactory.class);
        
        bind(MailboxPathLocker.class).to(NoMailboxPathLocker.class);
        bind(Authenticator.class).to(UserRepositoryAuthenticator.class);

        bind(new TypeLiteral<ModSeqProvider<CassandraId>>(){}).to(new TypeLiteral<CassandraModSeqProvider>(){});
        bind(new TypeLiteral<UidProvider<CassandraId>>(){}).to(new TypeLiteral<CassandraUidProvider>(){});
    }
    
    @Provides @Named(MAILBOXMANAGER_NAME) @Singleton
    public MailboxManager provideMailboxManager(CassandraMailboxManager cassandraMailboxManager) throws MailboxException {
        cassandraMailboxManager.init();
        return cassandraMailboxManager;
    }
}