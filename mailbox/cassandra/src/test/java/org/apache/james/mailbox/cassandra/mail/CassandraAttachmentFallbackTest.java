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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.cassandra.CassandraBlobId;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobsDAO;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

public class CassandraAttachmentFallbackTest {
    public static final AttachmentId ATTACHMENT_ID_1 = AttachmentId.from("id1");
    public static final AttachmentId ATTACHMENT_ID_2 = AttachmentId.from("id2");
    private static final CassandraBlobId.Factory BLOB_ID_FACTORY = new CassandraBlobId.Factory();

    @ClassRule
    public static DockerCassandraRule cassandraServer = new DockerCassandraRule();

    private static CassandraCluster cassandra;

    private CassandraAttachmentDAOV2 attachmentDAOV2;
    private CassandraAttachmentDAO attachmentDAO;
    private CassandraAttachmentMapper attachmentMapper;
    private CassandraBlobsDAO blobsDAO;
    private CassandraAttachmentMessageIdDAO attachmentMessageIdDAO;

    @BeforeClass
    public static void setUpClass() {
        CassandraModule modules = CassandraModule.aggregateModules(
            CassandraAttachmentModule.MODULE,
            CassandraBlobModule.MODULE);
        cassandra = CassandraCluster.create(modules, cassandraServer.getHost());
    }

    @Before
    public void setUp() throws Exception {
        attachmentDAOV2 = new CassandraAttachmentDAOV2(BLOB_ID_FACTORY, cassandra.getConf());
        attachmentDAO = new CassandraAttachmentDAO(cassandra.getConf(),
            CassandraUtils.WITH_DEFAULT_CONFIGURATION,
            CassandraConfiguration.DEFAULT_CONFIGURATION);
        blobsDAO = new CassandraBlobsDAO(cassandra.getConf());
        attachmentMessageIdDAO = new CassandraAttachmentMessageIdDAO(cassandra.getConf(), new CassandraMessageId.Factory(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
        CassandraAttachmentOwnerDAO ownerDAO = new CassandraAttachmentOwnerDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
        attachmentMapper = new CassandraAttachmentMapper(attachmentDAO, attachmentDAOV2, blobsDAO, attachmentMessageIdDAO, ownerDAO);
    }

    @After
    public void tearDown() {
        cassandra.clearTables();
    }

    @AfterClass
    public static void tearDownClass() {
        cassandra.closeCluster();
    }

    @Test
    public void getAttachmentShouldThrowWhenAbsentFromV1AndV2() throws Exception {
        assertThatThrownBy(() -> attachmentMapper.getAttachment(ATTACHMENT_ID_1))
            .isInstanceOf(AttachmentNotFoundException.class);
    }

    @Test
    public void getAttachmentsShouldReturnEmptyWhenAbsentFromV1AndV2() throws Exception {
        assertThat(attachmentMapper.getAttachments(ImmutableList.of(ATTACHMENT_ID_1)))
            .isEmpty();
    }

    @Test
    public void getAttachmentShouldReturnV2WhenPresentInV1AndV2() throws Exception {
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

        BlobId blobId = blobsDAO.save(attachment.getBytes()).join();
        attachmentDAOV2.storeAttachment(CassandraAttachmentDAOV2.from(attachment, blobId)).join();
        attachmentDAO.storeAttachment(otherAttachment).join();

        assertThat(attachmentMapper.getAttachment(ATTACHMENT_ID_1))
            .isEqualTo(attachment);
    }

    @Test
    public void getAttachmentShouldReturnV1WhenV2Absent() throws Exception {
        Attachment attachment = Attachment.builder()
            .attachmentId(ATTACHMENT_ID_1)
            .type("application/json")
            .bytes("{\"property\":`\"value\"}".getBytes(StandardCharsets.UTF_8))
            .build();

        attachmentDAO.storeAttachment(attachment).join();

        assertThat(attachmentMapper.getAttachment(ATTACHMENT_ID_1))
            .isEqualTo(attachment);
    }

    @Test
    public void getAttachmentsShouldReturnV2WhenV2AndV1() throws Exception {
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

        BlobId blobId = blobsDAO.save(attachment.getBytes()).join();
        attachmentDAOV2.storeAttachment(CassandraAttachmentDAOV2.from(attachment, blobId)).join();
        attachmentDAO.storeAttachment(otherAttachment).join();

        assertThat(attachmentMapper.getAttachments(ImmutableList.of(ATTACHMENT_ID_1)))
            .containsExactly(attachment);
    }

    @Test
    public void getAttachmentsShouldReturnV1WhenV2Absent() throws Exception {
        Attachment attachment = Attachment.builder()
            .attachmentId(ATTACHMENT_ID_1)
            .type("application/json")
            .bytes("{\"property\":`\"value\"}".getBytes(StandardCharsets.UTF_8))
            .build();

        attachmentDAO.storeAttachment(attachment).join();

        assertThat(attachmentMapper.getAttachments(ImmutableList.of(ATTACHMENT_ID_1)))
            .containsExactly(attachment);
    }

    @Test
    public void getAttachmentsShouldCombineElementsFromV1AndV2() throws Exception {
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

        BlobId blobId = blobsDAO.save(attachment.getBytes()).join();
        attachmentDAOV2.storeAttachment(CassandraAttachmentDAOV2.from(attachment, blobId)).join();
        attachmentDAO.storeAttachment(otherAttachment).join();

        assertThat(attachmentMapper.getAttachments(ImmutableList.of(ATTACHMENT_ID_1, ATTACHMENT_ID_2)))
            .containsExactly(attachment, otherAttachment);
    }
}
