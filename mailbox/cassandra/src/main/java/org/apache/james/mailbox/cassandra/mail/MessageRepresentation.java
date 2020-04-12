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

import java.util.Date;
import java.util.List;

import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;

public class MessageRepresentation {
    private final MessageId messageId;
    private final Date internalDate;
    private final Long size;
    private final Integer bodySize;
    private final SharedByteArrayInputStream content;
    private final PropertyBuilder propertyBuilder;
    private final boolean hasAttachment;
    private final List<MessageAttachmentRepresentation> attachments;

    public MessageRepresentation(MessageId messageId, Date internalDate, Long size, Integer bodySize, SharedByteArrayInputStream content,
                                 PropertyBuilder propertyBuilder, boolean hasAttachment, List<MessageAttachmentRepresentation> attachments) {
        this.messageId = messageId;
        this.internalDate = internalDate;
        this.size = size;
        this.bodySize = bodySize;
        this.content = content;
        this.propertyBuilder = propertyBuilder;
        this.hasAttachment = hasAttachment;
        this.attachments = attachments;
    }

    public SimpleMailboxMessage toMailboxMessage(ComposedMessageIdWithMetaData metadata, List<MessageAttachmentMetadata> attachments) {
        return SimpleMailboxMessage.builder()
            .messageId(messageId)
            .mailboxId(metadata.getComposedMessageId().getMailboxId())
            .uid(metadata.getComposedMessageId().getUid())
            .modseq(metadata.getModSeq())
            .internalDate(internalDate)
            .bodyStartOctet(bodySize)
            .size(size)
            .content(content)
            .flags(metadata.getFlags())
            .propertyBuilder(propertyBuilder)
            .addAttachments(attachments)
            .hasAttachment(hasAttachment)
            .build();
    }

    public MessageId getMessageId() {
        return messageId;
    }

    public SharedByteArrayInputStream getContent() {
        return content;
    }

    public PropertyBuilder getPropertyBuilder() {
        return propertyBuilder;
    }

    public List<MessageAttachmentRepresentation> getAttachments() {
        return attachments;
    }
}
