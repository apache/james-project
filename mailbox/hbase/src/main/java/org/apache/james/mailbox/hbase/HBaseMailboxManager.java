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
package org.apache.james.mailbox.hbase;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.hbase.mail.HBaseMailboxMapper;
import org.apache.james.mailbox.hbase.mail.model.HBaseMailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.transaction.TransactionalMapper;

/**
 * HBase implementation of {@link StoreMailboxManager}
 * 
 */
public class HBaseMailboxManager extends StoreMailboxManager<HBaseId> {

    public HBaseMailboxManager(HBaseMailboxSessionMapperFactory mapperFactory, Authenticator authenticator, MailboxPathLocker locker, MailboxACLResolver aclResolver, GroupMembershipResolver groupMembershipResolver) {
        super(mapperFactory, authenticator, locker, aclResolver, groupMembershipResolver);
    }

    public HBaseMailboxManager(HBaseMailboxSessionMapperFactory mapperFactory, Authenticator authenticator, MailboxACLResolver aclResolver, GroupMembershipResolver groupMembershipResolver) {
        super(mapperFactory, authenticator, new JVMMailboxPathLocker(), aclResolver, groupMembershipResolver);
    }

    @Override
    protected Mailbox<HBaseId> doCreateMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        return new HBaseMailbox(mailboxPath, randomUidValidity());
    }

    /**
     * Delete all mailboxes 
     * 
     * @param mailboxSession
     * @throws MailboxException
     */
    public void deleteEverything(MailboxSession mailboxSession) throws MailboxException {

        final HBaseMailboxMapper mapper = (HBaseMailboxMapper) getMapperFactory().getMailboxMapper(mailboxSession);

        mapper.execute(new TransactionalMapper.VoidTransaction() {

            @Override
            public void runVoid() throws MailboxException {
                mapper.deleteAllMemberships();
            }
        });
        mapper.execute(new TransactionalMapper.VoidTransaction() {

            @Override
            public void runVoid() throws MailboxException {
                mapper.deleteAllMailboxes();
            }
        });
    }

    @Override
    protected StoreMessageManager<HBaseId> createMessageManager(Mailbox<HBaseId> mailboxRow, MailboxSession session) throws MailboxException {
        StoreMessageManager<HBaseId> result = new HBaseMessageManager(getMapperFactory(),
            getMessageSearchIndex(),
            getEventDispatcher(),
            getLocker(),
            mailboxRow,
            getAclResolver(),
            getGroupMembershipResolver(),
            getQuotaManager(),
            getQuotaRootResolver());
        return result;
    }
}
