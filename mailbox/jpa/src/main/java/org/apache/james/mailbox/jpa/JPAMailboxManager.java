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
package org.apache.james.mailbox.jpa;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.mail.JPAMailboxMapper;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.transaction.TransactionalMapper;

/**
 * JPA implementation of {@link StoreMailboxManager}
 */
public abstract class JPAMailboxManager extends StoreMailboxManager<JPAId> {
    
    public JPAMailboxManager(JPAMailboxSessionMapperFactory mailboxSessionMapperFactory,
            final Authenticator authenticator, final MailboxPathLocker locker, MailboxACLResolver aclResolver, GroupMembershipResolver groupMembershipResolver) {
        super(mailboxSessionMapperFactory, authenticator, locker, aclResolver, groupMembershipResolver);
    }
    
    @Override
    protected Mailbox<JPAId> doCreateMailbox(MailboxPath path, MailboxSession session) throws MailboxException {
        return  new JPAMailbox(path, randomUidValidity());
    }

    /**
     * Delete all mailboxes 
     * 
     * @param mailboxSession
     * @throws MailboxException
     */
    public void deleteEverything(MailboxSession mailboxSession) throws MailboxException {
        final JPAMailboxMapper mapper = (JPAMailboxMapper) getMapperFactory().getMailboxMapper(mailboxSession);
        mapper.execute(new TransactionalMapper.VoidTransaction() {
            public void runVoid() throws MailboxException {
                mapper.deleteAllMemberships(); 
            }
        });
        mapper.execute(new TransactionalMapper.VoidTransaction() {
            public void runVoid() throws MailboxException {
                mapper.deleteAllMailboxes(); 
            }
        });
    }

}
