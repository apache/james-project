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

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.blob.api.BlobId;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.Properties;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class CassandraMessageMetadata {

    public static class Builder {
        private ComposedMessageIdWithMetaData composedMessageId;
        private Optional<Date> internalDate;
        private Optional<Date> saveDate;
        private Optional<Long> bodyStartOctet;
        private Optional<Long> size;
        private Optional<BlobId> headerContent;

        public Builder() {
            this.internalDate = Optional.empty();
            this.saveDate = Optional.empty();
            this.size = Optional.empty();
            this.headerContent = Optional.empty();
            this.bodyStartOctet = Optional.empty();
        }

        public Builder ids(ComposedMessageIdWithMetaData ids) {
            this.composedMessageId = ids;
            return this;
        }

        public Builder internalDate(Date date) {
            this.internalDate = Optional.ofNullable(date);
            return this;
        }

        public Builder internalDate(Optional<Date> date) {
            this.internalDate = date;
            return this;
        }

        public Builder saveDate(Date date) {
            this.saveDate = Optional.ofNullable(date);
            return this;
        }

        public Builder saveDate(Optional<Date> date) {
            this.saveDate = date;
            return this;
        }

        public Builder bodyStartOctet(Long bodyStartOctet) {
            this.bodyStartOctet = Optional.ofNullable(bodyStartOctet);
            return this;
        }

        public Builder bodyStartOctet(Integer bodyStartOctet) {
            this.bodyStartOctet = Optional.ofNullable(bodyStartOctet)
                .map(Integer::longValue);
            return this;
        }

        public Builder bodyStartOctet(Optional<Long> bodyStartOctet) {
            this.bodyStartOctet = bodyStartOctet;
            return this;
        }

        public Builder size(Long size) {
            this.size = Optional.ofNullable(size);
            return this;
        }

        public Builder size(Optional<Long> size) {
            this.size = size;
            return this;
        }

        public Builder headerContent(Optional<BlobId> headerContent) {
            this.headerContent = headerContent;
            return this;
        }

        public CassandraMessageMetadata build() {
            Preconditions.checkState(composedMessageId != null, "'composedMessageId' is compulsory");

            return new CassandraMessageMetadata(composedMessageId, internalDate, saveDate, bodyStartOctet, size, headerContent);
        }
    }

    public static class CassandraMailboxMessage implements MailboxMessage {
        private final MailboxMessage delegate;
        private final BlobId headerBlobId;

        public CassandraMailboxMessage(MailboxMessage delegate, BlobId headerBlobId) {
            this.delegate = delegate;
            this.headerBlobId = headerBlobId;
        }

        @Override
        public ComposedMessageIdWithMetaData getComposedMessageIdWithMetaData() {
            return delegate.getComposedMessageIdWithMetaData();
        }

        @Override
        public MailboxId getMailboxId() {
            return delegate.getMailboxId();
        }

        @Override
        public MessageUid getUid() {
            return delegate.getUid();
        }

        @Override
        public void setUid(MessageUid uid) {
            delegate.setUid(uid);
        }

        @Override
        public void setModSeq(ModSeq modSeq) {
            delegate.setModSeq(modSeq);
        }

        @Override
        public ModSeq getModSeq() {
            return delegate.getModSeq();
        }

        @Override
        public boolean isAnswered() {
            return delegate.isAnswered();
        }

        @Override
        public boolean isDeleted() {
            return delegate.isDeleted();
        }

        @Override
        public boolean isDraft() {
            return delegate.isDraft();
        }

        @Override
        public boolean isFlagged() {
            return delegate.isFlagged();
        }

        @Override
        public boolean isRecent() {
            return delegate.isRecent();
        }

        @Override
        public boolean isSeen() {
            return delegate.isSeen();
        }

        @Override
        public void setFlags(Flags flags) {
            delegate.setFlags(flags);
        }

        @Override
        public void setSaveDate(Date saveDate) {
            delegate.setSaveDate(saveDate);
        }

        @Override
        public Flags createFlags() {
            return delegate.createFlags();
        }

        @Override
        public MessageId getMessageId() {
            return delegate.getMessageId();
        }

        @Override
        public Date getInternalDate() {
            return delegate.getInternalDate();
        }

        @Override
        public InputStream getBodyContent() throws IOException {
            return delegate.getBodyContent();
        }

        @Override
        public String getMediaType() {
            return delegate.getMediaType();
        }

        @Override
        public String getSubType() {
            return delegate.getSubType();
        }

        @Override
        public long getBodyOctets() {
            return delegate.getBodyOctets();
        }

        @Override
        public long getFullContentOctets() {
            return delegate.getFullContentOctets();
        }

        @Override
        public long getHeaderOctets() {
            return delegate.getHeaderOctets();
        }

        @Override
        public Long getTextualLineCount() {
            return delegate.getTextualLineCount();
        }

        @Override
        public InputStream getHeaderContent() throws IOException {
            return delegate.getHeaderContent();
        }

        @Override
        public InputStream getFullContent() throws IOException {
            return delegate.getFullContent();
        }

        @Override
        public ThreadId getThreadId() {
            return delegate.getThreadId();
        }

        @Override
        public Properties getProperties() {
            return delegate.getProperties();
        }

        @Override
        public List<MessageAttachmentMetadata> getAttachments() {
            return delegate.getAttachments();
        }

        @Override
        public Optional<Date> getSaveDate() {
            return delegate.getSaveDate();
        }

        public BlobId getHeaderBlobId() {
            return headerBlobId;
        }

        @Override
        public MailboxMessage copy(Mailbox mailbox) throws MailboxException {
            return new CassandraMailboxMessage(delegate.copy(mailbox), headerBlobId);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CassandraMessageMetadata from(MailboxMessage mailboxMessage, BlobId headerBlobId) {
        return new CassandraMessageMetadata(
            ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxMessage.getMailboxId(), mailboxMessage.getMessageId(), mailboxMessage.getUid()))
                .modSeq(mailboxMessage.getModSeq())
                .flags(mailboxMessage.createFlags())
                .threadId(mailboxMessage.getThreadId())
                .build(),
            Optional.of(mailboxMessage.getInternalDate()),
            mailboxMessage.getSaveDate(),
            Optional.of(mailboxMessage.getHeaderOctets()),
            Optional.of(mailboxMessage.getFullContentOctets()),
            Optional.of(headerBlobId));
    }

    public static CassandraMessageMetadata from(MailboxMessage mailboxMessage) {
        Preconditions.checkArgument(mailboxMessage instanceof CassandraMailboxMessage, "Requires a CassandraMailboxMessage");

        CassandraMailboxMessage cassandraMailboxMessage = (CassandraMailboxMessage) mailboxMessage;
        return from(mailboxMessage, cassandraMailboxMessage.headerBlobId);
    }

    private final ComposedMessageIdWithMetaData composedMessageId;
    private final Optional<Date> internalDate;
    private final Optional<Date> saveDate;
    private final Optional<Long> bodyStartOctet;
    private final Optional<Long> size;
    private final Optional<BlobId> headerContent;

    public CassandraMessageMetadata(ComposedMessageIdWithMetaData composedMessageId, Optional<Date> internalDate, Optional<Date> saveDate, Optional<Long> bodyStartOctet, Optional<Long> size, Optional<BlobId> headerContent) {
        this.composedMessageId = composedMessageId;
        this.internalDate = internalDate;
        this.saveDate = saveDate;
        this.bodyStartOctet = bodyStartOctet;
        this.size = size;
        this.headerContent = headerContent;
    }

    public boolean isComplete() {
        return internalDate.isPresent()
            && bodyStartOctet.isPresent()
            && size.isPresent()
            &&  headerContent.isPresent();
    }

    public MailboxMessage asMailboxMessage(byte[] headerContent) {
        Preconditions.checkState(isComplete());

        return new CassandraMailboxMessage(
            SimpleMailboxMessage.builder()
                .mailboxId(composedMessageId.getComposedMessageId().getMailboxId())
                .messageId(composedMessageId.getComposedMessageId().getMessageId())
                .threadId(composedMessageId.getThreadId())
                .uid(composedMessageId.getComposedMessageId().getUid())
                .modseq(composedMessageId.getModSeq())
                .flags(composedMessageId.getFlags())
                .content(new ByteContent(headerContent))
                .size(size.get())
                .bodyStartOctet(Math.toIntExact(bodyStartOctet.get()))
                .internalDate(internalDate.get())
                .saveDate(saveDate)
                .properties(new PropertyBuilder())
                .build(),
            getHeaderContent().get());
    }

    public Optional<BlobId> getHeaderContent() {
        return headerContent;
    }

    public ComposedMessageIdWithMetaData getComposedMessageId() {
        return composedMessageId;
    }

    public Optional<Date> getInternalDate() {
        return internalDate;
    }

    public Optional<Long> getBodyStartOctet() {
        return bodyStartOctet;
    }

    public Optional<Long> getSize() {
        return size;
    }

    public Optional<Date> getSaveDate() {
        return saveDate;
    }

    public CassandraMessageMetadata withMailboxId(MailboxId mailboxId) {
        return builder()
            .internalDate(internalDate)
            .saveDate(saveDate)
            .size(size)
            .bodyStartOctet(bodyStartOctet)
            .headerContent(headerContent)
            .ids(ComposedMessageIdWithMetaData.builder()
                .flags(composedMessageId.getFlags())
                .modSeq(composedMessageId.getModSeq())
                .threadId(composedMessageId.getThreadId())
                .composedMessageId(new ComposedMessageId(mailboxId,
                    composedMessageId.getComposedMessageId().getMessageId(),
                    composedMessageId.getComposedMessageId().getUid()))
                .build())
            .build();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CassandraMessageMetadata) {
            CassandraMessageMetadata other = (CassandraMessageMetadata) obj;
            return Objects.equal(this.composedMessageId, other.composedMessageId)
                && Objects.equal(this.internalDate, other.internalDate)
                && Objects.equal(this.saveDate, other.saveDate)
                && Objects.equal(this.bodyStartOctet, other.bodyStartOctet)
                && Objects.equal(this.size, other.size)
                && Objects.equal(this.headerContent, other.headerContent);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(composedMessageId, internalDate, saveDate, bodyStartOctet, size, headerContent);
    }

    @Override
    public String toString() {
        return MoreObjects
            .toStringHelper(this)
            .add("composedMessageId", composedMessageId)
            .add("internalDate", internalDate)
            .add("saveDate", saveDate)
            .add("bodyStartOctet", bodyStartOctet)
            .add("size", size)
            .add("headerContent", headerContent)
            .toString();
    }
}
