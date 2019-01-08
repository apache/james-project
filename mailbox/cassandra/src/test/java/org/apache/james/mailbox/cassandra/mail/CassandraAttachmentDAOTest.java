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
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.cassandra.modules.CassandraAttachmentModule;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.steveash.guavate.Guavate;

class CassandraAttachmentDAOTest {
    private static final AttachmentId ATTACHMENT_ID = AttachmentId.from("id1");
    private static final AttachmentId ATTACHMENT_ID_2 = AttachmentId.from("id2");

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraAttachmentModule.MODULE);

    private CassandraAttachmentDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraAttachmentDAO(cassandra.getConf(),
            CassandraUtils.WITH_DEFAULT_CONFIGURATION,
            CassandraConfiguration.DEFAULT_CONFIGURATION);
    }

    @Test
    void getAttachmentShouldReturnEmptyWhenAbsent() {
        Optional<Attachment> attachment = testee.getAttachment(ATTACHMENT_ID).blockOptional();

        assertThat(attachment).isEmpty();
    }

    @Test
    void retrieveAllShouldReturnEmptyByDefault() {
        assertThat(
            testee.retrieveAll()
                .collect(Guavate.toImmutableList()))
            .isEmpty();
    }

    @Test
    void retrieveAllShouldReturnStoredAttachments() throws Exception {
        Attachment attachment1 = Attachment.builder()
            .attachmentId(ATTACHMENT_ID)
            .type("application/json")
            .bytes("{\"property\":`\"value1\"}".getBytes(StandardCharsets.UTF_8))
            .build();
        Attachment attachment2 = Attachment.builder()
            .attachmentId(ATTACHMENT_ID_2)
            .type("application/json")
            .bytes("{\"property\":`\"value2\"}".getBytes(StandardCharsets.UTF_8))
            .build();
        testee.storeAttachment(attachment1).join();
        testee.storeAttachment(attachment2).join();

        assertThat(
            testee.retrieveAll()
                .collect(Guavate.toImmutableList()))
            .containsOnly(attachment1, attachment2);
    }

    @Test
    void getAttachmentShouldReturnAttachmentWhenStored() throws Exception {
        Attachment attachment = Attachment.builder()
            .attachmentId(ATTACHMENT_ID)
            .type("application/json")
            .bytes("{\"property\":`\"value\"}".getBytes(StandardCharsets.UTF_8))
            .build();
        testee.storeAttachment(attachment).join();

        Optional<Attachment> actual = testee.getAttachment(ATTACHMENT_ID).blockOptional();

        assertThat(actual).contains(attachment);
    }

    @Test
    void deleteAttachmentShouldRemoveAttachment() throws Exception {
        Attachment attachment = Attachment.builder()
            .attachmentId(ATTACHMENT_ID)
            .type("application/json")
            .bytes("{\"property\":`\"value\"}".getBytes(StandardCharsets.UTF_8))
            .build();
        testee.storeAttachment(attachment).join();

        testee.deleteAttachment(attachment.getAttachmentId()).join();

        assertThat(testee.getAttachment(attachment.getAttachmentId()).blockOptional())
            .isEmpty();
    }
}
