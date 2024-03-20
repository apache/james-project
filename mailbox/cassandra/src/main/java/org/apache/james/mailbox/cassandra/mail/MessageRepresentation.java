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

import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.apache.james.blob.api.BlobId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.model.impl.Properties;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.reactivestreams.Publisher;

public class MessageRepresentation {
    private final MessageId messageId;
    private final Date internalDate;
    private final Long size;
    private final Integer bodyStartOctet;
    private final Content content;
    private final Properties properties;
    private final List<MessageAttachmentRepresentation> attachments;
    private final BlobId headerId;
    private final BlobId bodyId;
    private final Publisher<InputStream> lazyLoadedFullContent;

    public MessageRepresentation(MessageId messageId, Date internalDate, Long size, Integer bodyStartOctet, Content content,
                                 Properties properties, List<MessageAttachmentRepresentation> attachments, BlobId headerId, BlobId bodyId,
                                 Publisher<InputStream> lazyLoadedFullContent) {
        this.messageId = messageId;
        this.internalDate = internalDate;
        this.size = size;
        this.bodyStartOctet = bodyStartOctet;
        this.content = content;
        this.properties = properties;
        this.attachments = attachments;
        this.headerId = headerId;
        this.bodyId = bodyId;
        this.lazyLoadedFullContent = lazyLoadedFullContent;
    }

    public SimpleMailboxMessage toMailboxMessage(ComposedMessageIdWithMetaData metadata, List<MessageAttachmentMetadata> attachments, Optional<Date> saveDate) {
        return SimpleMailboxMessage.builder()
            .messageId(messageId)
            .threadId(metadata.getThreadId())
            .mailboxId(metadata.getComposedMessageId().getMailboxId())
            .uid(metadata.getComposedMessageId().getUid())
            .modseq(metadata.getModSeq())
            .internalDate(internalDate)
            .saveDate(saveDate)
            .bodyStartOctet(bodyStartOctet)
            .size(size)
            .content(content)
            .flags(metadata.getFlags())
            .properties(properties)
            .addAttachments(attachments)
            .lazyLoadedFullContent(lazyLoadedFullContent)
            .build();
    }

    public Date getInternalDate() {
        return internalDate;
    }

    public Long getSize() {
        return size;
    }

    public Integer getBodyStartOctet() {
        return bodyStartOctet;
    }

    public MessageId getMessageId() {
        return messageId;
    }

    public Content getContent() {
        return content;
    }

    public Properties getProperties() {
        return properties;
    }

    public List<MessageAttachmentRepresentation> getAttachments() {
        return attachments;
    }

    public BlobId getHeaderId() {
        return headerId;
    }

    public BlobId getBodyId() {
        return bodyId;
    }

    public Publisher<InputStream> getLazyLoadedFullContent() {
        return lazyLoadedFullContent;
    }
}
