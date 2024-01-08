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

import org.apache.james.blob.api.BlobId;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.MessageId;

import com.google.common.base.Preconditions;

public class MessageRepresentation {
    public static MessageRepresentation.Builder builder() {
        return new MessageRepresentation.Builder();
    }

    public static class Builder {
        private MessageId messageId;
        private Date internalDate;
        private Long size;
        private Content headerContent;
        private BlobId bodyBlobId;

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

        public MessageRepresentation build() {
            Preconditions.checkNotNull(messageId, "messageId is required");
            Preconditions.checkNotNull(internalDate, "internalDate is required");
            Preconditions.checkNotNull(size, "size is required");
            Preconditions.checkNotNull(headerContent, "headerContent is required");
            Preconditions.checkNotNull(bodyBlobId, "mailboxId is required");

            return new MessageRepresentation(messageId, internalDate, size, headerContent, bodyBlobId);
        }
    }

    private final MessageId messageId;
    private final Date internalDate;
    private final Long size;
    private final Content headerContent;
    private final BlobId bodyBlobId;

    private MessageRepresentation(MessageId messageId, Date internalDate, Long size,
                                  Content headerContent, BlobId bodyBlobId) {
        this.messageId = messageId;
        this.internalDate = internalDate;
        this.size = size;
        this.headerContent = headerContent;
        this.bodyBlobId = bodyBlobId;
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
}
