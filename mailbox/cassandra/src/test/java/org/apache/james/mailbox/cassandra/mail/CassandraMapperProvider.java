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
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.TestCassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId.Factory;
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

    private final CassandraCluster cassandra;
    private final MessageUidProvider messageUidProvider;
    private final CassandraModSeqProvider cassandraModSeqProvider;
    private final MockMailboxSession mailboxSession = new MockMailboxSession("benwa");


    public CassandraMapperProvider(CassandraCluster cassandra) {
        this.cassandra = cassandra;
        messageUidProvider = new MessageUidProvider();
        cassandraModSeqProvider = new CassandraModSeqProvider(this.cassandra.getConf());
    }

    @Override
    public MessageId generateMessageId() {
        return MESSAGE_ID_FACTORY.generate();
    }
    
    @Override
    public MailboxMapper createMailboxMapper() throws MailboxException {
        return createMapperFactory().getMailboxMapper(mailboxSession);
    }

    @Override
    public MessageMapper createMessageMapper() throws MailboxException {
        return createMapperFactory().getMessageMapper(mailboxSession);
    }

    @Override
    public MessageIdMapper createMessageIdMapper() throws MailboxException {
        return createMapperFactory().getMessageIdMapper(mailboxSession);
    }

    private CassandraMailboxSessionMapperFactory createMapperFactory() {
        return TestCassandraMailboxSessionMapperFactory.forTests(cassandra.getConf(),
            cassandra.getTypesProvider(),
            new CassandraMessageId.Factory());
    }

    @Override
    public AttachmentMapper createAttachmentMapper() throws MailboxException {
        return createMapperFactory().createAttachmentMapper(mailboxSession);
    }

    @Override
    public CassandraId generateId() {
        return CassandraId.timeBased();
    }

    @Override
    public void clearMapper() throws MailboxException {
        cassandra.close();
    }

    @Override
    public boolean supportPartialAttachmentFetch() {
        return true;
    }

    @Override
    public AnnotationMapper createAnnotationMapper() throws MailboxException {
        return createMapperFactory().getAnnotationMapper(mailboxSession);
    }

    @Override
    public List<Capabilities> getSupportedCapabilities() {
        return ImmutableList.copyOf(Capabilities.values());
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
