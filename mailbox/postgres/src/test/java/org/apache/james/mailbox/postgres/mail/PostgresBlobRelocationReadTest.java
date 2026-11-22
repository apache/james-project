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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.mail.Flags;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.StringBackedAttachmentIdFactory;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.postgres.PostgresMailboxAggregateDataDefinition;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresAttachmentDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxDAO;
import org.apache.james.mailbox.store.mail.AttachmentIdAssignationStrategy;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.reactivestreams.Publisher;

import com.google.common.io.ByteSource;

import reactor.core.publisher.Mono;

class PostgresBlobRelocationReadTest {
    private static final PlainBlobId.Factory BLOB_ID_FACTORY = new PlainBlobId.Factory();
    private static final BlobId RELOCATED = BLOB_ID_FACTORY.of("relocated");
    private static final String CONTENT = "Subject: relocation\n\nbody content\n";
    private static final byte[] ATTACHMENT_CONTENT = "attachment content".getBytes(StandardCharsets.UTF_8);

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresMailboxAggregateDataDefinition.MODULE);

    private MemoryBlobStoreDAO blobStoreDAO;
    private BlobStore blobStore;
    private PostgresExecutor executor;
    private PostgresMessageMapper messageMapper;
    private PostgresAttachmentMapper attachmentMapper;
    private Mailbox mailbox;

    @BeforeEach
    void setUp() {
        blobStoreDAO = new MemoryBlobStoreDAO();
        blobStore = spy(new DeDuplicationBlobStore(blobStoreDAO, BucketName.DEFAULT, BLOB_ID_FACTORY));
        executor = postgresExtension.getDefaultPostgresExecutor();
        PostgresMailboxDAO mailboxDAO = new PostgresMailboxDAO(executor);
        AttachmentIdAssignationStrategy attachmentIdAssignationStrategy = new AttachmentIdAssignationStrategy.Default(new StringBackedAttachmentIdFactory());
        messageMapper = new PostgresMessageMapper(executor, new PostgresModSeqProvider(mailboxDAO), new PostgresUidProvider(mailboxDAO),
            blobStore, Clock.systemUTC(), BLOB_ID_FACTORY, attachmentIdAssignationStrategy);
        attachmentMapper = new PostgresAttachmentMapper(new PostgresAttachmentDAO(executor, BLOB_ID_FACTORY), blobStore, attachmentIdAssignationStrategy);
        mailbox = new PostgresMailboxMapper(mailboxDAO)
            .create(MailboxPath.inbox(Username.of("bob")), UidValidity.of(42))
            .block();
    }

    @Test
    void fullFetchShouldReadBodyRelocatedAfterTheRowWasRead() throws Exception {
        messageMapper.add(mailbox, message());
        AtomicBoolean relocated = new AtomicBoolean();
        doAnswer(invocation -> {
            Mono<Void> relocation = relocated.compareAndSet(false, true)
                ? relocate(invocation.getArgument(1), "message", "body_blob_id")
                : Mono.empty();
            return relocation.then(Mono.from((Publisher<?>) invocation.callRealMethod()));
        }).when(blobStore).readBytes(any(), any(), any());

        List<MailboxMessage> messages = fetchFull();

        assertThat(messages).hasSize(1);
        assertThat(new String(messages.get(0).getFullContent().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(CONTENT);
        verify(blobStore, times(2)).readBytes(any(), any(), any());
    }

    @Test
    void fullFetchShouldFailOnceWhenBodyIsGenuinelyMissing() throws Exception {
        messageMapper.add(mailbox, message());
        Mono.from(blobStoreDAO.deleteBucket(BucketName.DEFAULT)).block();

        assertThatThrownBy(this::fetchFull).isInstanceOf(ObjectNotFoundException.class);
        verify(blobStore, times(1)).readBytes(any(), any(), any());
    }

    @Test
    void attachmentContentShouldReadBlobRelocatedAfterTheRowWasRead() throws Exception {
        AttachmentId attachmentId = storeAttachment();
        AtomicBoolean relocated = new AtomicBoolean();
        doAnswer(invocation -> {
            Mono<Void> relocation = relocated.compareAndSet(false, true)
                ? relocate(invocation.getArgument(1), "attachment", "blob_id")
                : Mono.empty();
            return relocation.then(Mono.from((Publisher<?>) invocation.callRealMethod()));
        }).when(blobStore).readReactive(any(), any(), any());

        byte[] content = attachmentMapper.loadAttachmentContentReactive(attachmentId).block().readAllBytes();

        assertThat(content).isEqualTo(ATTACHMENT_CONTENT);
        verify(blobStore, times(2)).readReactive(any(), any(), any());
    }

    @Test
    void attachmentContentShouldFailOnceWhenBlobIsGenuinelyMissing() {
        AttachmentId attachmentId = storeAttachment();
        Mono.from(blobStoreDAO.deleteBucket(BucketName.DEFAULT)).block();

        assertThatThrownBy(() -> attachmentMapper.loadAttachmentContentReactive(attachmentId).block())
            .isInstanceOf(ObjectNotFoundException.class);
        verify(blobStore, times(1)).readReactive(any(), any(), any());
    }

    private List<MailboxMessage> fetchFull() {
        return messageMapper.findInMailboxReactive(mailbox, MessageRange.all(), FetchType.FULL, 10)
            .collectList()
            .block();
    }

    /** Simulates compaction between the row read and the blob read: copy, update the reference, delete the old blob. */
    private Mono<Void> relocate(BlobId oldId, String table, String column) {
        return Mono.from(blobStoreDAO.readBytes(BucketName.DEFAULT, oldId))
            .flatMap(blob -> Mono.from(blobStoreDAO.save(BucketName.DEFAULT, RELOCATED, blob)))
            .then(executor.executeVoid(dsl -> Mono.from(dsl.query("UPDATE " + table + " SET " + column + " = ? WHERE " + column + " = ?",
                RELOCATED.asString(), oldId.asString()))))
            .then(Mono.from(blobStoreDAO.delete(BucketName.DEFAULT, oldId)));
    }

    private AttachmentId storeAttachment() {
        return attachmentMapper.storeAttachmentsReactive(List.of(ParsedAttachment.builder()
                    .contentType("text/plain")
                    .content(ByteSource.wrap(ATTACHMENT_CONTENT))
                    .noName()
                    .noCid()
                    .inline(false)), new PostgresMessageId.Factory().generate())
            .block()
            .get(0)
            .getAttachmentId();
    }

    private SimpleMailboxMessage message() {
        PostgresMessageId messageId = new PostgresMessageId.Factory().generate();
        return SimpleMailboxMessage.builder()
            .messageId(messageId)
            .threadId(ThreadId.fromBaseMessageId(messageId))
            .mailboxId(mailbox.getMailboxId())
            .uid(MessageUid.of(1))
            .internalDate(new Date())
            .bodyStartOctet(CONTENT.indexOf("\n\n") + 2)
            .size(CONTENT.length())
            .content(new ByteContent(CONTENT.getBytes(StandardCharsets.UTF_8)))
            .flags(new Flags())
            .build();
    }
}
