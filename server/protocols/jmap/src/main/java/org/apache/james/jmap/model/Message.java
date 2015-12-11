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

package org.apache.james.jmap.model;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.jmap.model.message.EMailer;
import org.apache.james.jmap.model.message.IndexableMessage;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.MailboxId;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

@JsonDeserialize(builder = Message.Builder.class)
public class Message {
    public static final String NO_SUBJECT = "(No subject)";
    public static final String MULTIVALUED_HEADERS_SEPARATOR = ", ";
    public static final String NO_BODY = "(Empty)";
    public static final ZoneId UTC_ZONE_ID = ZoneId.of("Z");

    public static Builder builder() {
        return new Builder();
    }

    public static Message fromMailboxMessage(org.apache.james.mailbox.store.mail.model.Message<? extends MailboxId> mailboxMessage) {
        IndexableMessage im = IndexableMessage.from(mailboxMessage, new DefaultTextExtractor(), UTC_ZONE_ID);
        if (im.getHasAttachment()) {
            throw new NotImplementedException();
        }
        return builder()
                .id(String.valueOf(im.getId()))
                .blobId(String.valueOf(im.getId()))
                .threadId(String.valueOf(im.getId()))
                .mailboxIds(ImmutableList.of(im.getMailboxId()))
                .inReplyToMessageId(getHeaderAsSingleValue(im, "in-reply-to"))
                .isUnread(im.isUnRead())
                .isFlagged(im.isFlagged())
                .isAnswered(im.isAnswered())
                .isDraft(im.isDraft())
                .subject(getSubject(im))
                .headers(toMap(im.getHeaders()))
                .from(firstElasticSearchEmailers(im.getFrom()))
                .to(fromElasticSearchEmailers(im.getTo()))
                .cc(fromElasticSearchEmailers(im.getCc()))
                .bcc(fromElasticSearchEmailers(im.getBcc()))
                .replyTo(fromElasticSearchEmailers(im.getReplyTo()))
                .size(im.getSize())
                .date(getInternalDate(mailboxMessage, im))
                .preview(getPreview(im))
                .textBody(im.getBodyText().map(Strings::emptyToNull).orElse(null))
                .build();
    }

    private static String getSubject(IndexableMessage im) {
        return Optional.ofNullable(
                    Strings.emptyToNull(
                        im.getSubjects()
                            .stream()
                            .collect(Collectors.joining(MULTIVALUED_HEADERS_SEPARATOR))))
                .orElse(NO_SUBJECT);
    }
    
    private static Emailer firstElasticSearchEmailers(Set<EMailer> emailers) {
        return emailers.stream()
                    .findFirst()
                    .map(Message::fromElasticSearchEmailer)
                    .orElse(null);
    }
    
    private static ImmutableList<Emailer> fromElasticSearchEmailers(Set<EMailer> emailers) {
        return emailers.stream()
                    .map(Message::fromElasticSearchEmailer)
                    .collect(org.apache.james.util.streams.Collectors.toImmutableList());
    }
    
    private static Emailer fromElasticSearchEmailer(EMailer emailer) {
        return Emailer.builder()
                    .name(emailer.getName())
                    .email(emailer.getAddress())
                    .build();
    }
    
    private static String getPreview(IndexableMessage im) {
        return Optional.ofNullable(
                Strings.emptyToNull(
                    im.getBodyText()
                        .map(Message::computePreview)
                        .orElse(NO_BODY)))
            .orElse(NO_BODY);
    }

    @VisibleForTesting static String computePreview(String body) {
        if (body.length() <= 256) {
            return body;
        }
        return body.substring(0, 253) + "...";
    }
    
    private static ImmutableMap<String, String> toMap(Multimap<String, String> multimap) {
        return multimap
                .asMap()
                .entrySet()
                .stream()
                .collect(org.apache.james.util.streams.Collectors.toImmutableMap(Map.Entry::getKey, x -> joinOnComma(x.getValue())));
    }
    
