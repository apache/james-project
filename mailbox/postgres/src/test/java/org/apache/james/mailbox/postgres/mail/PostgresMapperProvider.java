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

import java.time.Instant;
import java.util.List;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MapperProvider;
import org.apache.james.mailbox.store.mail.model.MessageUidProvider;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.apache.james.utils.UpdatableTickingClock;

import com.google.common.collect.ImmutableList;

public class PostgresMapperProvider implements MapperProvider {

    private final PostgresMessageId.Factory messageIdFactory;
    private final PostgresExtension postgresExtension;
    private final UpdatableTickingClock updatableTickingClock;
    private final BlobStore blobStore;
    private final BlobId.Factory blobIdFactory;
    private MessageUidProvider messageUidProvider;

    public PostgresMapperProvider(PostgresExtension postgresExtension) {
        this.postgresExtension = postgresExtension;
        this.updatableTickingClock = new UpdatableTickingClock(Instant.now());
        this.messageIdFactory = new PostgresMessageId.Factory();
        this.blobIdFactory = new HashBlobId.Factory();
        this.blobStore = new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.DEFAULT, blobIdFactory);
        this.messageUidProvider = new MessageUidProvider();
    }

    @Override
    public List<Capabilities> getSupportedCapabilities() {
        return ImmutableList.of(Capabilities.ANNOTATION, Capabilities.MAILBOX, Capabilities.MESSAGE, Capabilities.MOVE,
            Capabilities.ATTACHMENT, Capabilities.THREAD_SAFE_FLAGS_UPDATE, Capabilities.UNIQUE_MESSAGE_ID);
    }

    @Override
    public MailboxMapper createMailboxMapper() {
        return new PostgresMailboxMapper(new PostgresMailboxDAO(postgresExtension.getPostgresExecutor()));
    }

    @Override
    public MessageMapper createMessageMapper() {
        PostgresMailboxDAO mailboxDAO = new PostgresMailboxDAO(postgresExtension.getPostgresExecutor());

        PostgresModSeqProvider modSeqProvider = new PostgresModSeqProvider(mailboxDAO);
        PostgresUidProvider uidProvider = new PostgresUidProvider(mailboxDAO);

        return new PostgresMessageMapper(
            postgresExtension.getPostgresExecutor(),
            modSeqProvider,
            uidProvider,
            blobStore,
            updatableTickingClock,
            blobIdFactory);
    }

    @Override
    public MessageIdMapper createMessageIdMapper() {
        PostgresMailboxDAO mailboxDAO = new PostgresMailboxDAO(postgresExtension.getPostgresExecutor());
        return new PostgresMessageIdMapper(postgresExtension.getPostgresExecutor(), new PostgresModSeqProvider(mailboxDAO),
            blobStore, updatableTickingClock, blobIdFactory);
    }

    @Override
    public AttachmentMapper createAttachmentMapper() {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public MailboxId generateId() {
        return PostgresMailboxId.generate();
    }

    @Override
    public MessageUid generateMessageUid() {
        return messageUidProvider.next();
    }

    @Override
    public ModSeq generateModSeq(Mailbox mailbox) {
        try {
            return new PostgresModSeqProvider(new PostgresMailboxDAO(postgresExtension.getPostgresExecutor()))
                .nextModSeq(mailbox);
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ModSeq highestModSeq(Mailbox mailbox) {
        return new PostgresModSeqProvider(new PostgresMailboxDAO(postgresExtension.getPostgresExecutor()))
            .highestModSeq(mailbox);
    }

    @Override
    public boolean supportPartialAttachmentFetch() {
        return false;
    }

    @Override
    public MessageId generateMessageId() {
        return messageIdFactory.generate();
    }

    public UpdatableTickingClock getUpdatableTickingClock() {
        return updatableTickingClock;
    }
}
