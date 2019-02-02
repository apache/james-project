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

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;

public abstract class AttachmentMapperTest {
    private static final AttachmentId UNKNOWN_ATTACHMENT_ID = AttachmentId.from("unknown");
    public static final Username OWNER = Username.fromRawValue("owner");
    public static final Username ADDITIONAL_OWNER = Username.fromRawValue("additionalOwner");

    private AttachmentMapper attachmentMapper;

    @Rule
    public ExpectedException expected = ExpectedException.none();

    protected abstract AttachmentMapper createAttachmentMapper();

    protected abstract MessageId generateMessageId();

    public void setUp() throws MailboxException {
        this.attachmentMapper = createAttachmentMapper();
    }

    @Test
    public void getAttachmentShouldThrowWhenNullAttachmentId() throws Exception {
        expected.expect(IllegalArgumentException.class);
        attachmentMapper.getAttachment(null);
    }

    @Test
    public void getAttachmentShouldThrowWhenNonReferencedAttachmentId() throws Exception {
        expected.expect(AttachmentNotFoundException.class);
        attachmentMapper.getAttachment(UNKNOWN_ATTACHMENT_ID);
    }

    @Test
    public void getAttachmentShouldReturnTheAttachmentWhenReferenced() throws Exception {
        //Given
        Attachment expected = Attachment.builder()
                .bytes("payload".getBytes(StandardCharsets.UTF_8))
                .type("content")
                .build();
        AttachmentId attachmentId = expected.getAttachmentId();
        attachmentMapper.storeAttachmentForOwner(expected, OWNER);
        //When
        Attachment attachment = attachmentMapper.getAttachment(attachmentId);
        //Then
        assertThat(attachment).isEqualTo(expected);
    }

    @Test
    public void getAttachmentShouldReturnTheAttachmentsWhenMultipleStored() throws Exception {
        //Given
        Attachment expected1 = Attachment.builder()
                .bytes("payload1".getBytes(StandardCharsets.UTF_8))
                .type("content1")
                .build();
        Attachment expected2 = Attachment.builder()
                .bytes("payload2".getBytes(StandardCharsets.UTF_8))
                .type("content2")
                .build();
        AttachmentId attachmentId1 = expected1.getAttachmentId();
        AttachmentId attachmentId2 = expected2.getAttachmentId();
        //When
        attachmentMapper.storeAttachmentsForMessage(ImmutableList.of(expected1, expected2), generateMessageId());
        //Then
        Attachment attachment1 = attachmentMapper.getAttachment(attachmentId1);
        Attachment attachment2 = attachmentMapper.getAttachment(attachmentId2);
        assertThat(attachment1).isEqualTo(expected1);
        assertThat(attachment2).isEqualTo(expected2);
    }

    @Test
    public void getAttachmentsShouldThrowWhenNullAttachmentId() {
        expected.expect(IllegalArgumentException.class);
        attachmentMapper.getAttachments(null);
    }

    @Test
    public void getAttachmentsShouldReturnEmptyListWhenNonReferencedAttachmentId() {
        List<Attachment> attachments = attachmentMapper.getAttachments(ImmutableList.of(UNKNOWN_ATTACHMENT_ID));

        assertThat(attachments).isEmpty();
    }

    @Test
    public void getAttachmentsShouldReturnTheAttachmentsWhenSome() throws Exception {
        //Given
        Attachment expected = Attachment.builder()
                .bytes("payload".getBytes(StandardCharsets.UTF_8))
                .type("content")
                .build();
        AttachmentId attachmentId = expected.getAttachmentId();
        attachmentMapper.storeAttachmentForOwner(expected, OWNER);

        Attachment expected2 = Attachment.builder()
                .bytes("payload2".getBytes(StandardCharsets.UTF_8))
                .type("content")
                .build();
        AttachmentId attachmentId2 = expected2.getAttachmentId();
        attachmentMapper.storeAttachmentForOwner(expected2, OWNER);

        //When
        List<Attachment> attachments = attachmentMapper.getAttachments(ImmutableList.of(attachmentId, attachmentId2));
        //Then
        assertThat(attachments).contains(expected, expected2);
    }

