/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.cassandra;

import java.util.Date;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.store.MessageIdManagerTestSystem;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.mailbox.store.quota.StoreCurrentQuotaManager;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;

public class CassandraMessageIdManagerTestSystem extends MessageIdManagerTestSystem {

    private static final byte[] MESSAGE_CONTENT = "subject: any\n\nbody".getBytes(Charsets.UTF_8);

    public static MessageIdManagerTestSystem createTestingData(CassandraCluster cassandra, QuotaManager quotaManager, MailboxEventDispatcher dispatcher) throws Exception {
        CassandraMailboxSessionMapperFactory mapperFactory = CassandraTestSystemFixture.createMapperFactory(cassandra);

        return new CassandraMessageIdManagerTestSystem(CassandraTestSystemFixture.createMessageIdManager(mapperFactory, quotaManager, dispatcher),
            new CassandraMessageId.Factory(),
            mapperFactory,
            CassandraTestSystemFixture.createMailboxManager(mapperFactory));
    }

    public static MessageIdManagerTestSystem createTestingDataWithQuota(CassandraCluster cassandra, QuotaManager quotaManager, CurrentQuotaManager currentQuotaManager) throws Exception {
        CassandraMailboxSessionMapperFactory mapperFactory = CassandraTestSystemFixture.createMapperFactory(cassandra);

        CassandraMailboxManager mailboxManager = CassandraTestSystemFixture.createMailboxManager(mapperFactory);
        ListeningCurrentQuotaUpdater listeningCurrentQuotaUpdater = new ListeningCurrentQuotaUpdater(
            (StoreCurrentQuotaManager) currentQuotaManager,
            mailboxManager.getQuotaRootResolver());
        mailboxManager.addGlobalListener(listeningCurrentQuotaUpdater, mailboxManager.createSystemSession("System"));
        return new CassandraMessageIdManagerTestSystem(CassandraTestSystemFixture.createMessageIdManager(mapperFactory, quotaManager, mailboxManager.getEventDispatcher()),
            new CassandraMessageId.Factory(),
            mapperFactory,
            mailboxManager);
    }

    private final CassandraMessageId.Factory messageIdFactory;
    private final CassandraMailboxSessionMapperFactory mapperFactory;
    private final CassandraMailboxManager cassandraMailboxManager;

    public CassandraMessageIdManagerTestSystem(MessageIdManager messageIdManager, CassandraMessageId.Factory messageIdFactory, CassandraMailboxSessionMapperFactory mapperFactory, CassandraMailboxManager cassandraMailboxManager) {
        super(messageIdManager);
        this.messageIdFactory = messageIdFactory;
        this.mapperFactory = mapperFactory;
        this.cassandraMailboxManager = cassandraMailboxManager;
    }

    @Override
    public Mailbox createMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException {
        cassandraMailboxManager.createMailbox(mailboxPath, session);
        return mapperFactory.getMailboxMapper(session).findMailboxByPath(mailboxPath);
    }

    @Override
    public MessageId persist(MailboxId mailboxId, MessageUid uid, Flags flags, MailboxSession mailboxSession) {
        try {
            CassandraMessageId messageId = messageIdFactory.generate();
            Mailbox mailbox = mapperFactory.getMailboxMapper(mailboxSession).findMailboxById(mailboxId);
            MailboxMessage message = createMessage(mailboxId, flags, messageId, uid);
            mapperFactory.getMessageMapper(mailboxSession).add(mailbox, message);
            cassandraMailboxManager.getEventDispatcher().added(mailboxSession, new SimpleMessageMetaData(message), mailbox);
            return messageId;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public MessageId createNotUsedMessageId() {
        return messageIdFactory.generate();
    }

    @Override
    public void deleteMailbox(MailboxId mailboxId, MailboxSession mailboxSession) {
        try {
            Mailbox mailbox = mapperFactory.getMailboxMapper(mailboxSession).findMailboxById(mailboxId);
            cassandraMailboxManager.deleteMailbox(new MailboxPath(mailbox.getNamespace(), mailbox.getUser(), mailbox.getName()), mailboxSession);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private static MailboxMessage createMessage(MailboxId mailboxId, Flags flags, MessageId messageId, MessageUid uid) {
        MailboxMessage mailboxMessage = new SimpleMailboxMessage(messageId, new Date(), MESSAGE_CONTENT.length, 1256,
            new SharedByteArrayInputStream(MESSAGE_CONTENT), flags, new PropertyBuilder(), mailboxId);
        mailboxMessage.setModSeq(CassandraTestSystemFixture.MOD_SEQ);
        mailboxMessage.setUid(uid);
        return mailboxMessage;
    }

    @Override
    public int getConstantMessageSize() {
        return MESSAGE_CONTENT.length;
    }

    @Override
    public void setACL(MailboxId mailboxId, MailboxACL mailboxACL, MailboxSession session) throws MailboxException {
        cassandraMailboxManager.setRights(mailboxId, mailboxACL, session);
    }
}