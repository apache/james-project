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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.migration.Migration;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobsDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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
    private CassandraBlobsDAO blobsDAO;
    private AttachmentV2Migration migration;
    private Attachment attachment1;
    private Attachment attachment2;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        attachmentDAO = new CassandraAttachmentDAO(cassandra.getConf(),
            CassandraUtils.WITH_DEFAULT_CONFIGURATION,
            CassandraConfiguration.DEFAULT_CONFIGURATION);
        attachmentDAOV2 = new CassandraAttachmentDAOV2(BLOB_ID_FACTORY, cassandra.getConf());
        blobsDAO = new CassandraBlobsDAO(cassandra.getConf());
        migration = new AttachmentV2Migration(attachmentDAO, attachmentDAOV2, blobsDAO);

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
    void emptyMigrationShouldSucceed() {
        assertThat(migration.run())
            .isEqualTo(Migration.Result.COMPLETED);
    }

    @Test
    void migrationShouldSucceed() throws Exception {
        attachmentDAO.storeAttachment(attachment1).join();
        attachmentDAO.storeAttachment(attachment2).join();

        assertThat(migration.run())
            .isEqualTo(Migration.Result.COMPLETED);
    }

    @Test
    void migrationShouldMoveAttachmentsToV2() throws Exception {
        attachmentDAO.storeAttachment(attachment1).join();
        attachmentDAO.storeAttachment(attachment2).join();

        migration.run();

        assertThat(attachmentDAOV2.getAttachment(ATTACHMENT_ID).blockOptional())
            .contains(CassandraAttachmentDAOV2.from(attachment1, BLOB_ID_FACTORY.forPayload(attachment1.getBytes())));
        assertThat(attachmentDAOV2.getAttachment(ATTACHMENT_ID_2).blockOptional())
            .contains(CassandraAttachmentDAOV2.from(attachment2, BLOB_ID_FACTORY.forPayload(attachment2.getBytes())));
        assertThat(blobsDAO.readBytes(BLOB_ID_FACTORY.forPayload(attachment1.getBytes())).join())
            .isEqualTo(attachment1.getBytes());
        assertThat(blobsDAO.readBytes(BLOB_ID_FACTORY.forPayload(attachment2.getBytes())).join())
            .isEqualTo(attachment2.getBytes());
    }

    @Test
    void migrationShouldRemoveAttachmentsFromV1() throws Exception {
        attachmentDAO.storeAttachment(attachment1).join();
        attachmentDAO.storeAttachment(attachment2).join();

        migration.run();

        assertThat(attachmentDAO.getAttachment(ATTACHMENT_ID).blockOptional())
            .isEmpty();
        assertThat(attachmentDAO.getAttachment(ATTACHMENT_ID_2).blockOptional())
            .isEmpty();
    }

    @Test
    void runShouldReturnPartialWhenInitialReadFail() {
        CassandraAttachmentDAO attachmentDAO = mock(CassandraAttachmentDAO.class);
        CassandraAttachmentDAOV2 attachmentDAOV2 = mock(CassandraAttachmentDAOV2.class);
        CassandraBlobsDAO blobsDAO = mock(CassandraBlobsDAO.class);
        migration = new AttachmentV2Migration(attachmentDAO, attachmentDAOV2, blobsDAO);

        when(attachmentDAO.retrieveAll()).thenThrow(new RuntimeException());

        assertThat(migration.run()).isEqualTo(Migration.Result.PARTIAL);
    }

    @Test
    void runShouldReturnPartialWhenSavingBlobsFails() {
        CassandraAttachmentDAO attachmentDAO = mock(CassandraAttachmentDAO.class);
        CassandraAttachmentDAOV2 attachmentDAOV2 = mock(CassandraAttachmentDAOV2.class);
        CassandraBlobsDAO blobsDAO = mock(CassandraBlobsDAO.class);
        migration = new AttachmentV2Migration(attachmentDAO, attachmentDAOV2, blobsDAO);

        when(attachmentDAO.retrieveAll()).thenReturn(Stream.of(
            attachment1,
            attachment2));
        when(blobsDAO.save(any(byte[].class))).thenThrow(new RuntimeException());

        assertThat(migration.run()).isEqualTo(Migration.Result.PARTIAL);
    }

    @Test
    void runShouldReturnPartialWhenSavingAttachmentV2Fail() {
        CassandraAttachmentDAO attachmentDAO = mock(CassandraAttachmentDAO.class);
        CassandraAttachmentDAOV2 attachmentDAOV2 = mock(CassandraAttachmentDAOV2.class);
        CassandraBlobsDAO blobsDAO = mock(CassandraBlobsDAO.class);
        migration = new AttachmentV2Migration(attachmentDAO, attachmentDAOV2, blobsDAO);

        when(attachmentDAO.retrieveAll()).thenReturn(Stream.of(
            attachment1,
            attachment2));
        when(blobsDAO.save(attachment1.getBytes()))
            .thenReturn(CompletableFuture.completedFuture(BLOB_ID_FACTORY.forPayload(attachment1.getBytes())));
        when(blobsDAO.save(attachment2.getBytes()))
            .thenReturn(CompletableFuture.completedFuture(BLOB_ID_FACTORY.forPayload(attachment2.getBytes())));
        when(attachmentDAOV2.storeAttachment(any())).thenThrow(new RuntimeException());

        assertThat(migration.run()).isEqualTo(Migration.Result.PARTIAL);
    }

    @Test
    void runShouldReturnPartialWhenDeleteV1AttachmentFail() {
        CassandraAttachmentDAO attachmentDAO = mock(CassandraAttachmentDAO.class);
        CassandraAttachmentDAOV2 attachmentDAOV2 = mock(CassandraAttachmentDAOV2.class);
        CassandraBlobsDAO blobsDAO = mock(CassandraBlobsDAO.class);
        migration = new AttachmentV2Migration(attachmentDAO, attachmentDAOV2, blobsDAO);

        when(attachmentDAO.retrieveAll()).thenReturn(Stream.of(
            attachment1,
            attachment2));
        when(blobsDAO.save(attachment1.getBytes()))
            .thenReturn(CompletableFuture.completedFuture(BLOB_ID_FACTORY.forPayload(attachment1.getBytes())));
        when(blobsDAO.save(attachment2.getBytes()))
            .thenReturn(CompletableFuture.completedFuture(BLOB_ID_FACTORY.forPayload(attachment2.getBytes())));
        when(attachmentDAOV2.storeAttachment(any())).thenReturn(Mono.empty());
        when(attachmentDAO.deleteAttachment(any())).thenThrow(new RuntimeException());

        assertThat(migration.run()).isEqualTo(Migration.Result.PARTIAL);
    }

    @Test
    void runShouldReturnPartialWhenAtLeastOneAttachmentMigrationFails() {
        CassandraAttachmentDAO attachmentDAO = mock(CassandraAttachmentDAO.class);
        CassandraAttachmentDAOV2 attachmentDAOV2 = mock(CassandraAttachmentDAOV2.class);
        CassandraBlobsDAO blobsDAO = mock(CassandraBlobsDAO.class);
        migration = new AttachmentV2Migration(attachmentDAO, attachmentDAOV2, blobsDAO);

        when(attachmentDAO.retrieveAll()).thenReturn(Stream.of(
            attachment1,
            attachment2));
        when(blobsDAO.save(attachment1.getBytes()))
            .thenReturn(CompletableFuture.completedFuture(BLOB_ID_FACTORY.forPayload(attachment1.getBytes())));
        when(blobsDAO.save(attachment2.getBytes()))
            .thenThrow(new RuntimeException());
        when(attachmentDAOV2.storeAttachment(any())).thenReturn(Mono.empty());
        when(attachmentDAO.deleteAttachment(any())).thenReturn(CompletableFuture.completedFuture(null));

        assertThat(migration.run()).isEqualTo(Migration.Result.PARTIAL);
    }

}