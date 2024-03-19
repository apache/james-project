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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Optional;

import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.StringBackedAttachmentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

class AttachmentLoaderTest {

    private CassandraAttachmentMapper attachmentMapper;
    private AttachmentLoader testee;

    @BeforeEach
    void setup() {
        attachmentMapper = mock(CassandraAttachmentMapper.class);
        testee = new AttachmentLoader(attachmentMapper);
    }

    @Test
    void getAttachmentsShouldWorkWithDuplicatedAttachments() {
        StringBackedAttachmentId attachmentId = StringBackedAttachmentId.from("1");

        AttachmentMetadata attachment = AttachmentMetadata.builder()
            .attachmentId(attachmentId)
            .size(11)
            .type("type")
            .messageId(CassandraMessageId.Factory.of(Uuids.random()))
            .build();

        when(attachmentMapper.getAttachmentsAsMono(attachmentId))
            .thenReturn(Mono.just(attachment));

        Optional<String> name = Optional.of("name1");
        Optional<Cid> cid = Optional.empty();
        boolean isInlined = false;
        MessageAttachmentRepresentation attachmentRepresentation = new MessageAttachmentRepresentation(attachmentId, name, cid, isInlined);

        Collection<MessageAttachmentMetadata> attachments = testee.getAttachments(ImmutableList.of(attachmentRepresentation, attachmentRepresentation))
            .block();

        MessageAttachmentMetadata expectedAttachment = new MessageAttachmentMetadata(attachment, name, cid, isInlined);
        assertThat(attachments).hasSize(2)
            .containsOnly(expectedAttachment, expectedAttachment);
    }

    @Test
    void getAttachmentsShouldWorkWithDuplicatedIds() {
        StringBackedAttachmentId attachmentId = StringBackedAttachmentId.from("1");

        AttachmentMetadata attachment = AttachmentMetadata.builder()
            .attachmentId(attachmentId)
            .size(11)
            .messageId(CassandraMessageId.Factory.of(Uuids.random()))
            .type("type")
            .build();

        when(attachmentMapper.getAttachmentsAsMono(attachmentId))
                .thenReturn(Mono.just(attachment));

        Optional<String> name1 = Optional.of("name1");
        Optional<String> name2 = Optional.of("name2");
        Optional<Cid> cid = Optional.empty();
        boolean isInlined = false;
        MessageAttachmentRepresentation attachmentRepresentation1 = new MessageAttachmentRepresentation(attachmentId, name1, cid, isInlined);
        MessageAttachmentRepresentation attachmentRepresentation2 = new MessageAttachmentRepresentation(attachmentId, name2, cid, isInlined);

        Collection<MessageAttachmentMetadata> attachments = testee.getAttachments(ImmutableList.of(attachmentRepresentation1, attachmentRepresentation2))
            .block();

        assertThat(attachments).hasSize(2)
            .containsOnly(new MessageAttachmentMetadata(attachment, name1, cid, isInlined),
                new MessageAttachmentMetadata(attachment, name2, cid, isInlined));
    }

    @Test
    void getAttachmentsShouldReturnMultipleAttachmentWhenSeveralAttachmentsRepresentation() {
        StringBackedAttachmentId attachmentId1 = StringBackedAttachmentId.from("1");
        StringBackedAttachmentId attachmentId2 = StringBackedAttachmentId.from("2");

        AttachmentMetadata attachment1 = AttachmentMetadata.builder()
            .attachmentId(attachmentId1)
            .messageId(CassandraMessageId.Factory.of(Uuids.random()))
            .size(12)
            .type("type")
            .build();
        AttachmentMetadata attachment2 = AttachmentMetadata.builder()
            .attachmentId(attachmentId2)
            .messageId(CassandraMessageId.Factory.of(Uuids.random()))
            .size(13)
            .type("type")
            .build();

        when(attachmentMapper.getAttachmentsAsMono(attachmentId1))
                .thenReturn(Mono.just(attachment1));
        when(attachmentMapper.getAttachmentsAsMono(attachmentId2))
                .thenReturn(Mono.just(attachment2));

        Optional<String> name1 = Optional.of("name1");
        Optional<String> name2 = Optional.of("name2");
        Optional<Cid> cid = Optional.empty();
        boolean isInlined = false;
        MessageAttachmentRepresentation attachmentRepresentation1 = new MessageAttachmentRepresentation(attachmentId1, name1, cid, isInlined);
        MessageAttachmentRepresentation attachmentRepresentation2 = new MessageAttachmentRepresentation(attachmentId2, name2, cid, isInlined);

        Collection<MessageAttachmentMetadata> attachments = testee.getAttachments(ImmutableList.of(attachmentRepresentation1, attachmentRepresentation2))
            .block();

        assertThat(attachments).hasSize(2)
            .containsOnly(new MessageAttachmentMetadata(attachment1, name1, cid, isInlined),
                new MessageAttachmentMetadata(attachment2, name2, cid, isInlined));
    }

    @Test
    void getAttachmentsShouldReturnEmptyByDefault() {
        StringBackedAttachmentId attachmentId = StringBackedAttachmentId.from("1");

        AttachmentMetadata attachment = AttachmentMetadata.builder()
            .attachmentId(attachmentId)
            .size(11)
            .messageId(CassandraMessageId.Factory.of(Uuids.random()))
            .type("type")
            .build();

        when(attachmentMapper.getAttachmentsAsMono(attachmentId))
                .thenReturn(Mono.just(attachment));

        Collection<MessageAttachmentMetadata> attachments = testee.getAttachments(ImmutableList.of())
            .block();

        assertThat(attachments).isEmpty();
    }
}
