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

package org.apache.james.mailbox.cassandra;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraApplicableFlagDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraBlobsDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraDeletedMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraFirstUnseenDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxCounterDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxPathDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxRecentsDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraModSeqProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUidProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;

import com.datastax.driver.core.Session;
import com.google.common.base.Throwables;

public class CassandraMailboxManagerProvider {
    private static final int LIMIT_ANNOTATIONS = 3;
    private static final int LIMIT_ANNOTATION_SIZE = 30;

    public static CassandraMailboxManager provideMailboxManager(Session session, CassandraTypesProvider cassandraTypesProvider) {
        CassandraUidProvider uidProvider = new CassandraUidProvider(session);
        CassandraModSeqProvider modSeqProvider = new CassandraModSeqProvider(session);
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();
        CassandraMessageIdDAO messageIdDAO = new CassandraMessageIdDAO(session, messageIdFactory);
        CassandraMessageIdToImapUidDAO imapUidDAO = new CassandraMessageIdToImapUidDAO(session, messageIdFactory);
        CassandraBlobsDAO blobsDAO = new CassandraBlobsDAO(session);
        CassandraMessageDAO messageDAO = new CassandraMessageDAO(session, cassandraTypesProvider, blobsDAO);
        CassandraMailboxCounterDAO mailboxCounterDAO = new CassandraMailboxCounterDAO(session);
        CassandraMailboxRecentsDAO mailboxRecentsDAO = new CassandraMailboxRecentsDAO(session);
        CassandraMailboxDAO mailboxDAO = new CassandraMailboxDAO(session, cassandraTypesProvider);
        CassandraMailboxPathDAO mailboxPathDAO = new CassandraMailboxPathDAO(session, cassandraTypesProvider);
        CassandraFirstUnseenDAO firstUnseenDAO = new CassandraFirstUnseenDAO(session);
        CassandraApplicableFlagDAO applicableFlagDAO = new CassandraApplicableFlagDAO(session);
        CassandraDeletedMessageDAO deletedMessageDAO = new CassandraDeletedMessageDAO(session);

        CassandraMailboxSessionMapperFactory mapperFactory = new CassandraMailboxSessionMapperFactory(uidProvider,
            modSeqProvider,
            session,
            messageDAO,
            messageIdDAO,
            imapUidDAO,
            mailboxCounterDAO,
            mailboxRecentsDAO,
            mailboxDAO,
            mailboxPathDAO,
            firstUnseenDAO,
            applicableFlagDAO,
            deletedMessageDAO);

        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        MessageParser messageParser = new MessageParser();

        Authenticator noAuthenticator = null;
        Authorizator noAuthorizator = null;
        CassandraMailboxManager manager = new CassandraMailboxManager(mapperFactory, noAuthenticator, noAuthorizator, new NoMailboxPathLocker(), aclResolver, groupMembershipResolver,
            messageParser, messageIdFactory, LIMIT_ANNOTATIONS, LIMIT_ANNOTATION_SIZE);
        try {
            manager.init();
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }

        return manager;
    }

}
