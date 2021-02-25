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

package org.apache.james.mailbox.store.mail.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Mono;

public abstract class AttachmentMapperTest {
    private static final AttachmentId UNKNOWN_ATTACHMENT_ID = AttachmentId.from("unknown");
    private static final Username OWNER = Username.of("owner");
    private static final Username ADDITIONAL_OWNER = Username.of("additionalOwner");

    private AttachmentMapper attachmentMapper;

    protected abstract AttachmentMapper createAttachmentMapper();

    protected abstract MessageId generateMessageId();

    @BeforeEach
    void setUp() {
        this.attachmentMapper = createAttachmentMapper();
    }

    @Test
    void getAttachmentShouldThrowWhenNullAttachmentId() {
        assertThatThrownBy(() -> attachmentMapper.getAttachment(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getAttachmentShouldThrowWhenNonReferencedAttachmentId() {
        assertThatThrownBy(() -> attachmentMapper.getAttachment(UNKNOWN_ATTACHMENT_ID))
            .isInstanceOf(AttachmentNotFoundException.class);
    }

    @Test
    void storeAttachmentForOwnerShouldReturnSuppliedInformation() throws Exception {
        ContentType content = ContentType.of("content");
        byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);

        AttachmentMetadata stored = Mono.from(attachmentMapper.storeAttachmentForOwner(content, new ByteArrayInputStream(bytes), OWNER)).block();

        SoftAssertions.assertSoftly(solftly -> {
            solftly.assertThat(stored.getSize()).isEqualTo(bytes.length);
            solftly.assertThat(stored.getType()).isEqualTo(content);
        });
    }

    @Test
    void getAttachmentShouldReturnTheAttachmentWhenReferenced() throws Exception {
        ContentType content = ContentType.of("content");
        byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);

        AttachmentMetadata stored = Mono.from(attachmentMapper.storeAttachmentForOwner(content, new ByteArrayInputStream(bytes), OWNER)).block();

        AttachmentMetadata attachment = attachmentMapper.getAttachment(stored.getAttachmentId());

        SoftAssertions.assertSoftly(solftly -> {
            solftly.assertThat(attachment.getAttachmentId()).isEqualTo(stored.getAttachmentId());
            solftly.assertThat(attachment.getSize()).isEqualTo(bytes.length);
            solftly.assertThat(attachment.getType()).isEqualTo(content);
        });
    }

    @Test
    void loadAttachmentContentShouldReturnStoredContent() throws Exception {
        ContentType content = ContentType.of("content");
        byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);

        AttachmentMetadata stored = Mono.from(attachmentMapper.storeAttachmentForOwner(content, new ByteArrayInputStream(bytes), OWNER)).block();

        assertThat(attachmentMapper.loadAttachmentContent(stored.getAttachmentId()))
            .hasSameContentAs(new ByteArrayInputStream(bytes));
    }