    @Test
    public void getOwnerMessageIdsShouldReturnEmptyWhenNone() throws Exception {
        Collection<MessageId> messageIds = attachmentMapper.getRelatedMessageIds(UNKNOWN_ATTACHMENT_ID);

        assertThat(messageIds).isEmpty();
    }

    @Test
    public void getOwnerMessageIdsShouldReturnEmptyWhenStoredWithoutMessageId() throws Exception {
        //Given
        Attachment attachment = Attachment.builder()
                .bytes("payload".getBytes(StandardCharsets.UTF_8))
                .type("content")
                .build();
        AttachmentId attachmentId = attachment.getAttachmentId();
        attachmentMapper.storeAttachmentForOwner(attachment, OWNER);
        
        //When
        Collection<MessageId> messageIds = attachmentMapper.getRelatedMessageIds(attachmentId);
        //Then
        assertThat(messageIds).isEmpty();
    }

    @Test
    public void getOwnerMessageIdsShouldReturnMessageIdWhenStoredWithMessageId() throws Exception {
        //Given
        Attachment attachment = Attachment.builder()
                .bytes("payload".getBytes(StandardCharsets.UTF_8))
                .type("content")
                .build();
        AttachmentId attachmentId = attachment.getAttachmentId();
        MessageId messageId = generateMessageId();
        attachmentMapper.storeAttachmentsForMessage(ImmutableList.of(attachment), messageId);
        
        //When
        Collection<MessageId> messageIds = attachmentMapper.getRelatedMessageIds(attachmentId);
        //Then
        assertThat(messageIds).containsOnly(messageId);
    }

    @Test
    public void getOwnerMessageIdsShouldReturnTwoMessageIdsWhenStoredTwice() throws Exception {
        //Given
        Attachment attachment = Attachment.builder()
                .bytes("payload".getBytes(StandardCharsets.UTF_8))
                .type("content")
                .build();
        AttachmentId attachmentId = attachment.getAttachmentId();
        MessageId messageId1 = generateMessageId();
        MessageId messageId2 = generateMessageId();
        attachmentMapper.storeAttachmentsForMessage(ImmutableList.of(attachment), messageId1);
        attachmentMapper.storeAttachmentsForMessage(ImmutableList.of(attachment), messageId2);
        
        //When
        Collection<MessageId> messageIds = attachmentMapper.getRelatedMessageIds(attachmentId);
        //Then
        assertThat(messageIds).containsOnly(messageId1, messageId2);
    }

    @Test
    public void getOwnerMessageIdsShouldReturnOnlyMatchingMessageId() throws Exception {
        //Given
        Attachment attachment = Attachment.builder()
                .bytes("payload".getBytes(StandardCharsets.UTF_8))
                .type("content")
                .build();
        Attachment otherAttachment = Attachment.builder()
                .bytes("something different".getBytes(StandardCharsets.UTF_8))
                .type("content")
                .build();
        AttachmentId attachmentId = attachment.getAttachmentId();
        MessageId messageId1 = generateMessageId();
        MessageId messageId2 = generateMessageId();
        attachmentMapper.storeAttachmentsForMessage(ImmutableList.of(attachment), messageId1);
        attachmentMapper.storeAttachmentsForMessage(ImmutableList.of(otherAttachment), messageId2);
        
        //When
        Collection<MessageId> messageIds = attachmentMapper.getRelatedMessageIds(attachmentId);
        //Then
        assertThat(messageIds).containsOnly(messageId1);
    }

