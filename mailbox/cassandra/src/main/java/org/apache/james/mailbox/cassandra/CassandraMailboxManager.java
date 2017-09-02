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

import java.util.EnumSet;

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SimpleMailboxACL;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.event.DelegatingMailboxListener;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.mailbox.store.search.MessageSearchIndex;

/**
 * Cassandra implementation of {@link StoreMailboxManager}
 */
public class CassandraMailboxManager extends StoreMailboxManager {
    private final MailboxPathLocker locker;

    @Inject
    public CassandraMailboxManager(CassandraMailboxSessionMapperFactory mapperFactory, Authenticator authenticator, Authorizator authorizator,
                                   MailboxPathLocker locker, MessageParser messageParser, MessageId.Factory messageIdFactory,
                                   MailboxEventDispatcher mailboxEventDispatcher, DelegatingMailboxListener delegatingMailboxListener) {
        super(mapperFactory,
            authenticator,
            authorizator,
            locker,
            new UnionMailboxACLResolver(),
            new SimpleGroupMembershipResolver(),
            messageParser,
            messageIdFactory,
            MailboxConstants.DEFAULT_LIMIT_ANNOTATIONS_ON_MAILBOX,
            MailboxConstants.DEFAULT_LIMIT_ANNOTATION_SIZE,
            mailboxEventDispatcher,
            delegatingMailboxListener);
        this.locker = locker;
    }

    public CassandraMailboxManager(CassandraMailboxSessionMapperFactory mapperFactory, Authenticator authenticator, Authorizator authorizator,
                                   MailboxPathLocker locker, MessageParser messageParser, MessageId.Factory messageIdFactory) {
        super(mapperFactory,
            authenticator,
            authorizator,
            locker,
            new UnionMailboxACLResolver(),
            new SimpleGroupMembershipResolver(),
            messageParser,
            messageIdFactory);
        this.locker = locker;
    }

    public CassandraMailboxManager(CassandraMailboxSessionMapperFactory mapperFactory, Authenticator authenticator,  Authorizator authorizator,
            MailboxPathLocker locker, MailboxACLResolver aclResolver, GroupMembershipResolver groupMembershipResolver, MessageParser messageParser,
            MessageId.Factory messageIdFactory, int limitOfAnnotations, int limitAnnotationSize) {
        super(mapperFactory,
            authenticator,
            authorizator,
            aclResolver,
            groupMembershipResolver,
            messageParser,
            messageIdFactory,
            limitOfAnnotations,
            limitAnnotationSize);
        this.locker = locker;
    }

    @Override
    @Inject
    public void setMessageSearchIndex(MessageSearchIndex index) {
        super.setMessageSearchIndex(index);
    }

    @Override
    public EnumSet<MailboxManager.MailboxCapabilities> getSupportedMailboxCapabilities() {
        return EnumSet.of(MailboxCapabilities.Move, MailboxCapabilities.UserFlag, MailboxCapabilities.Namespace, MailboxCapabilities.Annotation);
    }

    @Override
    public EnumSet<MessageCapabilities> getSupportedMessageCapabilities() {
        return EnumSet.of(MessageCapabilities.Attachment, MessageCapabilities.UniqueID);
    }
    
    @Override
    protected Mailbox doCreateMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        SimpleMailbox cassandraMailbox = new SimpleMailbox(mailboxPath, randomUidValidity());
        cassandraMailbox.setACL(SimpleMailboxACL.EMPTY);
        return cassandraMailbox;
    }

    @Override
    protected StoreMessageManager createMessageManager(Mailbox mailboxRow, MailboxSession session) throws MailboxException {
        return new CassandraMessageManager(getMapperFactory(),
            getMessageSearchIndex(),
            getEventDispatcher(),
            this.locker,
            mailboxRow,
            getQuotaManager(),
            getQuotaRootResolver(),
            getMessageParser(),
            getMessageIdFactory(),
            getBatchSizes(),
            getImmutableMailboxMessageFactory());
    }

}
