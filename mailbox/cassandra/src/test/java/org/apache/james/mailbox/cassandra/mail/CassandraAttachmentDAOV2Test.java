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

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.cassandra.CassandraBlobId;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2.DAOAttachment;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class CassandraAttachmentDAOV2Test {
    public static final AttachmentId ATTACHMENT_ID = AttachmentId.from("id1");
    private static final CassandraBlobId.Factory BLOB_ID_FACTORY = new CassandraBlobId.Factory();

    @ClassRule
    public static DockerCassandraRule cassandraServer = new DockerCassandraRule();

    private static CassandraCluster cassandra;

    private CassandraAttachmentDAOV2 testee;

    @BeforeClass
    public static void setUpClass() {
        cassandra = CassandraCluster.create(CassandraAttachmentModule.MODULE, cassandraServer.getHost());
    }

    @Before
    public void setUp() throws Exception {
        testee = new CassandraAttachmentDAOV2(BLOB_ID_FACTORY, cassandra.getConf());
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
    public void getAttachmentShouldReturnEmptyWhenAbsent() {
        Optional<DAOAttachment> attachment = testee.getAttachment(ATTACHMENT_ID).join();

        assertThat(attachment).isEmpty();
    }

    @Test
    public void getAttachmentShouldReturnAttachmentWhenStored() throws Exception {
        Attachment attachment = Attachment.builder()
            .attachmentId(ATTACHMENT_ID)
            .type("application/json")
            .bytes("{\"property\":`\"value\"}".getBytes(StandardCharsets.UTF_8))
            .build();
        BlobId blobId = BLOB_ID_FACTORY.from("blobId");
        DAOAttachment daoAttachment = CassandraAttachmentDAOV2.from(attachment, blobId);
        testee.storeAttachment(daoAttachment).join();

        Optional<DAOAttachment> actual = testee.getAttachment(ATTACHMENT_ID).join();

        assertThat(actual).contains(daoAttachment);
    }
}
