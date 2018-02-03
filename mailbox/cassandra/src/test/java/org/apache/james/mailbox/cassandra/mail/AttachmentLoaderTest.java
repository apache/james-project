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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.MessageAttachment;
import org.assertj.core.data.MapEntry;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class AttachmentLoaderTest {

    private CassandraAttachmentMapper attachmentMapper;
    private AttachmentLoader testee;

    @Before
    public void setup() {
        attachmentMapper = mock(CassandraAttachmentMapper.class);
        testee = new AttachmentLoader(attachmentMapper);
    }

    @Test
    public void getAttachmentsShouldWorkWithDuplicatedAttachments() {
        AttachmentId attachmentId = AttachmentId.from("1");
        Set<AttachmentId> attachmentIds = ImmutableSet.of(attachmentId);

        Attachment attachment = Attachment.builder()
            .attachmentId(attachmentId)
            .bytes("attachment".getBytes())
            .type("type")
            .build();
        when(attachmentMapper.getAttachmentsAsFuture(attachmentIds))
            .thenReturn(CompletableFuture.completedFuture(ImmutableList.of(attachment)));

        Optional<String> name = Optional.of("name1");
        Optional<Cid> cid = Optional.empty();
        boolean isInlined = false;
        MessageAttachmentRepresentation attachmentRepresentation = new MessageAttachmentRepresentation(attachmentId, name, cid, isInlined);

        Collection<MessageAttachment> attachments = testee.getAttachments(ImmutableList.of(attachmentRepresentation, attachmentRepresentation))
            .join();

        MessageAttachment expectedAttachment = new MessageAttachment(attachment, name, cid, isInlined);
        assertThat(attachments).hasSize(2)
            .containsOnly(expectedAttachment, expectedAttachment);
    }

    @Test
    public void getAttachmentsShouldWorkWithDuplicatedIds() {
        AttachmentId attachmentId = AttachmentId.from("1");
        Set<AttachmentId> attachmentIds = ImmutableSet.of(attachmentId);

        Attachment attachment = Attachment.builder()
            .attachmentId(attachmentId)
            .bytes("attachment".getBytes())
            .type("type")
            .build();
        when(attachmentMapper.getAttachmentsAsFuture(attachmentIds))
            .thenReturn(CompletableFuture.completedFuture(ImmutableList.of(attachment)));

        Optional<String> name1 = Optional.of("name1");
        Optional<String> name2 = Optional.of("name2");
        Optional<Cid> cid = Optional.empty();
        boolean isInlined = false;
        MessageAttachmentRepresentation attachmentRepresentation1 = new MessageAttachmentRepresentation(attachmentId, name1, cid, isInlined);
        MessageAttachmentRepresentation attachmentRepresentation2 = new MessageAttachmentRepresentation(attachmentId, name2, cid, isInlined);

        Collection<MessageAttachment> attachments = testee.getAttachments(ImmutableList.of(attachmentRepresentation1, attachmentRepresentation2))
            .join();

        assertThat(attachments).hasSize(2)
            .containsOnly(new MessageAttachment(attachment, name1, cid, isInlined),
                new MessageAttachment(attachment, name2, cid, isInlined));
    }

    @Test
    public void getAttachmentsShouldReturnMultipleAttachmentWhenSeveralAttachmentsRepresentation() {
        AttachmentId attachmentId1 = AttachmentId.from("1");
        AttachmentId attachmentId2 = AttachmentId.from("2");
        Set<AttachmentId> attachmentIds = ImmutableSet.of(attachmentId1, attachmentId2);

        Attachment attachment1 = Attachment.builder()
            .attachmentId(attachmentId1)
            .bytes("attachment1".getBytes())
            .type("type")
            .build();
        Attachment attachment2 = Attachment.builder()
            .attachmentId(attachmentId2)
            .bytes("attachment2".getBytes())
            .type("type")
            .build();
        when(attachmentMapper.getAttachmentsAsFuture(attachmentIds))
            .thenReturn(CompletableFuture.completedFuture(ImmutableList.of(attachment1, attachment2)));

        Optional<String> name1 = Optional.of("name1");
        Optional<String> name2 = Optional.of("name2");
        Optional<Cid> cid = Optional.empty();
        boolean isInlined = false;
        MessageAttachmentRepresentation attachmentRepresentation1 = new MessageAttachmentRepresentation(attachmentId1, name1, cid, isInlined);
        MessageAttachmentRepresentation attachmentRepresentation2 = new MessageAttachmentRepresentation(attachmentId2, name2, cid, isInlined);

        Collection<MessageAttachment> attachments = testee.getAttachments(ImmutableList.of(attachmentRepresentation1, attachmentRepresentation2))
            .join();

        assertThat(attachments).hasSize(2)
            .containsOnly(new MessageAttachment(attachment1, name1, cid, isInlined),
                new MessageAttachment(attachment2, name2, cid, isInlined));
    }

    @Test
    public void getAttachmentsShouldReturnEmptyByDefault() {
        AttachmentId attachmentId = AttachmentId.from("1");
        Set<AttachmentId> attachmentIds = ImmutableSet.of(attachmentId);

        Attachment attachment = Attachment.builder()
            .attachmentId(attachmentId)
            .bytes("attachment".getBytes())
            .type("type")
            .build();
        when(attachmentMapper.getAttachmentsAsFuture(attachmentIds))
            .thenReturn(CompletableFuture.completedFuture(ImmutableList.of(attachment)));

        Collection<MessageAttachment> attachments = testee.getAttachments(ImmutableList.of())
            .join();

        assertThat(attachments).isEmpty();
    }

    @Test
    public void attachmentsByIdShouldReturnMapWhenExist() {
        AttachmentId attachmentId = AttachmentId.from("1");
        AttachmentId attachmentId2 = AttachmentId.from("2");
        Set<AttachmentId> attachmentIds = ImmutableSet.of(attachmentId, attachmentId2);

        Attachment attachment = Attachment.builder()
                .attachmentId(attachmentId)
                .bytes("attachment".getBytes())
                .type("type")
                .build();
        Attachment attachment2 = Attachment.builder()
                .attachmentId(attachmentId2)
                .bytes("attachment2".getBytes())
                .type("type")
                .build();
        when(attachmentMapper.getAttachmentsAsFuture(attachmentIds))
            .thenReturn(CompletableFuture.completedFuture(ImmutableList.of(attachment, attachment2)));

        Map<AttachmentId, Attachment> attachmentsById = testee.attachmentsById(attachmentIds)
            .join();

        assertThat(attachmentsById).hasSize(2)
                .containsOnly(MapEntry.entry(attachmentId, attachment), MapEntry.entry(attachmentId2, attachment2));
    }

    @Test
    public void attachmentsByIdShouldReturnEmptyMapWhenAttachmentsDontExists() {
        AttachmentId attachmentId = AttachmentId.from("1");
        AttachmentId attachmentId2 = AttachmentId.from("2");
        Set<AttachmentId> attachmentIds = ImmutableSet.of(attachmentId, attachmentId2);

        when(attachmentMapper.getAttachmentsAsFuture(attachmentIds))
                .thenReturn(CompletableFuture.completedFuture(ImmutableList.of()));

        Map<AttachmentId, Attachment> attachmentsById = testee.attachmentsById(attachmentIds)
            .join();

        assertThat(attachmentsById).hasSize(0);
    }

}
