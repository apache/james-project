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

package org.apache.james.mailbox.postgres.mail;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.james.blob.api.BlobId;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;

import com.google.common.base.Preconditions;

public class MessageRepresentation {
    public static class AttachmentRepresentation {
        public static AttachmentRepresentation from(MessageAttachmentMetadata messageAttachmentMetadata) {
            return new AttachmentRepresentation(
                messageAttachmentMetadata.getAttachment().getAttachmentId(),
                messageAttachmentMetadata.getName(),
                messageAttachmentMetadata.getCid(),
                messageAttachmentMetadata.isInline());
        }

        public static List<AttachmentRepresentation> from(List<MessageAttachmentMetadata> messageAttachmentMetadata) {
            return messageAttachmentMetadata.stream()
                .map(AttachmentRepresentation::from)
                .collect(Collectors.toList());
        }

        private final AttachmentId attachmentId;
        private final Optional<String> name;
        private final Optional<Cid> cid;
        private final boolean isInline;

        public AttachmentRepresentation(AttachmentId attachmentId, Optional<String> name, Optional<Cid> cid, boolean isInline) {
            Preconditions.checkNotNull(attachmentId, "attachmentId is required");
            this.attachmentId = attachmentId;
            this.name = name;
            this.cid = cid;
            this.isInline = isInline;
        }

        public AttachmentId getAttachmentId() {
            return attachmentId;
        }

        public Optional<String> getName() {
            return name;
        }

        public Optional<Cid> getCid() {
            return cid;
        }

        public boolean isInline() {
            return isInline;
        }
    }

    public static MessageRepresentation.Builder builder() {
        return new MessageRepresentation.Builder();
    }

    public static class Builder {
        private MessageId messageId;
        private Date internalDate;
        private Long size;
        private Content headerContent;
        private BlobId bodyBlobId;

        private List<AttachmentRepresentation> attachments = List.of();

        public MessageRepresentation.Builder messageId(MessageId messageId) {
            this.messageId = messageId;
            return this;
        }

        public MessageRepresentation.Builder internalDate(Date internalDate) {
            this.internalDate = internalDate;
            return this;
        }

        public MessageRepresentation.Builder size(long size) {
            Preconditions.checkArgument(size >= 0, "size can not be negative");
            this.size = size;
            return this;
        }

        public MessageRepresentation.Builder headerContent(Content headerContent) {
            this.headerContent = headerContent;
            return this;
        }

        public MessageRepresentation.Builder bodyBlobId(BlobId bodyBlobId) {
            this.bodyBlobId = bodyBlobId;
            return this;
        }

        public MessageRepresentation.Builder attachments(List<AttachmentRepresentation> attachments) {
            this.attachments = attachments;
            return this;
        }

        public MessageRepresentation build() {
            Preconditions.checkNotNull(messageId, "messageId is required");
            Preconditions.checkNotNull(internalDate, "internalDate is required");
            Preconditions.checkNotNull(size, "size is required");
            Preconditions.checkNotNull(headerContent, "headerContent is required");
            Preconditions.checkNotNull(bodyBlobId, "mailboxId is required");

            return new MessageRepresentation(messageId, internalDate, size, headerContent, bodyBlobId, attachments);
        }
    }

    private final MessageId messageId;
    private final Date internalDate;
    private final Long size;
    private final Content headerContent;
    private final BlobId bodyBlobId;

    private final List<AttachmentRepresentation> attachments;

    private MessageRepresentation(MessageId messageId, Date internalDate, Long size,
                                  Content headerContent, BlobId bodyBlobId,
                                  List<AttachmentRepresentation> attachments) {
        this.messageId = messageId;
        this.internalDate = internalDate;
        this.size = size;
        this.headerContent = headerContent;
        this.bodyBlobId = bodyBlobId;
        this.attachments = attachments;
    }

    public Date getInternalDate() {
        return internalDate;
    }

    public Long getSize() {
        return size;
    }

    public MessageId getMessageId() {
        return messageId;
    }

    public Content getHeaderContent() {
        return headerContent;
    }

    public BlobId getBodyBlobId() {
        return bodyBlobId;
    }

    public List<AttachmentRepresentation> getAttachments() {
        return attachments;
    }
}