    private static String getHeaderAsSingleValue(IndexableMessage im, String header) {
        return Strings.emptyToNull(joinOnComma(im.getHeaders().get(header)));
    }
    
    private static String joinOnComma(Iterable<String> iterable) {
        return String.join(MULTIVALUED_HEADERS_SEPARATOR, iterable);
    }
    
    private static ZonedDateTime getInternalDate(org.apache.james.mailbox.store.mail.model.Message<? extends MailboxId> mailboxMessage, IndexableMessage im) {
        return ZonedDateTime.ofInstant(mailboxMessage.getInternalDate().toInstant(), UTC_ZONE_ID);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private String id;
        private String blobId;
        private String threadId;
        private ImmutableList<String> mailboxIds;
        private String inReplyToMessageId;
        private boolean isUnread;
        private boolean isFlagged;
        private boolean isAnswered;
        private boolean isDraft;
        private boolean hasAttachment;
        private ImmutableMap<String, String> headers;
        private Emailer from;
        private ImmutableList.Builder<Emailer> to;
        private ImmutableList.Builder<Emailer> cc;
        private ImmutableList.Builder<Emailer> bcc;
        private ImmutableList.Builder<Emailer> replyTo;
        private String subject;
        private ZonedDateTime date;
        private Long size;
        private String preview;
        private String textBody;
        private String htmlBody;
        private ImmutableList.Builder<Attachment> attachments;
        private ImmutableMap.Builder<String, SubMessage> attachedMessages;

        private Builder() {
            to = ImmutableList.builder();
            cc = ImmutableList.builder();
            bcc = ImmutableList.builder();
            replyTo = ImmutableList.builder();
            attachments = ImmutableList.builder();
            attachedMessages = ImmutableMap.builder();
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder blobId(String blobId) {
            this.blobId = blobId;
            return this;
        }

        public Builder threadId(String threadId) {
            this.threadId = threadId;
            return this;
        }

        public Builder mailboxIds(ImmutableList<String> mailboxIds) {
            this.mailboxIds = mailboxIds;
            return this;
        }

        public Builder inReplyToMessageId(String inReplyToMessageId) {
            this.inReplyToMessageId = inReplyToMessageId;
            return this;
        }

        public Builder isUnread(boolean isUnread) {
            this.isUnread = isUnread;
            return this;
        }

        public Builder isFlagged(boolean isFlagged) {
            this.isFlagged = isFlagged;
            return this;
        }

        public Builder isAnswered(boolean isAnswered) {
            this.isAnswered = isAnswered;
            return this;
        }

        public Builder isDraft(boolean isDraft) {
            this.isDraft = isDraft;
            return this;
        }

        public Builder hasAttachment(boolean hasAttachment) {
            this.hasAttachment = hasAttachment;
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

        public Builder date(ZonedDateTime date) {
            this.date = date;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public Builder preview(String preview) {
            this.preview = preview;
            return this;
        }

        public Builder textBody(String textBody) {
            this.textBody = textBody;
            return this;
        }

        public Builder htmlBody(String htmlBody) {
            this.htmlBody = htmlBody;
            return this;
        }

        public Builder attachments(List<Attachment> attachments) {
            this.attachments.addAll(attachments);
            return this;
        }

        public Builder attachedMessages(Map<String, SubMessage> attachedMessages) {
            this.attachedMessages.putAll(attachedMessages);
            return this;
        }

        public Message build() {
            Preconditions.checkState(!Strings.isNullOrEmpty(id), "'id' is mandatory");
            Preconditions.checkState(!Strings.isNullOrEmpty(blobId), "'blobId' is mandatory");
            Preconditions.checkState(!Strings.isNullOrEmpty(threadId), "'threadId' is mandatory");
            Preconditions.checkState(mailboxIds != null, "'mailboxIds' is mandatory");
            Preconditions.checkState(headers != null, "'headers' is mandatory");
            Preconditions.checkState(!Strings.isNullOrEmpty(subject), "'subject' is mandatory");
            Preconditions.checkState(size != null, "'size' is mandatory");
            Preconditions.checkState(date != null, "'date' is mandatory");
            Preconditions.checkState(!Strings.isNullOrEmpty(preview), "'preview' is mandatory");
            ImmutableList<Attachment> attachments = this.attachments.build();
            ImmutableMap<String, SubMessage> attachedMessages = this.attachedMessages.build();
            Preconditions.checkState(areAttachedMessagesKeysInAttachments(attachments, attachedMessages), "'attachedMessages' keys must be in 'attachements'");

            return new Message(id, blobId, threadId, mailboxIds, Optional.ofNullable(inReplyToMessageId), isUnread, isFlagged, isAnswered, isDraft, hasAttachment, headers, Optional.ofNullable(from),
                    to.build(), cc.build(), bcc.build(), replyTo.build(), subject, date, size, preview, Optional.ofNullable(textBody), Optional.ofNullable(htmlBody), attachments, attachedMessages);
        }
    }

    protected static boolean areAttachedMessagesKeysInAttachments(ImmutableList<Attachment> attachments, ImmutableMap<String, SubMessage> attachedMessages) {
        return attachments.stream()
                .map(Attachment::getBlobId)
                .allMatch(attachedMessages::containsKey);
    }

    private final String id;
    private final String blobId;
    private final String threadId;
    private final ImmutableList<String> mailboxIds;
    private final Optional<String> inReplyToMessageId;
    private final boolean isUnread;
    private final boolean isFlagged;
    private final boolean isAnswered;
    private final boolean isDraft;
    private final boolean hasAttachment;
    private final ImmutableMap<String, String> headers;
    private final Optional<Emailer> from;
    private final ImmutableList<Emailer> to;
    private final ImmutableList<Emailer> cc;
    private final ImmutableList<Emailer> bcc;
    private final ImmutableList<Emailer> replyTo;
    private final String subject;
    private final ZonedDateTime date;
    private final long size;
    private final String preview;
    private final Optional<String> textBody;
    private final Optional<String> htmlBody;
    private final ImmutableList<Attachment> attachments;
    private final ImmutableMap<String, SubMessage> attachedMessages;

    @VisibleForTesting Message(String id, String blobId, String threadId, ImmutableList<String> mailboxIds, Optional<String> inReplyToMessageId, boolean isUnread, boolean isFlagged, boolean isAnswered, boolean isDraft, boolean hasAttachment, ImmutableMap<String, String> headers, Optional<Emailer> from,
            ImmutableList<Emailer> to, ImmutableList<Emailer> cc, ImmutableList<Emailer> bcc, ImmutableList<Emailer> replyTo, String subject, ZonedDateTime date, long size, String preview, Optional<String> textBody, Optional<String> htmlBody, ImmutableList<Attachment> attachments,
            ImmutableMap<String, SubMessage> attachedMessages) {
        this.id = id;
        this.blobId = blobId;
        this.threadId = threadId;
        this.mailboxIds = mailboxIds;
        this.inReplyToMessageId = inReplyToMessageId;
        this.isUnread = isUnread;
        this.isFlagged = isFlagged;
        this.isAnswered = isAnswered;
        this.isDraft = isDraft;
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
    }

    public String getId() {
        return id;
    }

    public String getBlobId() {
        return blobId;
    }

    public String getThreadId() {
        return threadId;
    }

    public ImmutableList<String> getMailboxIds() {
        return mailboxIds;
    }

    public Optional<String> getInReplyToMessageId() {
        return inReplyToMessageId;
    }

    public boolean isIsUnread() {
        return isUnread;
    }

    public boolean isIsFlagged() {
        return isFlagged;
    }

    public boolean isIsAnswered() {
        return isAnswered;
    }

    public boolean isIsDraft() {
        return isDraft;
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

    public ZonedDateTime getDate() {
        return date;
    }

    public long getSize() {
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

    public ImmutableMap<String, SubMessage> getAttachedMessages() {
        return attachedMessages;
    }
}
