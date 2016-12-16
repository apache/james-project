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

import static org.mockito.Mockito.mock;

import java.util.Date;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraModSeqProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUidProvider;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAnnotationModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.cassandra.modules.CassandraModSeqModule;
import org.apache.james.mailbox.cassandra.modules.CassandraUidModule;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.AbstractMessageIdManagerStorageTest;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.MessageIdManagerTestSystem;
import org.apache.james.mailbox.store.NoMailboxPathLocker;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.NoQuotaManager;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;

public class CassandraMessageIdManagerStorageTest extends AbstractMessageIdManagerStorageTest {

    private static final long UID_VALIDITY = 18L;

    private static final CassandraCluster CASSANDRA = CassandraCluster.create(new CassandraModuleComposite(
        new CassandraAclModule(),
        new CassandraMailboxModule(),
        new CassandraMessageModule(),
        new CassandraMailboxCounterModule(),
        new CassandraUidModule(),
        new CassandraModSeqModule(),
        new CassandraAttachmentModule(),
        new CassandraAnnotationModule()));
    public static final int MOD_SEQ = 452;

    @Override
    protected MessageIdManagerTestSystem createTestingData() throws Exception {
        CASSANDRA.ensureAllTables();
        CassandraUidProvider uidProvider = new CassandraUidProvider(CASSANDRA.getConf());
        CassandraModSeqProvider modSeqProvider = new CassandraModSeqProvider(CASSANDRA.getConf());
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();
        CassandraMessageIdDAO messageIdDAO = new CassandraMessageIdDAO(CASSANDRA.getConf(), messageIdFactory);
        CassandraMessageIdToImapUidDAO imapUidDAO = new CassandraMessageIdToImapUidDAO(CASSANDRA.getConf(), messageIdFactory);
        CassandraMessageDAO messageDAO = new CassandraMessageDAO(CASSANDRA.getConf(), CASSANDRA.getTypesProvider(), messageIdFactory);
        CassandraMailboxSessionMapperFactory mapperFactory = new CassandraMailboxSessionMapperFactory(uidProvider,
            modSeqProvider,
            CASSANDRA.getConf(),
            CASSANDRA.getTypesProvider(),
            messageDAO,
            messageIdDAO,
            imapUidDAO);

        StoreMessageIdManager messageIdManager = new StoreMessageIdManager(mapperFactory,
            new MailboxEventDispatcher(mock(MailboxListener.class)),
            new CassandraMessageId.Factory(),
            new NoQuotaManager(),
            new DefaultQuotaRootResolver(mapperFactory));
        SimpleMailbox mailbox1 = new SimpleMailbox(new MailboxPath("#private", "user", "INBOX"), UID_VALIDITY, CassandraId.timeBased());
        SimpleMailbox mailbox2 = new SimpleMailbox(new MailboxPath("#private", "user", "OUTBOX"), UID_VALIDITY, CassandraId.timeBased());
        SimpleMailbox mailbox3 = new SimpleMailbox(new MailboxPath("#private", "user", "SENT"), UID_VALIDITY, CassandraId.timeBased());
        MockMailboxSession mailboxSession = new MockMailboxSession("user");
        MailboxMapper mailboxMapper = mapperFactory.getMailboxMapper(mailboxSession);
        mailboxMapper.save(mailbox1);
        mailboxMapper.save(mailbox2);
        mailboxMapper.save(mailbox3);

        CassandraMailboxManager cassandraMailboxManager = new CassandraMailboxManager(mapperFactory, mock(Authenticator.class), new NoMailboxPathLocker(), new MessageParser(), messageIdFactory);
        cassandraMailboxManager.init();

        return new MessageIdManagerTestSystem(messageIdManager, mailboxSession,
            mailbox1, mailbox2, mailbox3) {
            @Override
            public MessageId persist(MailboxId mailboxId, MessageUid uid, Flags flags) {
                try {
                    CassandraMessageId messageId = messageIdFactory.generate();
                    Mailbox mailbox = mapperFactory.getMailboxMapper(mailboxSession).findMailboxById(mailboxId);
                    mapperFactory.getMessageMapper(mailboxSession).add(mailbox, createMessage(mailboxId, flags, messageId, uid));
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
            public void deleteMailbox(MailboxId mailboxId) {
                try {
                    Mailbox mailbox = mapperFactory.getMailboxMapper(mailboxSession).findMailboxById(mailboxId);
                    cassandraMailboxManager.deleteMailbox(new MailboxPath(mailbox.getNamespace(), mailbox.getUser(), mailbox.getName()), mailboxSession);
                } catch (Exception e) {
                    throw Throwables.propagate(e);
                }
            }

            @Override
            public void clean() {
                CASSANDRA.clearAllTables();
            }
        };
    }

    private MailboxMessage createMessage(MailboxId mailboxId, Flags flags, MessageId messageId, MessageUid uid) {
        MailboxMessage mailboxMessage = new SimpleMailboxMessage(messageId, new Date(), 1596, 1256,
            new SharedByteArrayInputStream("subject: any\n\nbody".getBytes(Charsets.UTF_8)), flags, new PropertyBuilder(), mailboxId);
        mailboxMessage.setModSeq(MOD_SEQ);
        mailboxMessage.setUid(uid);
        return mailboxMessage;
    }
}
