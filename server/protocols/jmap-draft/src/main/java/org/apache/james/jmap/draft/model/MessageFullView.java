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

package org.apache.james.jmap.draft.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.james.jmap.draft.methods.GetMessagesMethod;
import org.apache.james.jmap.draft.methods.JmapResponseWriterImpl;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@JsonDeserialize(builder = MessageFullView.Builder.class)
@JsonFilter(JmapResponseWriterImpl.PROPERTIES_FILTER)
public class MessageFullView {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private MessageId id;
        private BlobId blobId;
        private String threadId;
        private ImmutableList<MailboxId> mailboxIds;
        private String inReplyToMessageId;
        private ImmutableMap<String, String> headers;
        private Emailer from;
        private final ImmutableList.Builder<Emailer> to;
        private final ImmutableList.Builder<Emailer> cc;
        private final ImmutableList.Builder<Emailer> bcc;
        private final ImmutableList.Builder<Emailer> replyTo;
        private String subject;
        private Instant date;
        private Number size;
        private String preview;
        private Optional<String> textBody = Optional.empty();
        private Optional<String> htmlBody = Optional.empty();
        private final ImmutableList.Builder<Attachment> attachments;
        private final ImmutableMap.Builder<BlobId, SubMessage> attachedMessages;
        private Optional<Keywords> keywords = Optional.empty();

        private Builder() {
            to = ImmutableList.builder();
            cc = ImmutableList.builder();
            bcc = ImmutableList.builder();
            replyTo = ImmutableList.builder();
            attachments = ImmutableList.builder();
            attachedMessages = ImmutableMap.builder();
        }

        public Builder id(MessageId id) {
            this.id = id;
            return this;
        }

        public Builder blobId(BlobId blobId) {
            this.blobId = blobId;
            return this;
        }

        public Builder threadId(String threadId) {
            this.threadId = threadId;
            return this;
        }

        @JsonIgnore
        public Builder mailboxId(MailboxId mailboxId) {
            return this.fluentMailboxIds(mailboxId);
        }

        @JsonIgnore
        public Builder fluentMailboxIds(MailboxId... mailboxIds) {
            return this.mailboxIds(Arrays.asList((mailboxIds)));
        }

        public Builder mailboxIds(Collection<MailboxId> mailboxIds) {
            this.mailboxIds = ImmutableList.copyOf(mailboxIds);
            return this;
        }

        public Builder inReplyToMessageId(String inReplyToMessageId) {
            this.inReplyToMessageId = inReplyToMessageId;
            return this;
        }

        public Builder keywords(Keywords keywords) {
            this.keywords = Optional.ofNullable(keywords);
            return this;
        }

