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

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SimpleMailboxACL;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;

/**
 * Cassandra implementation of {@link StoreMailboxManager}
 */
public class CassandraMailboxManager extends StoreMailboxManager<CassandraId> {
    private MailboxPathLocker locker;

    public CassandraMailboxManager(CassandraMailboxSessionMapperFactory mapperFactory, Authenticator authenticator, final MailboxPathLocker locker) {
        super(mapperFactory,
            authenticator,
            locker,
            new UnionMailboxACLResolver(),
            new SimpleGroupMembershipResolver());
        this.locker = locker;
    }

    @Override
    protected Mailbox<CassandraId> doCreateMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        SimpleMailbox<CassandraId> cassandraMailbox = new SimpleMailbox<>(mailboxPath, randomUidValidity());
        cassandraMailbox.setACL(SimpleMailboxACL.EMPTY);
        return cassandraMailbox;
    }

    @Override
    protected StoreMessageManager<CassandraId> createMessageManager(Mailbox<CassandraId> mailboxRow, MailboxSession session) throws MailboxException {
        return new CassandraMessageManager(getMapperFactory(),
            getMessageSearchIndex(),
            getEventDispatcher(),
            this.locker,
            mailboxRow,
            getQuotaManager(),
            getQuotaRootResolver());
    }

}
