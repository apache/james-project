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

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.cassandra.mail.CassandraAnnotationMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraIndexTableHandler;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxCounterDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxRecentsDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageMapper;
import org.apache.james.mailbox.cassandra.user.CassandraSubscriptionMapper;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.user.SubscriptionMapper;

import com.datastax.driver.core.Session;

/**
 * Cassandra implementation of {@link MailboxSessionMapperFactory}
 * 
 */
public class CassandraMailboxSessionMapperFactory extends MailboxSessionMapperFactory {
    private static final int DEFAULT_MAX_RETRY = 1000;

    private final Session session;
    private final UidProvider uidProvider;
    private final ModSeqProvider modSeqProvider;
    private final CassandraTypesProvider typesProvider;
    private final CassandraMessageDAO messageDAO;
    private final CassandraMessageIdDAO messageIdDAO;
    private final CassandraMessageIdToImapUidDAO imapUidDAO;
    private final CassandraMailboxCounterDAO mailboxCounterDAO;
    private final CassandraMailboxRecentsDAO mailboxRecentsDAO;
    private final CassandraIndexTableHandler indexTableHandler;
    private int maxRetry;

    @Inject
    public CassandraMailboxSessionMapperFactory(UidProvider uidProvider, ModSeqProvider modSeqProvider, 
            Session session, CassandraTypesProvider typesProvider,
            CassandraMessageDAO messageDAO, CassandraMessageIdDAO messageIdDAO, CassandraMessageIdToImapUidDAO imapUidDAO,
            CassandraMailboxCounterDAO mailboxCounterDAO, CassandraMailboxRecentsDAO mailboxRecentsDAO) {
        this.uidProvider = uidProvider;
        this.modSeqProvider = modSeqProvider;
        this.session = session;
        this.messageDAO = messageDAO;
        this.messageIdDAO = messageIdDAO;
        this.imapUidDAO = imapUidDAO;
        this.mailboxCounterDAO = mailboxCounterDAO;
        this.mailboxRecentsDAO = mailboxRecentsDAO;
        this.indexTableHandler = new CassandraIndexTableHandler(mailboxRecentsDAO, mailboxCounterDAO);
        this.maxRetry = DEFAULT_MAX_RETRY;
        this.typesProvider = typesProvider;
    }

    public void setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
    }

    @Override
    public CassandraMessageMapper createMessageMapper(MailboxSession mailboxSession) {
        return new CassandraMessageMapper(uidProvider, modSeqProvider, null, maxRetry, createAttachmentMapper(mailboxSession),
                messageDAO, messageIdDAO, imapUidDAO, mailboxCounterDAO, mailboxRecentsDAO, indexTableHandler);
    }

    @Override
    public MessageIdMapper createMessageIdMapper(MailboxSession mailboxSession) throws MailboxException {
        return new CassandraMessageIdMapper(getMailboxMapper(mailboxSession), getAttachmentMapper(mailboxSession),
                imapUidDAO, messageIdDAO, messageDAO, indexTableHandler, modSeqProvider, mailboxSession);
    }

    @Override
    public MailboxMapper createMailboxMapper(MailboxSession mailboxSession) {
        return new CassandraMailboxMapper(session, typesProvider, maxRetry);
    }

    @Override
    public AttachmentMapper createAttachmentMapper(MailboxSession mailboxSession) {
        return new CassandraAttachmentMapper(session);
    }

    @Override
    public SubscriptionMapper createSubscriptionMapper(MailboxSession mailboxSession) {
        return new CassandraSubscriptionMapper(session);
    }

    public ModSeqProvider getModSeqProvider() {
        return modSeqProvider;
    }

    public UidProvider getUidProvider() {
        return uidProvider;
    }

    Session getSession() {
        return session;
    }

    @Override
    public AnnotationMapper createAnnotationMapper(MailboxSession mailboxSession)
            throws MailboxException {
        return new CassandraAnnotationMapper(session);
    }
}
