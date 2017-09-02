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

import java.util.EnumSet;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.hbase.mail.HBaseMailboxMapper;
import org.apache.james.mailbox.hbase.mail.model.HBaseMailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.transaction.TransactionalMapper;

/**
 * HBase implementation of {@link StoreMailboxManager}
 * 
 */
public class HBaseMailboxManager extends StoreMailboxManager {

    public HBaseMailboxManager(HBaseMailboxSessionMapperFactory mapperFactory, Authenticator authenticator, Authorizator authorizator,
            MailboxPathLocker locker, MailboxACLResolver aclResolver, GroupMembershipResolver groupMembershipResolver, 
            MessageParser messageParser, MessageId.Factory messageIdFactory) {
        super(mapperFactory, authenticator, authorizator, locker, aclResolver, groupMembershipResolver, messageParser, messageIdFactory);
    }

    public HBaseMailboxManager(HBaseMailboxSessionMapperFactory mapperFactory, Authenticator authenticator, Authorizator authorizator,
            MailboxACLResolver aclResolver, GroupMembershipResolver groupMembershipResolver, 
            MessageParser messageParser, MessageId.Factory messageIdFactory) {
        super(mapperFactory, authenticator, authorizator, new JVMMailboxPathLocker(), aclResolver, groupMembershipResolver, messageParser, messageIdFactory);
    }

    @Override
    protected Mailbox doCreateMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
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
    public EnumSet<MailboxCapabilities> getSupportedMailboxCapabilities() {
        return EnumSet.of(MailboxCapabilities.Namespace);
    }

    @Override
    protected StoreMessageManager createMessageManager(Mailbox mailboxRow, MailboxSession session) throws MailboxException {
        return new HBaseMessageManager(getMapperFactory(),
            getMessageSearchIndex(),
            getEventDispatcher(),
            getLocker(),
            mailboxRow,
            getAclResolver(),
            getGroupMembershipResolver(),
            getQuotaManager(),
            getQuotaRootResolver(),
            getMessageParser(),
            getMessageIdFactory(),
            getBatchSizes(),
            getImmutableMailboxMessageFactory());
    }
}
