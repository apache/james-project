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

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.StringBackedAttachmentId;
import org.apache.james.mailbox.postgres.PostgresMailboxAggregateDataDefinition;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresAttachmentDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgresAttachmentBlobReferenceSourceTest {

    private static final AttachmentId ATTACHMENT_ID = StringBackedAttachmentId.random();
    private static final AttachmentId ATTACHMENT_ID_2 = StringBackedAttachmentId.random();
    private static final BlobId.Factory BLOB_ID_FACTORY = new PlainBlobId.Factory();

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresMailboxAggregateDataDefinition.MODULE);

    private PostgresAttachmentBlobReferenceSource testee;

    private PostgresAttachmentDAO postgresAttachmentDAO;

    @BeforeEach
    void beforeEach() {
        BlobId.Factory blobIdFactory = new PlainBlobId.Factory();
        postgresAttachmentDAO = new PostgresAttachmentDAO(postgresExtension.getDefaultPostgresExecutor(),
            blobIdFactory);
        testee = new PostgresAttachmentBlobReferenceSource(postgresAttachmentDAO);
    }

    @Test
    void blobReferencesShouldBeEmptyByDefault() {
        assertThat(testee.listReferencedBlobs().collectList().block())
            .isEmpty();
    }

    @Test
    void blobReferencesShouldReturnAllValues() {
        AttachmentMetadata attachment1 = AttachmentMetadata.builder()
            .attachmentId(ATTACHMENT_ID)
            .messageId(new PostgresMessageId.Factory().generate())
            .type("application/json")
            .size(36)
            .build();
        BlobId blobId1 = BLOB_ID_FACTORY.parse("blobId");

        postgresAttachmentDAO.storeAttachment(attachment1, blobId1).block();

        AttachmentMetadata attachment2 = AttachmentMetadata.builder()
            .attachmentId(ATTACHMENT_ID_2)
            .messageId(new PostgresMessageId.Factory().generate())
            .type("application/json")
            .size(36)
            .build();
        BlobId blobId2 = BLOB_ID_FACTORY.parse("blobId");
        postgresAttachmentDAO.storeAttachment(attachment2, blobId2).block();

        assertThat(testee.listReferencedBlobs().collectList().block())
            .containsOnly(blobId1, blobId2);
    }

    @Test
    void blobReferencesShouldReturnDuplicates() {
        AttachmentMetadata attachment1 = AttachmentMetadata.builder()
            .attachmentId(ATTACHMENT_ID)
            .messageId(new PostgresMessageId.Factory().generate())
            .type("application/json")
            .size(36)
            .build();
        BlobId blobId = BLOB_ID_FACTORY.parse("blobId");
        postgresAttachmentDAO.storeAttachment(attachment1, blobId).block();

        AttachmentMetadata attachment2 = AttachmentMetadata.builder()
            .attachmentId(ATTACHMENT_ID_2)
            .messageId(new PostgresMessageId.Factory().generate())
            .type("application/json")
            .size(36)
            .build();
        postgresAttachmentDAO.storeAttachment(attachment2, blobId).block();

        assertThat(testee.listReferencedBlobs().collectList().block())
            .hasSize(2)
            .containsOnly(blobId);
    }
}
