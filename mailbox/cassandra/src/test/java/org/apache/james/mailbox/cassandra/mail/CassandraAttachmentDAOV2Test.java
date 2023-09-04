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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2.DAOAttachment;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.uuid.Uuids;

import reactor.core.publisher.Mono;

class CassandraAttachmentDAOV2Test {
    private static final AttachmentId ATTACHMENT_ID = AttachmentId.from("id1");
    private static final AttachmentId ATTACHMENT_ID_2 = AttachmentId.from("id2");
    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraAttachmentModule.MODULE);

    private CassandraAttachmentDAOV2 testee;
    private AttachmentBlobReferenceSource blobReferenceSource;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraAttachmentDAOV2(
            BLOB_ID_FACTORY,
            cassandra.getConf());
        blobReferenceSource = new AttachmentBlobReferenceSource(testee);
    }

    @Test
    void getAttachmentShouldReturnEmptyWhenAbsent() {
        Optional<DAOAttachment> attachment = testee.getAttachment(ATTACHMENT_ID).blockOptional();

        assertThat(attachment).isEmpty();
    }

    @Test
    void deleteShouldNotThrowWhenDoesNotExist() {
        assertThatCode(() -> testee.delete(ATTACHMENT_ID).block())
            .doesNotThrowAnyException();
    }

    @Test
    void getAttachmentShouldReturnAttachmentWhenStored() {
        AttachmentMetadata attachment = AttachmentMetadata.builder()
            .attachmentId(ATTACHMENT_ID)
            .messageId(CassandraMessageId.Factory.of(Uuids.timeBased()))
            .type("application/json")
            .size(4)
            .build();
        BlobId blobId = BLOB_ID_FACTORY.from("blobId");
        DAOAttachment daoAttachment = CassandraAttachmentDAOV2.from(attachment, blobId);
        testee.storeAttachment(daoAttachment).block();

        Optional<DAOAttachment> actual = testee.getAttachment(ATTACHMENT_ID).blockOptional();

        assertThat(actual).contains(daoAttachment);
    }

    @Test
    void getAttachmentShouldNotReturnDeletedAttachments() {
        AttachmentMetadata attachment = AttachmentMetadata.builder()
            .messageId(CassandraMessageId.Factory.of(Uuids.timeBased()))
            .attachmentId(ATTACHMENT_ID)
            .type("application/json")
            .size(36)
            .build();
        BlobId blobId = BLOB_ID_FACTORY.from("blobId");
        DAOAttachment daoAttachment = CassandraAttachmentDAOV2.from(attachment, blobId);
        testee.storeAttachment(daoAttachment).block();

        testee.delete(ATTACHMENT_ID).block();

        Optional<DAOAttachment> actual = testee.getAttachment(ATTACHMENT_ID).blockOptional();

        assertThat(actual).isEmpty();
    }

    @Test
    void blobReferencesShouldBeEmptyByDefault() {
        assertThat(blobReferenceSource.listReferencedBlobs().collectList().block())
            .isEmpty();
    }

    @Test
    void blobReferencesShouldReturnAllValues() {
        AttachmentMetadata attachment1 = AttachmentMetadata.builder()
            .attachmentId(ATTACHMENT_ID)
            .type("application/json")
            .messageId(CassandraMessageId.Factory.of(Uuids.timeBased()))
            .size(36)
            .build();
        BlobId blobId1 = BLOB_ID_FACTORY.from("blobId");
        DAOAttachment daoAttachment1 = CassandraAttachmentDAOV2.from(attachment1, blobId1);
        testee.storeAttachment(daoAttachment1).block();

        AttachmentMetadata attachment2 = AttachmentMetadata.builder()
            .attachmentId(ATTACHMENT_ID_2)
            .type("application/json")
            .messageId(CassandraMessageId.Factory.of(Uuids.timeBased()))
            .size(36)
            .build();
        BlobId blobId2 = BLOB_ID_FACTORY.from("blobId");
        DAOAttachment daoAttachment2 = CassandraAttachmentDAOV2.from(attachment2, blobId2);
        testee.storeAttachment(daoAttachment2).block();

        assertThat(blobReferenceSource.listReferencedBlobs().collectList().block())
            .containsOnly(blobId1, blobId2);
    }

    @Test
    void blobReferencesShouldReturnDuplicates() {
        AttachmentMetadata attachment1 = AttachmentMetadata.builder()
            .attachmentId(ATTACHMENT_ID)
            .type("application/json")
            .messageId(CassandraMessageId.Factory.of(Uuids.timeBased()))
            .size(36)
            .build();
        BlobId blobId = BLOB_ID_FACTORY.from("blobId");
        DAOAttachment daoAttachment1 = CassandraAttachmentDAOV2.from(attachment1, blobId);
        testee.storeAttachment(daoAttachment1).block();

        AttachmentMetadata attachment2 = AttachmentMetadata.builder()
            .attachmentId(ATTACHMENT_ID_2)
            .type("application/json")
            .messageId(CassandraMessageId.Factory.of(Uuids.timeBased()))
            .size(36)
            .build();
        DAOAttachment daoAttachment2 = CassandraAttachmentDAOV2.from(attachment2, blobId);
        testee.storeAttachment(daoAttachment2).block();

        assertThat(blobReferenceSource.listReferencedBlobs().collectList().block())
            .hasSize(2)
            .containsOnly(blobId);
    }
}
