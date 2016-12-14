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
package org.apache.james.mailbox.cassandra.mail;

import java.util.List;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.CassandraMessageId;
import org.apache.james.mailbox.cassandra.CassandraMessageId.Factory;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAnnotationModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.cassandra.modules.CassandraModSeqModule;
import org.apache.james.mailbox.cassandra.modules.CassandraUidModule;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MapperProvider;
import org.apache.james.mailbox.store.mail.model.MessageUidProvider;

import com.google.common.collect.ImmutableList;

public class CassandraMapperProvider implements MapperProvider {

    private static final Factory MESSAGE_ID_FACTORY = new CassandraMessageId.Factory();
    private static final CassandraCluster cassandra = CassandraCluster.create(new CassandraModuleComposite(
        new CassandraAclModule(),
        new CassandraMailboxModule(),
        new CassandraMessageModule(),
        new CassandraMailboxCounterModule(),
        new CassandraModSeqModule(),
        new CassandraUidModule(),
        new CassandraAttachmentModule(),
        new CassandraAnnotationModule()));

    private final MessageUidProvider messageUidProvider;
    private final CassandraModSeqProvider cassandraModSeqProvider;

    public CassandraMapperProvider() {
        messageUidProvider = new MessageUidProvider();
        cassandraModSeqProvider = new CassandraModSeqProvider(cassandra.getConf());
    }

    @Override
    public MessageId generateMessageId() {
        return MESSAGE_ID_FACTORY.generate();
    }
    
    @Override
    public MailboxMapper createMailboxMapper() throws MailboxException {
        CassandraMessageIdToImapUidDAO imapUidDAO = new CassandraMessageIdToImapUidDAO(cassandra.getConf(), MESSAGE_ID_FACTORY);
        return new CassandraMailboxSessionMapperFactory(
            new CassandraUidProvider(cassandra.getConf()),
            new CassandraModSeqProvider(cassandra.getConf()),
            cassandra.getConf(),
            cassandra.getTypesProvider(),
            new CassandraMessageDAO(cassandra.getConf(), cassandra.getTypesProvider(), MESSAGE_ID_FACTORY),
            new CassandraMessageIdDAO(cassandra.getConf(), MESSAGE_ID_FACTORY),
            imapUidDAO
        ).getMailboxMapper(new MockMailboxSession("benwa"));
    }

    @Override
    public MessageMapper createMessageMapper() throws MailboxException {
        return new CassandraMailboxSessionMapperFactory(
                new CassandraUidProvider(cassandra.getConf()),
                new CassandraModSeqProvider(cassandra.getConf()),
                cassandra.getConf(),
                cassandra.getTypesProvider(),
                new CassandraMessageDAO(cassandra.getConf(), cassandra.getTypesProvider(), MESSAGE_ID_FACTORY),
                new CassandraMessageIdDAO(cassandra.getConf(), MESSAGE_ID_FACTORY),
                new CassandraMessageIdToImapUidDAO(cassandra.getConf(), MESSAGE_ID_FACTORY)
            ).getMessageMapper(new MockMailboxSession("benwa"));
    }

    @Override
    public MessageIdMapper createMessageIdMapper() throws MailboxException {
        return new CassandraMailboxSessionMapperFactory(
                new CassandraUidProvider(cassandra.getConf()),
                new CassandraModSeqProvider(cassandra.getConf()),
                cassandra.getConf(),
                cassandra.getTypesProvider(),
                new CassandraMessageDAO(cassandra.getConf(), cassandra.getTypesProvider(), MESSAGE_ID_FACTORY),
                new CassandraMessageIdDAO(cassandra.getConf(), MESSAGE_ID_FACTORY),
                new CassandraMessageIdToImapUidDAO(cassandra.getConf(), MESSAGE_ID_FACTORY)
            ).getMessageIdMapper(new MockMailboxSession("benwa"));
    }

    @Override
    public AttachmentMapper createAttachmentMapper() throws MailboxException {
        return new CassandraMailboxSessionMapperFactory(
                new CassandraUidProvider(cassandra.getConf()),
                cassandraModSeqProvider,
                cassandra.getConf(),
                cassandra.getTypesProvider(),
                new CassandraMessageDAO(cassandra.getConf(), cassandra.getTypesProvider(), MESSAGE_ID_FACTORY),
                new CassandraMessageIdDAO(cassandra.getConf(), MESSAGE_ID_FACTORY),
                new CassandraMessageIdToImapUidDAO(cassandra.getConf(), MESSAGE_ID_FACTORY)
            ).getAttachmentMapper(new MockMailboxSession("benwa"));
    }

    @Override
    public CassandraId generateId() {
        return CassandraId.timeBased();
    }

    @Override
    public void clearMapper() throws MailboxException {
        cassandra.clearAllTables();
    }

    @Override
    public void ensureMapperPrepared() throws MailboxException {
        cassandra.ensureAllTables();
    }

    @Override
    public boolean supportPartialAttachmentFetch() {
        return true;
    }

    @Override
    public AnnotationMapper createAnnotationMapper() throws MailboxException {
        return new CassandraMailboxSessionMapperFactory(
                new CassandraUidProvider(cassandra.getConf()),
                cassandraModSeqProvider,
                cassandra.getConf(),
                cassandra.getTypesProvider(),
                new CassandraMessageDAO(cassandra.getConf(), cassandra.getTypesProvider(), MESSAGE_ID_FACTORY),
                new CassandraMessageIdDAO(cassandra.getConf(), MESSAGE_ID_FACTORY),
                new CassandraMessageIdToImapUidDAO(cassandra.getConf(), MESSAGE_ID_FACTORY)
            ).getAnnotationMapper(new MockMailboxSession("benwa"));
    }

    @Override
    public List<Capabilities> getNotImplemented() {
        return ImmutableList.of();
    }

    @Override
    public MessageUid generateMessageUid() {
        return messageUidProvider.next();
    }

    @Override
    public long generateModSeq(Mailbox mailbox) throws MailboxException {
        MailboxSession mailboxSession = null;
        return cassandraModSeqProvider.nextModSeq(mailboxSession, mailbox);
    }

    @Override
    public long highestModSeq(Mailbox mailbox) throws MailboxException {
        MailboxSession mailboxSession = null;
        return cassandraModSeqProvider.highestModSeq(mailboxSession, mailbox);
    }
}