    @Test
    public void getOwnerMessageIdsShouldReturnOnlyOneMessageIdWhenStoredTwice() throws Exception {
        //Given
        Attachment attachment = Attachment.builder()
                .bytes("payload".getBytes(StandardCharsets.UTF_8))
                .type("content")
                .build();
        AttachmentId attachmentId = attachment.getAttachmentId();
        MessageId messageId = generateMessageId();
        attachmentMapper.storeAttachmentsForMessage(ImmutableList.of(attachment), messageId);
        attachmentMapper.storeAttachmentsForMessage(ImmutableList.of(attachment), messageId);
        
        //When
        Collection<MessageId> messageIds = attachmentMapper.getRelatedMessageIds(attachmentId);
        //Then
        assertThat(messageIds).containsOnly(messageId);
    }

    @Test
    public void getOwnerMessageIdsShouldReturnMessageIdForTwoAttachmentsWhenBothStoredAtTheSameTime() throws Exception {
        //Given
        Attachment attachment = Attachment.builder()
                .bytes("payload".getBytes(StandardCharsets.UTF_8))
                .type("content")
                .build();
        Attachment attachment2 = Attachment.builder()
                .bytes("other payload".getBytes(StandardCharsets.UTF_8))
                .type("content")
                .build();
        AttachmentId attachmentId = attachment.getAttachmentId();
        AttachmentId attachmentId2 = attachment2.getAttachmentId();
        MessageId messageId = generateMessageId();
        attachmentMapper.storeAttachmentsForMessage(ImmutableList.of(attachment, attachment2), messageId);
        
        //When
        Collection<MessageId> messageIds = attachmentMapper.getRelatedMessageIds(attachmentId);
        Collection<MessageId> messageIds2 = attachmentMapper.getRelatedMessageIds(attachmentId2);
        //Then
        assertThat(messageIds).isEqualTo(messageIds2);
    }

    @Test
    public void getOwnersShouldBeRetrievedWhenExplicitlySpecified() throws Exception {
        //Given
        Attachment attachment = Attachment.builder()
            .bytes("payload".getBytes(StandardCharsets.UTF_8))
            .type("content")
            .build();

        AttachmentId attachmentId = attachment.getAttachmentId();
        attachmentMapper.storeAttachmentForOwner(attachment, OWNER);

        //When
        Collection<Username> expectedOwners = ImmutableList.of(OWNER);
        Collection<Username> actualOwners = attachmentMapper.getOwners(attachmentId);
        //Then
        assertThat(actualOwners).containsOnlyElementsOf(expectedOwners);
    }

    @Test
    public void getOwnersShouldReturnEmptyWhenMessageIdReferenced() throws Exception {
        //Given
        Attachment attachment = Attachment.builder()
            .bytes("payload".getBytes(StandardCharsets.UTF_8))
            .type("content")
            .build();

        AttachmentId attachmentId = attachment.getAttachmentId();
        attachmentMapper.storeAttachmentsForMessage(ImmutableList.of(attachment), generateMessageId());

        //When
        Collection<Username> actualOwners = attachmentMapper.getOwners(attachmentId);
        //Then
        assertThat(actualOwners).isEmpty();
    }

    @Test
    public void getOwnersShouldReturnAllOwners() throws Exception {
        //Given
        Attachment attachment = Attachment.builder()
            .bytes("payload".getBytes(StandardCharsets.UTF_8))
            .type("content")
            .build();

        AttachmentId attachmentId = attachment.getAttachmentId();
        attachmentMapper.storeAttachmentForOwner(attachment, OWNER);
        attachmentMapper.storeAttachmentForOwner(attachment, ADDITIONAL_OWNER);

        //When
        Collection<Username> expectedOwners = ImmutableList.of(OWNER, ADDITIONAL_OWNER);
        Collection<Username> actualOwners = attachmentMapper.getOwners(attachmentId);
        //Then
        assertThat(actualOwners).containsOnlyElementsOf(expectedOwners);
    }

    @Test
    public void getOwnersShouldReturnEmptyWhenUnknownAttachmentId() throws Exception {
        Collection<Username> actualOwners = attachmentMapper.getOwners(AttachmentId.from("any"));

        assertThat(actualOwners).isEmpty();
    }
}
