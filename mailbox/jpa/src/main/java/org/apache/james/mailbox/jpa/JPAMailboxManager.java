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

import java.util.EnumSet;

import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.mail.JPAMailboxMapper;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.transaction.Mapper;

/**
 * JPA implementation of {@link StoreMailboxManager}
 */
public abstract class JPAMailboxManager extends StoreMailboxManager {
    
    public JPAMailboxManager(JPAMailboxSessionMapperFactory mailboxSessionMapperFactory,
            Authenticator authenticator, Authorizator authorizator, MailboxPathLocker locker, MailboxACLResolver aclResolver, 
            GroupMembershipResolver groupMembershipResolver, MessageParser messageParser, MessageId.Factory messageIdFactory) {
        super(mailboxSessionMapperFactory, authenticator, authorizator, locker, aclResolver, groupMembershipResolver,
            messageParser, messageIdFactory);
    }

    public JPAMailboxManager(JPAMailboxSessionMapperFactory mailboxSessionMapperFactory,
                             Authenticator authenticator, Authorizator authorizator, MailboxPathLocker locker, MailboxACLResolver aclResolver,
                             GroupMembershipResolver groupMembershipResolver, MessageParser messageParser, MessageId.Factory messageIdFactory,
                             int limitAnnotation, int limitAnnotationSize) {
        super(mailboxSessionMapperFactory, authenticator, authorizator, locker, aclResolver, groupMembershipResolver,
            messageParser, messageIdFactory, limitAnnotation, limitAnnotationSize);
    }
    
    @Override
    protected Mailbox doCreateMailbox(MailboxPath path, MailboxSession session) throws MailboxException {
        return  new JPAMailbox(path, randomUidValidity());
    }

    @Override
    public EnumSet<MailboxCapabilities> getSupportedMailboxCapabilities() {
        return EnumSet.of(MailboxCapabilities.UserFlag, MailboxCapabilities.Namespace, MailboxCapabilities.Annotation);
    }

    /**
     * Delete all mailboxes 
     * 
     * @param mailboxSession
     * @throws MailboxException
     */
    public void deleteEverything(MailboxSession mailboxSession) throws MailboxException {
        final JPAMailboxMapper mapper = (JPAMailboxMapper) getMapperFactory().getMailboxMapper(mailboxSession);
        mapper.execute(Mapper.toTransaction(mapper::deleteAllMemberships));
        mapper.execute(Mapper.toTransaction(mapper::deleteAllMailboxes));
    }

}
