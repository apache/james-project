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
package org.apache.james.mailbox.store;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Date;
import java.util.List;

import javax.mail.Flags;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.model.FlagsFactory;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Property;

import com.google.common.collect.ImmutableList;

public class ImmutableMailboxMessage implements MailboxMessage {

    public static class Factory {

        private final MailboxManager mailboxManager;

        public Factory(MailboxManager mailboxManager) {
            this.mailboxManager = mailboxManager;
        }

        public ImmutableMailboxMessage from(MailboxId mailboxId, MailboxMessage message) throws MailboxException {
            try {
                return new ImmutableMailboxMessage(message.getMessageId(),
                        message.getInternalDate(),
                        IOUtils.toByteArray(message.getBodyContent()),
                        message.getMediaType(),
                        message.getSubType(),
                        message.getBodyOctets(),
                        message.getFullContentOctets(),
                        message.getFullContentOctets() - message.getBodyOctets(),
                        message.getTextualLineCount(),
                        IOUtils.toByteArray(message.getHeaderContent()),
                        ImmutableList.copyOf(message.getProperties()),
                        attachments(message),
                        mailboxId,
                        message.getUid(),
                        message.getModSeq(),
                        message.isAnswered(),
                        message.isDeleted(),
                        message.isDraft(),
                        message.isFlagged(),
                        message.isRecent(),
                        message.isSeen(),
                        message.createFlags().getUserFlags());
            } catch (IOException e) {
                throw new MailboxException("Unable to parse message", e);
            }
        }

        private ImmutableList<MessageAttachment> attachments(MailboxMessage message) {
            if (mailboxManager.getSupportedMessageCapabilities().contains(MailboxManager.MessageCapabilities.Attachment)) {
                return ImmutableList.copyOf(message.getAttachments());
            }
            return ImmutableList.of();
        }
    }

    private final MessageId messageId;
    private final Date internalDate;
    private final byte[] bodyContent;
    private final String mediaType;
    private final String subType;
    private final long bodyOctets;
    private final long fullContentOctets;
    private final long headerOctets;
    private final Long textualLineCount;
    private final byte[] headerContent;
    private final List<Property> properties;
    private final List<MessageAttachment> attachments;
    private final MailboxId mailboxId;
    private final MessageUid uid;
    private final long modSeq;
    private final boolean answered;
    private final boolean deleted;
    private final boolean draft;
    private final boolean flagged;
    private final boolean recent;
    private final boolean seen;
    private final String[] userFlags;

    private ImmutableMailboxMessage(MessageId messageId, Date internalDate, byte[] bodyContent, String mediaType, String subType, long bodyOctets, long fullContentOctets, long headerOctets, Long textualLineCount, byte[] headerContent,
                                    List<Property> properties, List<MessageAttachment> attachments, MailboxId mailboxId, MessageUid uid, long modSeq, boolean answered, boolean deleted, boolean draft, boolean flagged, boolean recent,
                                    boolean seen, String[] userFlags) {
        this.messageId = messageId;
        this.internalDate = internalDate;
        this.bodyContent = bodyContent;
        this.mediaType = mediaType;
        this.subType = subType;
        this.bodyOctets = bodyOctets;
        this.fullContentOctets = fullContentOctets;
        this.headerOctets = headerOctets; 
        this.textualLineCount = textualLineCount;
        this.headerContent = headerContent;
        this.properties = properties;
        this.attachments = attachments;
        this.mailboxId = mailboxId;
        this.uid = uid;
        this.modSeq = modSeq;
        this.answered = answered;
        this.deleted = deleted;
        this.draft = draft;
        this.flagged = flagged;
        this.recent = recent;
        this.seen = seen;
        this.userFlags = userFlags;
    }

    @Override
    public ComposedMessageIdWithMetaData getComposedMessageIdWithMetaData() {
        return ComposedMessageIdWithMetaData.builder()
            .modSeq(modSeq)
            .flags(createFlags())
            .composedMessageId(new ComposedMessageId(mailboxId, getMessageId(), uid))
            .build();
    }

    @Override
    public MessageId getMessageId() {
        return messageId;
    }

    @Override
    public Date getInternalDate() {
        return internalDate;
    }

    @Override
    public InputStream getBodyContent() {
        return new ByteArrayInputStream(bodyContent);
    }

    @Override
    public String getMediaType() {
        return mediaType;
    }

    @Override
    public String getSubType() {
        return subType;
    }

    @Override
    public long getBodyOctets() {
        return bodyOctets;
    }

    @Override
    public long getFullContentOctets() {
        return fullContentOctets;
    }
    
    @Override
    public long getHeaderOctets() {
        return headerOctets;
    }

    @Override
    public Long getTextualLineCount() {
        return textualLineCount;
    }

    @Override
    public InputStream getHeaderContent() {
        return new ByteArrayInputStream(headerContent);
    }

    @Override
    public InputStream getFullContent() {
        return new SequenceInputStream(
            new ByteArrayInputStream(headerContent),
            new ByteArrayInputStream(bodyContent));
    }

    @Override
    public List<Property> getProperties() {
        return properties;
    }

    @Override
    public List<MessageAttachment> getAttachments() {
        return attachments;
    }

    @Override
    public MailboxId getMailboxId() {
        return mailboxId;
    }

    @Override
    public MessageUid getUid() {
        return uid;
    }

    @Override
    public long getModSeq() {
        return modSeq;
    }

    @Override
    public boolean isAnswered() {
        return answered;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public boolean isDraft() {
        return draft;
    }

    @Override
    public boolean isFlagged() {
        return flagged;
    }

    @Override
    public boolean isRecent() {
        return recent;
    }

    @Override
    public boolean isSeen() {
        return seen;
    }

    @Override
    public int compareTo(MailboxMessage o) {
        throw new NotImplementedException("Not implemented");
    }

    @Override
    public void setUid(MessageUid uid) {
        throw new NotImplementedException("Not implemented");
    }

    @Override
    public void setModSeq(long modSeq) {
        throw new NotImplementedException("Not implemented");
    }

    @Override
    public void setFlags(Flags flags) {
        throw new NotImplementedException("Not implemented");
    }

    @Override
    public Flags createFlags() {
        return FlagsFactory.createFlags(this, userFlags);
    }

}
