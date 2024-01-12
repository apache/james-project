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

import java.time.Instant;
import java.util.List;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.CassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.TestCassandraMailboxSessionMapperFactory;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId.Factory;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.MapperProvider;
import org.apache.james.mailbox.store.mail.model.MessageUidProvider;
import org.apache.james.utils.UpdatableTickingClock;

import com.google.common.collect.ImmutableList;

public class CassandraMapperProvider implements MapperProvider {

    private static final Factory MESSAGE_ID_FACTORY = new CassandraMessageId.Factory();

    private final CassandraCluster cassandra;
    private final UidProvider messageUidProvider;
    private final CassandraModSeqProvider cassandraModSeqProvider;
    private final UpdatableTickingClock updatableTickingClock;
    private final MailboxSession mailboxSession = MailboxSessionUtil.create(Username.of("benwa"));
    private CassandraMailboxSessionMapperFactory mapperFactory;

    public CassandraMapperProvider(CassandraCluster cassandra,
                                   CassandraConfiguration cassandraConfiguration) {
        this.cassandra = cassandra;
        messageUidProvider = new CassandraUidProvider(this.cassandra.getConf(), cassandraConfiguration);
        cassandraModSeqProvider = new CassandraModSeqProvider(
                this.cassandra.getConf(),
                cassandraConfiguration);
        updatableTickingClock = new UpdatableTickingClock(Instant.now());
        mapperFactory = createMapperFactory(cassandraConfiguration, updatableTickingClock);
    }

    @Override
    public MessageId generateMessageId() {
        return MESSAGE_ID_FACTORY.generate();
    }
    
    @Override
    public MailboxMapper createMailboxMapper() throws MailboxException {
        return mapperFactory.getMailboxMapper(mailboxSession);
    }

    @Override
    public MessageMapper createMessageMapper() throws MailboxException {
        return mapperFactory.getMessageMapper(mailboxSession);
    }

    @Override
    public MessageIdMapper createMessageIdMapper() throws MailboxException {
        return mapperFactory.getMessageIdMapper(mailboxSession);
    }

    private CassandraMailboxSessionMapperFactory createMapperFactory(CassandraConfiguration cassandraConfiguration, UpdatableTickingClock updatableTickingClock) {
        return TestCassandraMailboxSessionMapperFactory.forTests(cassandra,
            new CassandraMessageId.Factory(),
            cassandraConfiguration,
            updatableTickingClock);
    }

    @Override
    public AttachmentMapper createAttachmentMapper() {
        return mapperFactory.createAttachmentMapper(mailboxSession);
    }

    @Override
    public CassandraId generateId() {
        return CassandraId.timeBased();
    }

    @Override
    public boolean supportPartialAttachmentFetch() {
        return true;
    }

    @Override
    public List<Capabilities> getSupportedCapabilities() {
        return ImmutableList.copyOf(Capabilities.values());
    }

    @Override
    public MessageUid generateMessageUid(Mailbox mailbox) {
        try {
            return messageUidProvider.nextUid(mailbox);
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ModSeq generateModSeq(Mailbox mailbox) throws MailboxException {
        return cassandraModSeqProvider.nextModSeq(mailbox);
    }

    @Override
    public ModSeq highestModSeq(Mailbox mailbox) throws MailboxException {
        return cassandraModSeqProvider.highestModSeq(mailbox);
    }

    public UpdatableTickingClock getUpdatableTickingClock() {
        return updatableTickingClock;
    }
}
