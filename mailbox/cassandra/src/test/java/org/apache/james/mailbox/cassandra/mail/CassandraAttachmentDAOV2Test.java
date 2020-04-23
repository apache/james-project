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
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2.DAOAttachment;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraAttachmentDAOV2Test {
    private static final AttachmentId ATTACHMENT_ID = AttachmentId.from("id1");
    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraAttachmentModule.MODULE);

    private CassandraAttachmentDAOV2 testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraAttachmentDAOV2(BLOB_ID_FACTORY, cassandra.getConf());
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
}
