/**************************************************************
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
 **************************************************************/


package org.apache.james.mailbox.jpa.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import java.nio.charset.StandardCharsets;

import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.mailbox.jpa.JPAMailboxFixture;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.model.AttachmentMapperTest;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;

class JPAAttachmentMapperTest extends AttachmentMapperTest {

    private static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(JPAMailboxFixture.MAILBOX_PERSISTANCE_CLASSES);

    @AfterEach
    void cleanUp() {
        JPA_TEST_CLUSTER.clear(JPAMailboxFixture.MAILBOX_TABLE_NAMES);
    }

    @Override
    protected AttachmentMapper createAttachmentMapper() {
        return new TransactionalAttachmentMapper(new JPAAttachmentMapper(JPA_TEST_CLUSTER.getEntityManagerFactory()));
    }

    @Override
    protected MessageId generateMessageId() {
        return new DefaultMessageId.Factory().generate();
    }

    @Test
    @Override
    public void getAttachmentsShouldReturnTheAttachmentsWhenSome() throws Exception {
        //Given
        ContentType content1 = ContentType.of("content");
        byte[] bytes1 = "payload".getBytes(StandardCharsets.UTF_8);
        ContentType content2 = ContentType.of("content");
        byte[] bytes2 = "payload".getBytes(StandardCharsets.UTF_8);

        MessageId messageId1 = generateMessageId();
        AttachmentMetadata stored1 = attachmentMapper.storeAttachments(ImmutableList.of(ParsedAttachment.builder()
                .contentType(content1)
                .content(ByteSource.wrap(bytes1))
                .noName()
                .noCid()
                .inline(false)), messageId1).get(0)
            .getAttachment();
        AttachmentMetadata stored2 = attachmentMapper.storeAttachments(ImmutableList.of(ParsedAttachment.builder()
                .contentType(content2)
                .content(ByteSource.wrap(bytes2))
                .noName()
                .noCid()
                .inline(false)), messageId1).get(0)
            .getAttachment();

        // JPA does not support MessageId
        assertThat(attachmentMapper.getAttachments(ImmutableList.of(stored1.getAttachmentId(), stored2.getAttachmentId())))
            .extracting(
                AttachmentMetadata::getAttachmentId,
                AttachmentMetadata::getSize,
                AttachmentMetadata::getType
            )
            .contains(
                tuple(stored1.getAttachmentId(), stored1.getSize(), stored1.getType()),
                tuple(stored2.getAttachmentId(), stored2.getSize(), stored2.getType())
            );
    }

}
