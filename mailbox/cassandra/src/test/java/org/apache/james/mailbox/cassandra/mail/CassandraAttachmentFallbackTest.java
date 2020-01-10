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

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobStore;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

class CassandraAttachmentFallbackTest {
    private static final AttachmentId ATTACHMENT_ID_1 = AttachmentId.from("id1");
    private static final AttachmentId ATTACHMENT_ID_2 = AttachmentId.from("id2");
    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(
            CassandraAttachmentModule.MODULE,
            CassandraBlobModule.MODULE));

    private CassandraAttachmentDAOV2 attachmentDAOV2;
    private CassandraAttachmentDAO attachmentDAO;
    private CassandraAttachmentMapper attachmentMapper;
    private CassandraBlobStore blobStore;
    private CassandraAttachmentMessageIdDAO attachmentMessageIdDAO;


    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        attachmentDAOV2 = new CassandraAttachmentDAOV2(BLOB_ID_FACTORY, cassandra.getConf());
        attachmentDAO = new CassandraAttachmentDAO(cassandra.getConf(),
            CassandraConfiguration.DEFAULT_CONFIGURATION);
        blobStore = CassandraBlobStore.forTesting(cassandra.getConf());
        attachmentMessageIdDAO = new CassandraAttachmentMessageIdDAO(cassandra.getConf(), new CassandraMessageId.Factory());
        CassandraAttachmentOwnerDAO ownerDAO = new CassandraAttachmentOwnerDAO(cassandra.getConf());
        attachmentMapper = new CassandraAttachmentMapper(attachmentDAO, attachmentDAOV2, blobStore, attachmentMessageIdDAO, ownerDAO);
    }

    @Test
    void getAttachmentShouldThrowWhenAbsentFromV1AndV2() {
        assertThatThrownBy(() -> attachmentMapper.getAttachment(ATTACHMENT_ID_1))
            .isInstanceOf(AttachmentNotFoundException.class);
    }

    @Test
    void getAttachmentsShouldReturnEmptyWhenAbsentFromV1AndV2() {
        assertThat(attachmentMapper.getAttachments(ImmutableList.of(ATTACHMENT_ID_1)))
            .isEmpty();
    }

    @Test
    void getAttachmentShouldReturnV2WhenPresentInV1AndV2() throws Exception {
        Attachment attachment = Attachment.builder()
            .attachmentId(ATTACHMENT_ID_1)
            .type("application/json")
            .bytes("{\"property\":`\"value\"}".getBytes(StandardCharsets.UTF_8))
            .build();
        Attachment otherAttachment = Attachment.builder()
            .attachmentId(ATTACHMENT_ID_1)
            .type("application/json")
            .bytes("{\"property\":`\"different\"}".getBytes(StandardCharsets.UTF_8))
            .build();

        BlobId blobId = blobStore.save(blobStore.getDefaultBucketName(), attachment.getBytes(), LOW_COST).block();
        attachmentDAOV2.storeAttachment(CassandraAttachmentDAOV2.from(attachment, blobId)).block();
        attachmentDAO.storeAttachment(otherAttachment).block();

        assertThat(attachmentMapper.getAttachment(ATTACHMENT_ID_1))
            .isEqualTo(attachment);
    }

    @Test
    void getAttachmentShouldReturnV1WhenV2Absent() throws Exception {
        Attachment attachment = Attachment.builder()
            .attachmentId(ATTACHMENT_ID_1)
            .type("application/json")
            .bytes("{\"property\":`\"value\"}".getBytes(StandardCharsets.UTF_8))
            .build();

        attachmentDAO.storeAttachment(attachment).block();

        assertThat(attachmentMapper.getAttachment(ATTACHMENT_ID_1))
            .isEqualTo(attachment);
    }

    @Test
    void getAttachmentsShouldReturnV2WhenV2AndV1() throws Exception {
        Attachment attachment = Attachment.builder()
            .attachmentId(ATTACHMENT_ID_1)
            .type("application/json")
            .bytes("{\"property\":`\"value\"}".getBytes(StandardCharsets.UTF_8))
            .build();
        Attachment otherAttachment = Attachment.builder()
            .attachmentId(ATTACHMENT_ID_1)
            .type("application/json")
            .bytes("{\"property\":`\"different\"}".getBytes(StandardCharsets.UTF_8))
            .build();

        BlobId blobId = blobStore.save(blobStore.getDefaultBucketName(), attachment.getBytes(), LOW_COST).block();
        attachmentDAOV2.storeAttachment(CassandraAttachmentDAOV2.from(attachment, blobId)).block();
        attachmentDAO.storeAttachment(otherAttachment).block();

        assertThat(attachmentMapper.getAttachments(ImmutableList.of(ATTACHMENT_ID_1)))
            .containsExactly(attachment);
    }

    @Test
    void getAttachmentsShouldReturnV1WhenV2Absent() throws Exception {
        Attachment attachment = Attachment.builder()
            .attachmentId(ATTACHMENT_ID_1)
            .type("application/json")
            .bytes("{\"property\":`\"value\"}".getBytes(StandardCharsets.UTF_8))
            .build();

        attachmentDAO.storeAttachment(attachment).block();

        assertThat(attachmentMapper.getAttachments(ImmutableList.of(ATTACHMENT_ID_1)))
            .containsExactly(attachment);
    }

    @Test
    void getAttachmentsShouldCombineElementsFromV1AndV2() throws Exception {
        Attachment attachment = Attachment.builder()
            .attachmentId(ATTACHMENT_ID_1)
            .type("application/json")
            .bytes("{\"property\":`\"value\"}".getBytes(StandardCharsets.UTF_8))
            .build();
        Attachment otherAttachment = Attachment.builder()
            .attachmentId(ATTACHMENT_ID_2)
            .type("application/json")
            .bytes("{\"property\":`\"different\"}".getBytes(StandardCharsets.UTF_8))
            .build();

        BlobId blobId = blobStore.save(blobStore.getDefaultBucketName(), attachment.getBytes(), LOW_COST).block();
        attachmentDAOV2.storeAttachment(CassandraAttachmentDAOV2.from(attachment, blobId)).block();
        attachmentDAO.storeAttachment(otherAttachment).block();

        List<Attachment> attachments = attachmentMapper.getAttachments(ImmutableList.of(ATTACHMENT_ID_1, ATTACHMENT_ID_2));
        assertThat(attachments)
            .containsExactlyInAnyOrder(attachment, otherAttachment);
    }
}