    @Test
    void getAttachmentsShouldThrowWhenNullAttachmentId() {
        assertThatThrownBy(() -> attachmentMapper.getAttachments(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getAttachmentsShouldReturnEmptyListWhenNonReferencedAttachmentId() {
        List<AttachmentMetadata> attachments = attachmentMapper.getAttachments(ImmutableList.of(UNKNOWN_ATTACHMENT_ID));

        assertThat(attachments).isEmpty();
    }

    @Test
    void getAttachmentsShouldReturnTheAttachmentsWhenSome() {
        //Given
        ContentType content1 = ContentType.of("content");
        byte[] bytes1 = "payload".getBytes(StandardCharsets.UTF_8);
        AttachmentMetadata stored1 = Mono.from(attachmentMapper.storeAttachmentForOwner(content1, new ByteArrayInputStream(bytes1), OWNER)).block();
        ContentType content2 = ContentType.of("content");
        byte[] bytes2 = "payload".getBytes(StandardCharsets.UTF_8);
        AttachmentMetadata stored2 = Mono.from(attachmentMapper.storeAttachmentForOwner(content2, new ByteArrayInputStream(bytes2), OWNER)).block();

        assertThat(attachmentMapper.getAttachments(ImmutableList.of(stored1.getAttachmentId(), stored2.getAttachmentId())))
            .contains(stored1, stored2);
    }

    @Test
    void getOwnerMessageIdsShouldReturnEmptyWhenNone() throws Exception {
        Collection<MessageId> messageIds = attachmentMapper.getRelatedMessageIds(UNKNOWN_ATTACHMENT_ID);

        assertThat(messageIds).isEmpty();
    }

    @Test
    void getOwnerMessageIdsShouldReturnEmptyWhenStoredWithoutMessageId() throws Exception {
        ContentType content = ContentType.of("content");
        byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);
        AttachmentMetadata stored = Mono.from(attachmentMapper.storeAttachmentForOwner(content, new ByteArrayInputStream(bytes), OWNER)).block();

        assertThat(attachmentMapper.getRelatedMessageIds(stored.getAttachmentId())).isEmpty();
    }

    @Test
    void getRelatedMessageIdsShouldReturnMessageIdWhenStoredWithMessageId() throws Exception {
        MessageId messageId = generateMessageId();
        AttachmentId attachmentId = attachmentMapper.storeAttachmentsForMessage(ImmutableList.of(ParsedAttachment.builder()
            .contentType("content")
            .content(ByteSource.empty())
            .noName()
            .noCid()
            .inline(false)), messageId)
            .get(0).getAttachmentId();

        assertThat(attachmentMapper.getRelatedMessageIds(attachmentId)).containsOnly(messageId);
    }

    @Test
    void getOwnerMessageIdsShouldReturnOnlyMatchingMessageId() throws Exception {
        MessageId messageId1 = generateMessageId();
        AttachmentId attachmentId1 = attachmentMapper.storeAttachmentsForMessage(ImmutableList.of(ParsedAttachment.builder()
            .contentType("content")
            .content(ByteSource.wrap("".getBytes(StandardCharsets.UTF_8)))
            .noName()
            .noCid()
            .inline(false)), messageId1)
            .get(0).getAttachmentId();
        attachmentMapper.storeAttachmentsForMessage(ImmutableList.of(ParsedAttachment.builder()
            .contentType("content")
            .content(ByteSource.wrap("".getBytes(StandardCharsets.UTF_8)))
            .noName()
            .noCid()
            .inline(false)), generateMessageId())
            .get(0).getAttachmentId();

        assertThat(attachmentMapper.getRelatedMessageIds(attachmentId1)).containsOnly(messageId1);
    }

    @Test
    void getOwnersShouldBeRetrievedWhenExplicitlySpecified() throws Exception {
        ContentType content = ContentType.of("content");
        byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);
        AttachmentMetadata stored = Mono.from(attachmentMapper.storeAttachmentForOwner(content, new ByteArrayInputStream(bytes), OWNER)).block();

        Collection<Username> actualOwners = attachmentMapper.getOwners(stored.getAttachmentId());

        assertThat(actualOwners).containsOnly(OWNER);
    }

    @Test
    void getOwnersShouldNotReturnUnrelatedOwners() throws Exception {
        ContentType content = ContentType.of("content");
        byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);
        AttachmentMetadata stored = Mono.from(attachmentMapper.storeAttachmentForOwner(content, new ByteArrayInputStream(bytes), OWNER)).block();
        ContentType content2 = ContentType.of("content");
        byte[] bytes2 = "payload".getBytes(StandardCharsets.UTF_8);
        AttachmentMetadata stored2 = Mono.from(attachmentMapper.storeAttachmentForOwner(content2, new ByteArrayInputStream(bytes2), ADDITIONAL_OWNER)).block();

        Collection<Username> actualOwners = attachmentMapper.getOwners(stored.getAttachmentId());

        assertThat(actualOwners).containsOnly(OWNER);
    }

    @Test
    void getOwnersShouldReturnEmptyWhenMessageIdReferenced() throws Exception {
        AttachmentId attachmentId = attachmentMapper.storeAttachmentsForMessage(ImmutableList.of(ParsedAttachment.builder()
            .contentType("content")
            .content(ByteSource.wrap("".getBytes(StandardCharsets.UTF_8)))
            .noName()
            .noCid()
            .inline(false)), generateMessageId())
            .get(0).getAttachmentId();

        assertThat(attachmentMapper.getOwners(attachmentId)).isEmpty();
    }

    @Test
    void getOwnersShouldReturnEmptyWhenUnknownAttachmentId() throws Exception {
        Collection<Username> actualOwners = attachmentMapper.getOwners(AttachmentId.from("any"));

        assertThat(actualOwners).isEmpty();
    }
}
