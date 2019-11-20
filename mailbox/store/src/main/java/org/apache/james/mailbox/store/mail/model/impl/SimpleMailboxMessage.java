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

package org.apache.james.mailbox.store.mail.model.impl;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.mail.Flags;
import javax.mail.internet.SharedInputStream;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.model.DelegatingMailboxMessage;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;

public class SimpleMailboxMessage extends DelegatingMailboxMessage {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MessageId messageId;
        private Date internalDate;
        private Long size;
        private Integer bodyStartOctet;
        private SharedInputStream content;
        private Flags flags;
        private PropertyBuilder propertyBuilder;
        private MailboxId mailboxId;
        private Optional<MessageUid> uid = Optional.empty();
        private Optional<ModSeq> modseq = Optional.empty();
        private ImmutableList.Builder<MessageAttachment> attachments = ImmutableList.builder();
        private Optional<Boolean> hasAttachment = Optional.empty();

        public Builder messageId(MessageId messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder uid(MessageUid messageUid) {
            this.uid = Optional.of(messageUid);
            return this;
        }

        public Builder modseq(ModSeq modseq) {
            this.modseq = Optional.of(modseq);
            return this;
        }

        public Builder internalDate(Date internalDate) {
            this.internalDate = internalDate;
            return this;
        }

        public Builder size(long size) {
            Preconditions.checkArgument(size >= 0, "size can not be negative");
            this.size = size;
            return this;
        }

        public Builder bodyStartOctet(int bodyStartOctet) {
            Preconditions.checkArgument(bodyStartOctet >= 0, "bodyStartOctet can not be negative");
            this.bodyStartOctet = bodyStartOctet;
            return this;
        }

        public Builder content(SharedInputStream content) {
            this.content = content;
            return this;
        }

        public Builder hasAttachment() {
            this.hasAttachment = Optional.of(true);
            return this;
        }

        public Builder hasAttachment(boolean hasAttachment) {
            this.hasAttachment = Optional.of(hasAttachment);
            return this;
        }

        public Builder flags(Flags flags) {
            this.flags = flags;
            return this;
        }

        public Builder propertyBuilder(PropertyBuilder propertyBuilder) {
            this.propertyBuilder = propertyBuilder;
            return this;
        }

        public Builder mailboxId(MailboxId mailboxId) {
            this.mailboxId = mailboxId;
            return this;
        }

        public Builder addAttachments(Collection<MessageAttachment> attachments) {
            this.attachments.addAll(attachments);
            return this;
        }

        public SimpleMailboxMessage build() {
            Preconditions.checkNotNull(messageId, "messageId is required");
            Preconditions.checkNotNull(internalDate, "internalDate is required");
            Preconditions.checkNotNull(size, "size is required");
            Preconditions.checkNotNull(bodyStartOctet, "bodyStartOctet is required");
            Preconditions.checkNotNull(content, "content is required");
            Preconditions.checkNotNull(flags, "flags is required");
            Preconditions.checkNotNull(propertyBuilder, "propertyBuilder is required");
            Preconditions.checkNotNull(mailboxId, "mailboxId is required");

            ImmutableList<MessageAttachment> attachments = this.attachments.build();
            boolean hasAttachment = this.hasAttachment.orElse(!attachments.isEmpty());
            SimpleMailboxMessage simpleMailboxMessage = new SimpleMailboxMessage(messageId, internalDate, size,
                bodyStartOctet, content, flags, propertyBuilder, mailboxId, attachments, hasAttachment);

            uid.ifPresent(simpleMailboxMessage::setUid);
            modseq.ifPresent(simpleMailboxMessage::setModSeq);

            return simpleMailboxMessage;
        }
    }

    public static Builder from(MailboxMessage original) throws MailboxException {
        return fromWithoutAttachments(original)
            .addAttachments(original.getAttachments());
    }

    public static SimpleMailboxMessage copy(MailboxId mailboxId, MailboxMessage original) throws MailboxException {
        return from(original).mailboxId(mailboxId).build();
    }

