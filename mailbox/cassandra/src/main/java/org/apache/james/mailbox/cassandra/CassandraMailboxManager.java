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
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.StoreRightManager;
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
    public static final EnumSet<MailboxCapabilities> MAILBOX_CAPABILITIES = EnumSet.of(
        MailboxCapabilities.Move,
        MailboxCapabilities.UserFlag,
        MailboxCapabilities.Namespace,
        MailboxCapabilities.Annotation,
        MailboxCapabilities.ACL,
        MailboxCapabilities.Quota);
    public static final EnumSet<MessageCapabilities> MESSAGE_CAPABILITIES = EnumSet.of(MessageCapabilities.Attachment, MessageCapabilities.UniqueID);

    private final MailboxPathLocker locker;
    private final CassandraMailboxSessionMapperFactory mapperFactory;

    @Inject
    public CassandraMailboxManager(CassandraMailboxSessionMapperFactory mapperFactory, Authenticator authenticator, Authorizator authorizator,
                                   MailboxPathLocker locker, MessageParser messageParser,
                                   MessageId.Factory messageIdFactory,
                                   MailboxEventDispatcher mailboxEventDispatcher, DelegatingMailboxListener delegatingMailboxListener,
                                   StoreMailboxAnnotationManager annotationManager, StoreRightManager storeRightManager) {
        super(mapperFactory,
            authenticator,
            authorizator,
            locker,
            messageParser,
            messageIdFactory,
            annotationManager,
            mailboxEventDispatcher,
            delegatingMailboxListener,
            storeRightManager);
        this.locker = locker;
        this.mapperFactory = mapperFactory;
    }

    @Override
    @Inject
    public void setMessageSearchIndex(MessageSearchIndex index) {
        super.setMessageSearchIndex(index);
    }

    @Override
    public EnumSet<MailboxManager.MailboxCapabilities> getSupportedMailboxCapabilities() {
        return MAILBOX_CAPABILITIES;
    }

    @Override
    public EnumSet<MessageCapabilities> getSupportedMessageCapabilities() {
        return MESSAGE_CAPABILITIES;
    }
    
    @Override
    protected Mailbox doCreateMailbox(MailboxPath mailboxPath, MailboxSession session) {
        SimpleMailbox cassandraMailbox = new SimpleMailbox(mailboxPath, randomUidValidity());
        cassandraMailbox.setACL(MailboxACL.EMPTY);
        return cassandraMailbox;
    }

    @Override
    protected StoreMessageManager createMessageManager(Mailbox mailboxRow, MailboxSession session) {
        return new CassandraMessageManager(mapperFactory,
            getMessageSearchIndex(),
            getEventDispatcher(),
            this.locker,
            mailboxRow,
            getQuotaManager(),
            getQuotaRootResolver(),
            getMessageParser(),
            getMessageIdFactory(),
            getBatchSizes(),
            getImmutableMailboxMessageFactory(),
            getStoreRightManager());
    }

}
