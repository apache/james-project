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
import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.DelegatingMailboxMessage;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.reactivestreams.Publisher;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;

import reactor.core.publisher.Mono;

public class SimpleMailboxMessage extends DelegatingMailboxMessage {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MessageId messageId;
        private ThreadId threadId;
        private Date internalDate;
        private Optional<Date> saveDate = Optional.empty();
        private Long size;
        private Integer bodyStartOctet;
        private Content content;
        private Flags flags;
        private Properties properties;
        private MailboxId mailboxId;
        private Optional<MessageUid> uid = Optional.empty();
        private Optional<ModSeq> modseq = Optional.empty();
        private Optional<Publisher<InputStream>> lazyLoadedFullContent = Optional.empty();
        private ImmutableList.Builder<MessageAttachmentMetadata> attachments = ImmutableList.builder();

        public Builder messageId(MessageId messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder lazyLoadedFullContent(Publisher<InputStream> lazyLoadedFullContent) {
            this.lazyLoadedFullContent = Optional.of(lazyLoadedFullContent);
            return this;
        }

        public Builder threadId(ThreadId threadId) {
            this.threadId = threadId;
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

        public Builder saveDate(Date saveDate) {
            this.saveDate = Optional.ofNullable(saveDate);
            return this;
        }

        public Builder saveDate(Optional<Date> saveDate) {
            this.saveDate = saveDate;
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

        public Builder content(Content content) {
            this.content = content;
            return this;
        }

        public Builder flags(Flags flags) {
            this.flags = flags;
            return this;
        }

        public Builder properties(PropertyBuilder propertyBuilder) {
            this.properties = propertyBuilder.build();
            return this;
        }

        public Builder properties(Properties properties) {
            this.properties = properties;
            return this;
        }

        public Builder mailboxId(MailboxId mailboxId) {
            this.mailboxId = mailboxId;
            return this;
        }

        public Builder addAttachments(Collection<MessageAttachmentMetadata> attachments) {
            this.attachments.addAll(attachments);
            return this;
        }

        public SimpleMailboxMessage build() {
            Preconditions.checkNotNull(messageId, "messageId is required");
            Preconditions.checkNotNull(threadId, "threadId is required");
            Preconditions.checkNotNull(internalDate, "internalDate is required");
            Preconditions.checkNotNull(size, "size is required");
            Preconditions.checkNotNull(bodyStartOctet, "bodyStartOctet is required");
            Preconditions.checkNotNull(content, "content is required");
            Preconditions.checkNotNull(flags, "flags is required");
            Preconditions.checkNotNull(properties, "properties is required");
            Preconditions.checkNotNull(mailboxId, "mailboxId is required");

            ImmutableList<MessageAttachmentMetadata> attachments = this.attachments.build();
            SimpleMailboxMessage simpleMailboxMessage = new SimpleMailboxMessage(messageId, threadId, internalDate, size,
                bodyStartOctet, content, flags, properties, mailboxId, attachments,
                lazyLoadedFullContent.orElse(Mono.error(new NotImplementedException("build"))), saveDate);

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
        return builder()
            .threadId(original.getThreadId())
            .bodyStartOctet(Ints.checkedCast(original.getFullContentOctets() - original.getBodyOctets()))
            .content(copyFullContent(original))
            .lazyLoadedFullContent(Mono.fromCallable(original::getFullContent))
            .messageId(original.getMessageId())
            .internalDate(original.getInternalDate())
            .saveDate(original.getSaveDate())
            .size(original.getFullContentOctets())
            .flags(original.createFlags())
            .properties(original.getProperties());
    }

    public static SimpleMailboxMessage copyWithoutAttachments(MailboxId mailboxId, MailboxMessage original) throws MailboxException {
        return fromWithoutAttachments(original)
            .mailboxId(mailboxId).build();
    }

    private static Content copyFullContent(MailboxMessage original) throws MailboxException {
        try {
            return new ByteContent(IOUtils.toByteArray(original.getFullContent()));
        } catch (IOException e) {
            throw new MailboxException("Unable to parse message", e);
        }
    }

    private MessageUid uid;
    private final MailboxId mailboxId;
    private final Publisher<InputStream> lazyLoadedFullContent;
    private final ThreadId threadId;
    private Optional<Date> saveDate;
    private boolean answered;
    private boolean deleted;
    private boolean draft;
    private boolean flagged;
    private boolean recent;
    private boolean seen;
    private String[] userFlags;
    private ModSeq modSeq;

    public SimpleMailboxMessage(MessageId messageId, ThreadId threadId, Date internalDate, long size, int bodyStartOctet,
                                Content content, Flags flags,
                                Properties properties, MailboxId mailboxId, List<MessageAttachmentMetadata> attachments, Publisher<InputStream> lazyLoadedFullContent, Optional<Date> saveDate) {
        super(new SimpleMessage(
                messageId,
                content, size, internalDate,
                bodyStartOctet,
                properties.getTextualLineCount(),
                properties,
                attachments));
        this.lazyLoadedFullContent = lazyLoadedFullContent;

        setFlags(flags);
        this.mailboxId = mailboxId;
        this.threadId = threadId;
        this.saveDate = saveDate;
    }

    @VisibleForTesting
    public SimpleMailboxMessage(MessageId messageId, ThreadId threadId, Date internalDate, long size, int bodyStartOctet,
                                Content content, Flags flags,
                                Properties properties, MailboxId mailboxId) {
        this(messageId, threadId, internalDate, size, bodyStartOctet,
                content, flags,
                properties, mailboxId, ImmutableList.of(), Mono.error(() -> new NotImplementedException("SimpleMailboxMessage constructor")), EMPTY_SAVE_DATE);
    }

    @Override
    public ComposedMessageIdWithMetaData getComposedMessageIdWithMetaData() {
        return ComposedMessageIdWithMetaData.builder()
            .modSeq(modSeq)
            .flags(createFlags())
            .composedMessageId(new ComposedMessageId(mailboxId, getMessageId(), uid))
            .threadId(threadId)
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
    public ThreadId getThreadId() {
        return threadId;
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
    public void setSaveDate(Date saveDate) {
        this.saveDate = Optional.of(saveDate);
    }

    @Override
    public Optional<Date> getSaveDate() {
        return saveDate;
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
            .add("threadId", this.threadId)
            .add("saveDate", this.saveDate)
            .add("answered", this.answered)
            .add("deleted", this.deleted)
            .add("draft", this.draft)
            .add("flagged", this.flagged)
            .add("recent", this.recent)
            .add("seen", this.seen)
            .add("message", this.getMessage())
            .toString();
    }

    @Override
    public MailboxMessage copy(Mailbox mailbox) throws MailboxException {
        return SimpleMailboxMessage.copy(mailbox.getMailboxId(), this);
    }

    @Override
    public Publisher<InputStream> lazyLoadedFullContent() {
        return lazyLoadedFullContent;
    }
}
