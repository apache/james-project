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

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.mail.Flags;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobIdUpdater;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.mailbox.model.StringBackedAttachmentId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.PostgresMailboxAggregateDataDefinition;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresAttachmentDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgresBlobIdUpdaterTest {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresMailboxAggregateDataDefinition.MODULE);

    private PostgresMessageDAO postgresMessageDAO;
    private PostgresAttachmentDAO postgresAttachmentDAO;
    private PlainBlobId.Factory blobIdFactory;
    private PostgresBlobIdUpdater.Factory updaterFactory;

    @BeforeEach
    void setUp() {
        blobIdFactory = new PlainBlobId.Factory();
        postgresMessageDAO = new PostgresMessageDAO(postgresExtension.getDefaultPostgresExecutor(), blobIdFactory);
        postgresAttachmentDAO = new PostgresAttachmentDAO(postgresExtension.getDefaultPostgresExecutor(), blobIdFactory);
        updaterFactory = new PostgresBlobIdUpdater.Factory(postgresExtension.getDefaultPostgresExecutor(), blobIdFactory);
    }

    @Test
    void replaceReferencesShouldUpdateMessageBodyBlobId() {
        PostgresMessageId messageId = PostgresMessageId.Factory.of(UUID.randomUUID());
        BlobId oldBlobId = blobIdFactory.of("old-body-blob-id");
        BlobId newBlobId = blobIdFactory.of("new-chunk-body-blob-id~100~200");

        SimpleMailboxMessage message = createMessage(messageId);
        postgresMessageDAO.insert(message, oldBlobId.asString()).block();

        List<BlobId> observed = new CopyOnWriteArrayList<>();
        BlobIdUpdater testee = updaterFactory.forPredicate(blobId -> true, observed::add).block();

        assertThat(observed).contains(oldBlobId);

        testee.replaceReferences(oldBlobId, newBlobId).block();

        BlobId updatedBlobId = postgresMessageDAO.getBodyBlobId(messageId).block();
        assertThat(updatedBlobId).isEqualTo(newBlobId);
    }

    @Test
    void replaceReferencesShouldUpdateAttachmentBlobId() {
        PostgresMessageId messageId = PostgresMessageId.Factory.of(UUID.randomUUID());
        AttachmentId attachmentId = StringBackedAttachmentId.from("att-123");
        BlobId oldBlobId = blobIdFactory.of("old-attachment-blob-id");
        BlobId newBlobId = blobIdFactory.of("new-chunk-attachment-blob-id~0~500");

        AttachmentMetadata attachment = AttachmentMetadata.builder()
            .attachmentId(attachmentId)
            .type(ContentType.of("image/png"))
            .messageId(messageId)
            .size(500)
            .build();
        postgresAttachmentDAO.storeAttachment(attachment, oldBlobId).block();

        List<BlobId> observed = new CopyOnWriteArrayList<>();
        BlobIdUpdater testee = updaterFactory.forPredicate(blobId -> true, observed::add).block();

        assertThat(observed).contains(oldBlobId);

        testee.replaceReferences(oldBlobId, newBlobId).block();

        BlobId updatedBlobId = postgresAttachmentDAO.getAttachment(attachmentId).block().getRight();
        assertThat(updatedBlobId).isEqualTo(newBlobId);
    }

    @Test
    void forPredicateShouldFilterByGenerationCondition() {
        PostgresMessageId messageId1 = PostgresMessageId.Factory.of(UUID.randomUUID());
        PostgresMessageId messageId2 = PostgresMessageId.Factory.of(UUID.randomUUID());
        BlobId blobId1 = blobIdFactory.of("gen1-body-blob-id");
        BlobId blobId2 = blobIdFactory.of("gen2-body-blob-id");
        BlobId newSlotRef = blobIdFactory.of("chunk-body~0~100");

        postgresMessageDAO.insert(createMessage(messageId1), blobId1.asString()).block();
        postgresMessageDAO.insert(createMessage(messageId2), blobId2.asString()).block();

        List<BlobId> observed = new CopyOnWriteArrayList<>();
        // Only target gen1
        BlobIdUpdater testee = updaterFactory.forPredicate(blobId -> blobId.asString().startsWith("gen1"), observed::add).block();

        // Both are observed as live references
        assertThat(observed).contains(blobId1, blobId2);

        // Replacing gen1 should succeed
        testee.replaceReferences(blobId1, newSlotRef).block();
        // Attempting to replace gen2 should be a no-op because it was excluded by the predicate
        testee.replaceReferences(blobId2, newSlotRef).block();

        assertThat(postgresMessageDAO.getBodyBlobId(messageId1).block()).isEqualTo(newSlotRef);
        assertThat(postgresMessageDAO.getBodyBlobId(messageId2).block()).isEqualTo(blobId2);
    }

    @Test
    void replaceReferencesShouldBeIdempotentOnNonExistentBlobId() {
        BlobId unknownOld = blobIdFactory.of("non-existent-blob-id");
        BlobId newSlot = blobIdFactory.of("new-slot~0~10");

        List<BlobId> observed = new CopyOnWriteArrayList<>();
        BlobIdUpdater testee = updaterFactory.forPredicate(blobId -> true, observed::add).block();

        testee.replaceReferences(unknownOld, newSlot).block();

        assertThat(observed).isEmpty();
    }

    private SimpleMailboxMessage createMessage(PostgresMessageId messageId) {
        String content = "Subject: Hello\r\n\r\nWorld";
        return SimpleMailboxMessage.builder()
            .messageId(messageId)
            .threadId(ThreadId.fromBaseMessageId(messageId))
            .mailboxId(PostgresMailboxId.generate())
            .uid(MessageUid.of(1))
            .internalDate(new Date())
            .bodyStartOctet(17)
            .size(content.length())
            .content(new ByteContent(content.getBytes(StandardCharsets.UTF_8)))
            .flags(new Flags())
            .build();
    }
}