        public Builder headers(ImmutableMap<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder from(Emailer from) {
            this.from = from;
            return this;
        }

        public Builder to(List<Emailer> to) {
            this.to.addAll(to);
            return this;
        }

        public Builder cc(List<Emailer> cc) {
            this.cc.addAll(cc);
            return this;
        }

        public Builder bcc(List<Emailer> bcc) {
            this.bcc.addAll(bcc);
            return this;
        }

        public Builder replyTo(List<Emailer> replyTo) {
            this.replyTo.addAll(replyTo);
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder date(Instant date) {
            this.date = date;
            return this;
        }

        public Builder size(long size) {
            this.size = Number.BOUND_SANITIZING_FACTORY.from(size);
            return this;
        }

        public Builder preview(String preview) {
            this.preview = preview;
            return this;
        }

        public Builder textBody(Optional<String> textBody) {
            this.textBody = textBody;
            return this;
        }

        public Builder htmlBody(Optional<String> htmlBody) {
            this.htmlBody = htmlBody;
            return this;
        }

        public Builder attachments(List<Attachment> attachments) {
            this.attachments.addAll(attachments);
            return this;
        }

        public Builder attachedMessages(Map<BlobId, SubMessage> attachedMessages) {
            this.attachedMessages.putAll(attachedMessages);
            return this;
        }

        public MessageFullView build() {
            Preconditions.checkState(id != null, "'id' is mandatory");
            Preconditions.checkState(blobId != null, "'blobId' is mandatory");
            Preconditions.checkState(!Strings.isNullOrEmpty(threadId), "'threadId' is mandatory");
            Preconditions.checkState(mailboxIds != null, "'mailboxIds' is mandatory");
            Preconditions.checkState(headers != null, "'headers' is mandatory");
            Preconditions.checkState(size != null, "'size' is mandatory");
            Preconditions.checkState(date != null, "'date' is mandatory");
            Preconditions.checkState(!Strings.isNullOrEmpty(preview), "'preview' is mandatory");
            ImmutableList<Attachment> attachments = this.attachments.build();
            ImmutableMap<BlobId, SubMessage> attachedMessages = this.attachedMessages.build();
            Preconditions.checkState(areAttachedMessagesKeysInAttachments(attachments, attachedMessages), "'attachedMessages' keys must be in 'attachements'");
            boolean hasAttachment = hasAttachment(attachments);

            return new MessageFullView(id, blobId, threadId, mailboxIds, Optional.ofNullable(inReplyToMessageId),
                hasAttachment, headers, Optional.ofNullable(from),
                to.build(), cc.build(), bcc.build(), replyTo.build(), subject, date, size, preview, textBody, htmlBody, attachments, attachedMessages,
                keywords.orElse(Keywords.DEFAULT_VALUE));
        }
    }

    protected static boolean areAttachedMessagesKeysInAttachments(ImmutableList<Attachment> attachments, ImmutableMap<BlobId, SubMessage> attachedMessages) {
        return attachedMessages.isEmpty() || attachedMessages.keySet().stream()
                .anyMatch(inAttachments(attachments));
    }

    private static Predicate<BlobId> inAttachments(ImmutableList<Attachment> attachments) {
        return (key) -> attachments.stream()
            .map(Attachment::getBlobId)
            .anyMatch(blobId -> blobId.equals(key));
    }

    private static boolean hasAttachment(List<Attachment> attachments) {
        return attachments.stream()
                .anyMatch(attachment -> !attachment.isInlinedWithCid());
    }

    private final MessageId id;
    private final BlobId blobId;
    private final String threadId;
    private final ImmutableList<MailboxId> mailboxIds;
    private final Optional<String> inReplyToMessageId;
    private final boolean hasAttachment;
    @JsonFilter(GetMessagesMethod.HEADERS_FILTER)
    private final ImmutableMap<String, String> headers;
    private final Optional<Emailer> from;
    private final ImmutableList<Emailer> to;
    private final ImmutableList<Emailer> cc;
    private final ImmutableList<Emailer> bcc;
    private final ImmutableList<Emailer> replyTo;
    private final String subject;
    private final Instant date;
    private final Number size;
    private final String preview;
    private final Optional<String> textBody;
    private final Optional<String> htmlBody;
    private final ImmutableList<Attachment> attachments;
    private final ImmutableMap<BlobId, SubMessage> attachedMessages;
    private final Keywords keywords;

    @VisibleForTesting
    MessageFullView(MessageId id,
                    BlobId blobId,
                    String threadId,
                    ImmutableList<MailboxId> mailboxIds,
                    Optional<String> inReplyToMessageId,
                    boolean hasAttachment,
                    ImmutableMap<String, String> headers,
                    Optional<Emailer> from,
                    ImmutableList<Emailer> to,
                    ImmutableList<Emailer> cc,
                    ImmutableList<Emailer> bcc,
                    ImmutableList<Emailer> replyTo,
                    String subject,
                    Instant date,
                    Number size,
                    String preview,
                    Optional<String> textBody,
                    Optional<String> htmlBody,
                    ImmutableList<Attachment> attachments,
                    ImmutableMap<BlobId, SubMessage> attachedMessages,
                    Keywords keywords) {
        this.id = id;
        this.blobId = blobId;
        this.threadId = threadId;
        this.mailboxIds = mailboxIds;
        this.inReplyToMessageId = inReplyToMessageId;
        this.hasAttachment = hasAttachment;
        this.headers = headers;
        this.from = from;
        this.to = to;
        this.cc = cc;
        this.bcc = bcc;
        this.replyTo = replyTo;
        this.subject = subject;
        this.date = date;
        this.size = size;
        this.preview = preview;
        this.textBody = textBody;
        this.htmlBody = htmlBody;
        this.attachments = attachments;
        this.attachedMessages = attachedMessages;
        this.keywords = keywords;
    }

    public MessageId getId() {
        return id;
    }

    public BlobId getBlobId() {
        return blobId;
    }

    public String getThreadId() {
        return threadId;
    }

    public ImmutableList<MailboxId> getMailboxIds() {
        return mailboxIds;
    }

    public Optional<String> getInReplyToMessageId() {
        return inReplyToMessageId;
    }

    public boolean isIsUnread() {
        return !keywords.contains(Keyword.SEEN);
    }

    public boolean isIsFlagged() {
        return keywords.contains(Keyword.FLAGGED);
    }

    public boolean isIsAnswered() {
        return keywords.contains(Keyword.ANSWERED);
    }

    public boolean isIsDraft() {
        return keywords.contains(Keyword.DRAFT);
    }

    public boolean isIsForwarded() {
        return keywords.contains(Keyword.FORWARDED);
    }

    public boolean isHasAttachment() {
        return hasAttachment;
    }

    public ImmutableMap<String, String> getHeaders() {
        return headers;
    }

    public Optional<Emailer> getFrom() {
        return from;
    }

    public ImmutableList<Emailer> getTo() {
        return to;
    }

    public ImmutableList<Emailer> getCc() {
        return cc;
    }

    public ImmutableList<Emailer> getBcc() {
        return bcc;
    }

    public ImmutableList<Emailer> getReplyTo() {
        return replyTo;
    }

    public String getSubject() {
        return subject;
    }

    public Instant getDate() {
        return date;
    }

    public Number getSize() {
        return size;
    }

    public String getPreview() {
        return preview;
    }

    public Optional<String> getTextBody() {
        return textBody;
    }

    public Optional<String> getHtmlBody() {
        return htmlBody;
    }

    public ImmutableList<Attachment> getAttachments() {
        return attachments;
    }

    public ImmutableMap<BlobId, SubMessage> getAttachedMessages() {
        return attachedMessages;
    }

    public ImmutableMap<String, Boolean> getKeywords() {
        return keywords.asMap();
    }
}
