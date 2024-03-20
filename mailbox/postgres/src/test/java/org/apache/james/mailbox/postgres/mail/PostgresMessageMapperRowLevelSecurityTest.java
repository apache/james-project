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

package org.apache.james.mailbox.postgres.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Date;

import jakarta.mail.Flags;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.utils.DomainImplPostgresConnectionFactory;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.postgres.PostgresMailboxAggregateModule;
import org.apache.james.mailbox.postgres.PostgresMailboxSessionMapperFactory;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresMessageMapperRowLevelSecurityTest {
    private static final int BODY_START = 16;
    private static final UidValidity UID_VALIDITY = UidValidity.of(42);
    private static final Username BENWA = Username.of("benwa");
    protected static final MailboxPath benwaInboxPath = MailboxPath.forUser(BENWA, "INBOX");
    private static final MailboxSession aliceSession = MailboxSessionUtil.create(Username.of("alice@domain1"));
    private static final MailboxSession bobSession = MailboxSessionUtil.create(Username.of("bob@domain1"));
    private static final MailboxSession bobDomain2Session = MailboxSessionUtil.create(Username.of("bob@domain2"));

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(PostgresMailboxAggregateModule.MODULE);

    private PostgresMailboxSessionMapperFactory postgresMailboxSessionMapperFactory;
    private Mailbox mailbox;

    private Mailbox generateMailbox() {
        MailboxMapper mailboxMapper = new PostgresMailboxMapper(new PostgresMailboxDAO(postgresExtension.getPostgresExecutor()));
        return mailboxMapper.create(benwaInboxPath, UID_VALIDITY).block();
    }

    @BeforeEach
    public void setUp() {
        BlobId.Factory blobIdFactory = new HashBlobId.Factory();
        postgresMailboxSessionMapperFactory = new PostgresMailboxSessionMapperFactory(new PostgresExecutor.Factory(new DomainImplPostgresConnectionFactory(postgresExtension.getConnectionFactory())),
            new UpdatableTickingClock(Instant.now()),
            new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.DEFAULT, blobIdFactory),
            blobIdFactory);

        mailbox = generateMailbox();
    }

    @Test
    void messagesCanBeAccessedAtTheDataLevelByMembersOfTheSameDomain() throws Exception {
        postgresMailboxSessionMapperFactory.getMessageMapper(aliceSession).add(mailbox, createMessage());

        assertThat(postgresMailboxSessionMapperFactory.getMessageMapper(bobSession).countMessagesInMailbox(mailbox)).isEqualTo(1L);
    }

    @Test
    void messagesShouldBeIsolatedByDomain() throws Exception {
        postgresMailboxSessionMapperFactory.getMessageMapper(aliceSession).add(mailbox, createMessage());

        assertThat(postgresMailboxSessionMapperFactory.getMessageMapper(bobDomain2Session).countMessagesInMailbox(mailbox)).isEqualTo(0L);
    }

    private MailboxMessage createMessage() {
        return  createMessage(mailbox, new PostgresMessageId.Factory().generate(), "Subject: Test1 \n\nBody1\n.\n", BODY_START, new PropertyBuilder());
    }

    private MailboxMessage createMessage(Mailbox mailbox, MessageId messageId, String content, int bodyStart, PropertyBuilder propertyBuilder) {
        return new SimpleMailboxMessage(messageId, ThreadId.fromBaseMessageId(messageId), new Date(), content.length(), bodyStart, new ByteContent(content.getBytes()), new Flags(), propertyBuilder.build(), mailbox.getMailboxId());
    }
}
