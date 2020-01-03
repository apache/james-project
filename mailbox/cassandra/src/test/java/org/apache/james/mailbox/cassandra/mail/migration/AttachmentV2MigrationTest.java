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

package org.apache.james.mailbox.cassandra.mail.migration;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobStore;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class AttachmentV2MigrationTest {
    private static final AttachmentId ATTACHMENT_ID = AttachmentId.from("id1");
    private static final AttachmentId ATTACHMENT_ID_2 = AttachmentId.from("id2");
    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(
            CassandraAttachmentModule.MODULE,
            CassandraBlobModule.MODULE));

    private CassandraAttachmentDAO attachmentDAO;
    private CassandraAttachmentDAOV2 attachmentDAOV2;
    private CassandraBlobStore blobsStore;
    private AttachmentV2Migration migration;
    private Attachment attachment1;
    private Attachment attachment2;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        attachmentDAO = new CassandraAttachmentDAO(cassandra.getConf(),
            CassandraConfiguration.DEFAULT_CONFIGURATION);
        attachmentDAOV2 = new CassandraAttachmentDAOV2(BLOB_ID_FACTORY, cassandra.getConf());
        blobsStore = new CassandraBlobStore(cassandra.getConf());
        migration = new AttachmentV2Migration(attachmentDAO, attachmentDAOV2, blobsStore);

        attachment1 = Attachment.builder()
            .attachmentId(ATTACHMENT_ID)
            .type("application/json")
            .bytes("{\"property\":`\"value1\"}".getBytes(StandardCharsets.UTF_8))
            .build();
        attachment2 = Attachment.builder()
            .attachmentId(ATTACHMENT_ID_2)
            .type("application/json")
            .bytes("{\"property\":`\"value2\"}".getBytes(StandardCharsets.UTF_8))
            .build();
    }

    @Test
    void emptyMigrationShouldSucceed() throws InterruptedException {
        assertThat(migration.asTask().run())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void migrationShouldSucceed() throws Exception {
        attachmentDAO.storeAttachment(attachment1).block();
        attachmentDAO.storeAttachment(attachment2).block();

        assertThat(migration.asTask().run())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void migrationShouldMoveAttachmentsToV2() throws Exception {
        attachmentDAO.storeAttachment(attachment1).block();
        attachmentDAO.storeAttachment(attachment2).block();

        migration.apply();

        assertThat(attachmentDAOV2.getAttachment(ATTACHMENT_ID).blockOptional())
            .contains(CassandraAttachmentDAOV2.from(attachment1, BLOB_ID_FACTORY.forPayload(attachment1.getBytes())));
        assertThat(attachmentDAOV2.getAttachment(ATTACHMENT_ID_2).blockOptional())
            .contains(CassandraAttachmentDAOV2.from(attachment2, BLOB_ID_FACTORY.forPayload(attachment2.getBytes())));
        assertThat(blobsStore.readBytes(blobsStore.getDefaultBucketName(), BLOB_ID_FACTORY.forPayload(attachment1.getBytes())).block())
            .isEqualTo(attachment1.getBytes());
        assertThat(blobsStore.readBytes(blobsStore.getDefaultBucketName(), BLOB_ID_FACTORY.forPayload(attachment2.getBytes())).block())
            .isEqualTo(attachment2.getBytes());
    }

    @Test
    void migrationShouldRemoveAttachmentsFromV1() throws Exception {
        attachmentDAO.storeAttachment(attachment1).block();
        attachmentDAO.storeAttachment(attachment2).block();

        migration.apply();

        assertThat(attachmentDAO.getAttachment(ATTACHMENT_ID).blockOptional())
            .isEmpty();
        assertThat(attachmentDAO.getAttachment(ATTACHMENT_ID_2).blockOptional())
            .isEmpty();
    }

    @Test
    void runShouldReturnPartialWhenInitialReadFail() throws InterruptedException {
        CassandraAttachmentDAO attachmentDAO = mock(CassandraAttachmentDAO.class);
        CassandraAttachmentDAOV2 attachmentDAOV2 = mock(CassandraAttachmentDAOV2.class);
        CassandraBlobStore blobsStore = mock(CassandraBlobStore.class);
        migration = new AttachmentV2Migration(attachmentDAO, attachmentDAOV2, blobsStore);

        when(attachmentDAO.retrieveAll()).thenReturn(Flux.error(new RuntimeException()));

        assertThat(migration.asTask().run()).isEqualTo(Task.Result.PARTIAL);
    }

    @Test
    void runShouldReturnPartialWhenSavingBlobsFails() throws InterruptedException {
        CassandraAttachmentDAO attachmentDAO = mock(CassandraAttachmentDAO.class);
        CassandraAttachmentDAOV2 attachmentDAOV2 = mock(CassandraAttachmentDAOV2.class);
        CassandraBlobStore blobsStore = mock(CassandraBlobStore.class);
        migration = new AttachmentV2Migration(attachmentDAO, attachmentDAOV2, blobsStore);

        when(attachmentDAO.retrieveAll()).thenReturn(Flux.just(
            attachment1,
            attachment2));
        when(blobsStore.save(any(BucketName.class), any(byte[].class), eq(LOW_COST))).thenThrow(new RuntimeException());

        assertThat(migration.asTask().run()).isEqualTo(Task.Result.PARTIAL);
    }

    @Test
    void runShouldReturnPartialWhenSavingAttachmentV2Fail() throws InterruptedException {
        CassandraAttachmentDAO attachmentDAO = mock(CassandraAttachmentDAO.class);
        CassandraAttachmentDAOV2 attachmentDAOV2 = mock(CassandraAttachmentDAOV2.class);
        CassandraBlobStore blobsStore = mock(CassandraBlobStore.class);
        migration = new AttachmentV2Migration(attachmentDAO, attachmentDAOV2, blobsStore);

        when(attachmentDAO.retrieveAll()).thenReturn(Flux.just(
            attachment1,
            attachment2));
        when(blobsStore.save(blobsStore.getDefaultBucketName(), attachment1.getBytes(), LOW_COST))
            .thenReturn(Mono.just(BLOB_ID_FACTORY.forPayload(attachment1.getBytes())));
        when(blobsStore.save(blobsStore.getDefaultBucketName(), attachment2.getBytes(), LOW_COST))
            .thenReturn(Mono.just(BLOB_ID_FACTORY.forPayload(attachment2.getBytes())));
        when(attachmentDAOV2.storeAttachment(any())).thenThrow(new RuntimeException());

        assertThat(migration.asTask().run()).isEqualTo(Task.Result.PARTIAL);
    }

    @Test
    void runShouldReturnPartialWhenDeleteV1AttachmentFail() throws InterruptedException {
        CassandraAttachmentDAO attachmentDAO = mock(CassandraAttachmentDAO.class);
        CassandraAttachmentDAOV2 attachmentDAOV2 = mock(CassandraAttachmentDAOV2.class);
        CassandraBlobStore blobsStore = mock(CassandraBlobStore.class);
        migration = new AttachmentV2Migration(attachmentDAO, attachmentDAOV2, blobsStore);

        when(attachmentDAO.retrieveAll()).thenReturn(Flux.just(
            attachment1,
            attachment2));
        when(blobsStore.save(blobsStore.getDefaultBucketName(), attachment1.getBytes(), LOW_COST))
            .thenReturn(Mono.just(BLOB_ID_FACTORY.forPayload(attachment1.getBytes())));
        when(blobsStore.save(blobsStore.getDefaultBucketName(), attachment2.getBytes(), LOW_COST))
            .thenReturn(Mono.just(BLOB_ID_FACTORY.forPayload(attachment2.getBytes())));
        when(attachmentDAOV2.storeAttachment(any())).thenReturn(Mono.empty());
        when(attachmentDAO.deleteAttachment(any())).thenThrow(new RuntimeException());

        assertThat(migration.asTask().run()).isEqualTo(Task.Result.PARTIAL);
    }

    @Test
    void runShouldReturnPartialWhenAtLeastOneAttachmentMigrationFails() throws InterruptedException {
        CassandraAttachmentDAO attachmentDAO = mock(CassandraAttachmentDAO.class);
        CassandraAttachmentDAOV2 attachmentDAOV2 = mock(CassandraAttachmentDAOV2.class);
        CassandraBlobStore blobsStore = mock(CassandraBlobStore.class);
        migration = new AttachmentV2Migration(attachmentDAO, attachmentDAOV2, blobsStore);

        when(attachmentDAO.retrieveAll()).thenReturn(Flux.just(
            attachment1,
            attachment2));
        when(blobsStore.save(blobsStore.getDefaultBucketName(), attachment1.getBytes(), LOW_COST))
            .thenReturn(Mono.just(BLOB_ID_FACTORY.forPayload(attachment1.getBytes())));
        when(blobsStore.save(blobsStore.getDefaultBucketName(), attachment2.getBytes(), LOW_COST))
            .thenThrow(new RuntimeException());
        when(attachmentDAOV2.storeAttachment(any())).thenReturn(Mono.empty());
        when(attachmentDAO.deleteAttachment(any())).thenReturn(Mono.empty());

        assertThat(migration.asTask().run()).isEqualTo(Task.Result.PARTIAL);
    }

}