    public static Builder fromWithoutAttachments(MailboxMessage original) throws MailboxException {
        PropertyBuilder propertyBuilder = new PropertyBuilder(original.getProperties());
        propertyBuilder.setTextualLineCount(original.getTextualLineCount());
        return builder()
            .bodyStartOctet(Ints.checkedCast(original.getFullContentOctets() - original.getBodyOctets()))
            .content(copyFullContent(original))
            .messageId(original.getMessageId())
            .internalDate(original.getInternalDate())
            .size(original.getFullContentOctets())
            .flags(original.createFlags())
            .hasAttachment(original.hasAttachment())
            .propertyBuilder(propertyBuilder);
    }

    public static SimpleMailboxMessage copyWithoutAttachments(MailboxId mailboxId, MailboxMessage original) throws MailboxException {
        return fromWithoutAttachments(original)
            .mailboxId(mailboxId).build();
    }

    private static SharedByteArrayInputStream copyFullContent(MailboxMessage original) throws MailboxException {
        try {
            return new SharedByteArrayInputStream(IOUtils.toByteArray(original.getFullContent()));
        } catch (IOException e) {
            throw new MailboxException("Unable to parse message", e);
        }
    }

    private MessageUid uid;
    private final MailboxId mailboxId;
    private boolean answered;
    private boolean deleted;
    private boolean draft;
    private boolean flagged;
    private boolean recent;
    private boolean seen;
    private String[] userFlags;
    private ModSeq modSeq;

    public SimpleMailboxMessage(MessageId messageId, Date internalDate, long size, int bodyStartOctet,
            SharedInputStream content, Flags flags,
            PropertyBuilder propertyBuilder, MailboxId mailboxId, List<MessageAttachment> attachments,
            boolean hasAttachment) {
        super(new SimpleMessage(
                messageId,
                content, size, internalDate, propertyBuilder.getSubType(),
                propertyBuilder.getMediaType(),
                bodyStartOctet,
                propertyBuilder.getTextualLineCount(),
                propertyBuilder.toProperties(),
                attachments,
                hasAttachment));

            setFlags(flags);
            this.mailboxId = mailboxId;
            this.userFlags = flags.getUserFlags();
    }

    public SimpleMailboxMessage(MessageId messageId, Date internalDate, long size, int bodyStartOctet,
            SharedInputStream content, Flags flags,
            PropertyBuilder propertyBuilder, MailboxId mailboxId, List<MessageAttachment> attachments) {
        this(messageId, internalDate, size, bodyStartOctet,
            content, flags,
            propertyBuilder, mailboxId, attachments, !attachments.isEmpty());
    }

    public SimpleMailboxMessage(MessageId messageId, Date internalDate, long size, int bodyStartOctet,
                                SharedInputStream content, Flags flags,
                                PropertyBuilder propertyBuilder, MailboxId mailboxId) {
        this(messageId, internalDate, size, bodyStartOctet,
                content, flags,
                propertyBuilder, mailboxId, ImmutableList.of());
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
    protected String[] createUserFlags() {
        return userFlags.clone();
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
    public ModSeq getModSeq() {
        return modSeq;
    }

    @Override
    public void setModSeq(ModSeq modSeq) {
        this.modSeq = modSeq;
    }

    @Override
    public void setUid(MessageUid uid) {
        this.uid = uid;
    }

    @Override
    public synchronized void setFlags(Flags flags) {
        answered = flags.contains(Flags.Flag.ANSWERED);
        deleted = flags.contains(Flags.Flag.DELETED);
        draft = flags.contains(Flags.Flag.DRAFT);
        flagged = flags.contains(Flags.Flag.FLAGGED);
        recent = flags.contains(Flags.Flag.RECENT);
        seen = flags.contains(Flags.Flag.SEEN);
        userFlags = flags.getUserFlags();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uid);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SimpleMailboxMessage) {
            SimpleMailboxMessage other = (SimpleMailboxMessage) obj;
            return Objects.equal(this.uid, other.uid);
        }
        return false;
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("uid", this.uid)
            .add("mailboxId", this.mailboxId)
            .add("answered", this.answered)
            .add("deleted", this.deleted)
            .add("draft", this.draft)
            .add("flagged", this.flagged)
            .add("recent", this.recent)
            .add("seen", this.seen)
            .add("message", this.getMessage())
            .toString();
    }

}
