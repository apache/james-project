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

package org.apache.james.mailbox.opensearch.json;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.opensearch.IndexAttachments;
import org.apache.james.mailbox.opensearch.IndexHeaders;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.SearchUtil;
import org.apache.james.mime4j.MimeException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class IndexableMessage {

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    public static class Builder {
        private static ZonedDateTime getSanitizedInternalDate(MailboxMessage message, ZoneId zoneId) {
            if (message.getInternalDate() == null) {
                return ZonedDateTime.now();
            }
            return ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(message.getInternalDate().getTime()),
                    zoneId);
        }

        private IndexAttachments indexAttachments;
        private IndexHeaders indexHeaders;
        private MailboxMessage message;
        private TextExtractor textExtractor;
        private ZoneId zoneId;

        private Builder() {
        }

        public Mono<IndexableMessage> build() {
            Preconditions.checkNotNull(message.getMailboxId());
            Preconditions.checkNotNull(textExtractor);
            Preconditions.checkNotNull(indexAttachments);
            Preconditions.checkNotNull(indexHeaders);
            Preconditions.checkNotNull(zoneId);

            try {
                return instantiateIndexedMessage();
            } catch (IOException | MimeException e) {
                throw new RuntimeException(e);
            }
        }

        public Builder extractor(TextExtractor textExtractor) {
            this.textExtractor = textExtractor;
            return this;
        }

        public Builder indexAttachments(IndexAttachments indexAttachments) {
            this.indexAttachments = indexAttachments;
            return this;
        }

        public Builder indexHeaders(IndexHeaders indexHeaders) {
            this.indexHeaders = indexHeaders;
            return this;
        }

        public Builder message(MailboxMessage message) {
            this.message = message;
            return this;
        }

        public Builder zoneId(ZoneId zoneId) {
            this.zoneId = zoneId;
            return this;
        }

        private Mono<IndexableMessage> instantiateIndexedMessage() throws IOException, MimeException {
            String messageId = SearchUtil.getSerializedMessageIdIfSupportedByUnderlyingStorageOrNull(message);
            String threadId = SearchUtil.getSerializedThreadIdIfSupportedByUnderlyingStorageOrNull(message);

            return new MimePartParser(message, textExtractor).parse()
                .asMimePart(textExtractor)
                .map(parsingResult -> {

                    Optional<String> bodyText = parsingResult.locateFirstTextBody();
                    Optional<String> bodyHtml = parsingResult.locateFirstHtmlBody();

                    boolean hasAttachment = MessageAttachmentMetadata.hasNonInlinedAttachment(message.getAttachments());
                    List<MimePart> attachments = setFlattenedAttachments(parsingResult, indexAttachments);

                    HeaderCollection headerCollection = parsingResult.getHeaderCollection();
                    ZonedDateTime internalDate = getSanitizedInternalDate(message, zoneId);

                    List<HeaderCollection.Header> headers = headerCollection.getHeaders();
                    Subjects subjects = Subjects.from(headerCollection.getSubjectSet());
                    EMailers from = EMailers.from(headerCollection.getFromAddressSet());
                    EMailers to = EMailers.from(headerCollection.getToAddressSet());
                    EMailers cc = EMailers.from(headerCollection.getCcAddressSet());
                    EMailers bcc = EMailers.from(headerCollection.getBccAddressSet());
                    String sentDate = DATE_TIME_FORMATTER.format(headerCollection.getSentDate().orElse(internalDate));
                    Optional<String> saveDate = message.getSaveDate()
                        .map(date -> DATE_TIME_FORMATTER.format(ZonedDateTime.ofInstant(date.toInstant(), zoneId)));
                    Optional<String> mimeMessageID = headerCollection.getMessageID();

                    long uid = message.getUid().asLong();
                    String mailboxId = message.getMailboxId().serialize();
                    ModSeq modSeq = message.getModSeq();
                    long size = message.getFullContentOctets();
                    String date = DATE_TIME_FORMATTER.format(getSanitizedInternalDate(message, zoneId));
                    String mediaType = message.getMediaType();
                    String subType = message.getSubType();
                    boolean isAnswered = message.isAnswered();
                    boolean isDeleted = message.isDeleted();
                    boolean isDraft = message.isDraft();
                    boolean isFlagged = message.isFlagged();
                    boolean isRecent = message.isRecent();
                    boolean isUnRead = !message.isSeen();
                    String[] userFlags = message.createFlags().getUserFlags();

                    return new IndexableMessage(
                        attachments,
                        bcc,
                        bodyHtml,
                        bodyText,
                        cc,
                        date,
                        from,
                        hasAttachment,
                        filterHeadersIfNeeded(headers),
                        isAnswered,
                        isDeleted,
                        isDraft,
                        isFlagged,
                        isRecent,
                        isUnRead,
                        mailboxId,
                        mediaType,
                        messageId,
                        threadId,
                        modSeq,
                        sentDate,
                        saveDate,
                        size,
                        subjects,
                        subType,
                        to,
                        uid,
                        userFlags,
                        mimeMessageID);
                });
        }

        private List<HeaderCollection.Header> filterHeadersIfNeeded(List<HeaderCollection.Header> headers) {
            if (IndexHeaders.YES.equals(indexHeaders)) {
                return headers;
            } else {
                return ImmutableList.of();
            }
        }

        private List<MimePart> setFlattenedAttachments(MimePart parsingResult, IndexAttachments indexAttachments) {
            if (IndexAttachments.YES.equals(indexAttachments)) {
                return parsingResult.getAttachmentsStream()
                    .collect(ImmutableList.toImmutableList());
            } else {
                return ImmutableList.of();
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final List<MimePart> attachments;
    private final EMailers bcc;
    private final Optional<String> bodyHtml;
    private final Optional<String> bodyText;
    private final EMailers cc;
    private final String date;
    private final EMailers from;
    private final boolean hasAttachment;
    private final List<HeaderCollection.Header> headers;
    private final boolean isAnswered;
    private final boolean isDeleted;
    private final boolean isDraft;
    private final boolean isFlagged;
    private final boolean isRecent;
    private final boolean isUnRead;
    private final String mailboxId;
    private final String mediaType;
    private final String messageId;
    private final String threadId;
    private final long modSeq;
    private final String sentDate;
    private final Optional<String> saveDate;
    private final long size;
    private final Subjects subjects;
    private final String subType;
    private final EMailers to;
    private final long uid;
    private final String[] userFlags;
    private final Optional<String> mimeMessageID;

    private IndexableMessage(List<MimePart> attachments,
                             EMailers bcc,
                             Optional<String> bodyHtml,
                             Optional<String> bodyText,
                             EMailers cc,
                             String date,
                             EMailers from,
                             boolean hasAttachment,
                             List<HeaderCollection.Header> headers,
                             boolean isAnswered,
                             boolean isDeleted,
                             boolean isDraft,
                             boolean isFlagged,
                             boolean isRecent,
                             boolean isUnRead,
                             String mailboxId,
                             String mediaType, String messageId,
                             String threadId,
                             ModSeq modSeq,
                             String sentDate,
                             Optional<String> saveDate, long size,
                             Subjects subjects,
                             String subType,
                             EMailers to,
                             long uid,
                             String[] userFlags,
                             Optional<String> mimeMessageID) {
        this.attachments = attachments;
        this.bcc = bcc;
        this.bodyHtml = bodyHtml;
        this.bodyText = bodyText;
        this.cc = cc;
        this.date = date;
        this.from = from;
        this.hasAttachment = hasAttachment;
        this.headers = headers;
        this.isAnswered = isAnswered;
        this.isDeleted = isDeleted;
        this.isDraft = isDraft;
        this.isFlagged = isFlagged;
        this.isRecent = isRecent;
        this.isUnRead = isUnRead;
        this.mailboxId = mailboxId;
        this.mediaType = mediaType;
        this.messageId = messageId;
        this.threadId = threadId;
        this.modSeq = modSeq.asLong();
        this.sentDate = sentDate;
        this.saveDate = saveDate;
        this.size = size;
        this.subjects = subjects;
        this.subType = subType;
        this.to = to;
        this.uid = uid;
        this.userFlags = userFlags;
        this.mimeMessageID = mimeMessageID;
    }

    @JsonProperty(JsonMessageConstants.ATTACHMENTS)
    public List<MimePart> getAttachments() {
        return attachments;
    }

    @JsonProperty(JsonMessageConstants.BCC)
    public EMailers getBcc() {
        return bcc;
    }

    @JsonProperty(JsonMessageConstants.HTML_BODY)
    public Optional<String> getBodyHtml() {
        return bodyHtml;
    }

    @JsonProperty(JsonMessageConstants.TEXT_BODY)
    public Optional<String> getBodyText() {
        return bodyText;
    }

    @JsonProperty(JsonMessageConstants.CC)
    public EMailers getCc() {
        return cc;
    }

    @JsonProperty(JsonMessageConstants.DATE)
    public String getDate() {
        return date;
    }

    @JsonProperty(JsonMessageConstants.FROM)
    public EMailers getFrom() {
        return from;
    }

    @JsonProperty(JsonMessageConstants.HAS_ATTACHMENT)
    public boolean getHasAttachment() {
        return hasAttachment;
    }

    @JsonProperty(JsonMessageConstants.HEADERS)
    public List<HeaderCollection.Header> getHeaders() {
        return headers;
    }

    @JsonProperty(JsonMessageConstants.MAILBOX_ID)
    public String getMailboxId() {
        return mailboxId;
    }

    @JsonProperty(JsonMessageConstants.MEDIA_TYPE)
    public String getMediaType() {
        return mediaType;
    }

    @JsonProperty(JsonMessageConstants.MESSAGE_ID)
    public String getMessageId() {
        return messageId;
    }

    @JsonProperty(JsonMessageConstants.THREAD_ID)
    public String getThreadId() {
        return threadId;
    }

    @JsonProperty(JsonMessageConstants.MODSEQ)
    public long getModSeq() {
        return modSeq;
    }

    @JsonProperty(JsonMessageConstants.SENT_DATE)
    public String getSentDate() {
        return sentDate;
    }

    @JsonProperty(JsonMessageConstants.SAVE_DATE)
    public Optional<String> getSaveDate() {
        return saveDate;
    }

    @JsonProperty(JsonMessageConstants.SIZE)
    public long getSize() {
        return size;
    }

    @JsonProperty(JsonMessageConstants.SUBJECT)
    public Subjects getSubjects() {
        return subjects;
    }

    @JsonProperty(JsonMessageConstants.SUBTYPE)
    public String getSubType() {
        return subType;
    }

    @JsonProperty(JsonMessageConstants.TO)
    public EMailers getTo() {
        return to;
    }

    @JsonProperty(JsonMessageConstants.UID)
    public Long getUid() {
        return uid;
    }

    @JsonProperty(JsonMessageConstants.USER_FLAGS)
    public String[] getUserFlags() {
        return userFlags;
    }

    @JsonProperty(JsonMessageConstants.IS_ANSWERED)
    public boolean isAnswered() {
        return isAnswered;
    }

    @JsonProperty(JsonMessageConstants.IS_DELETED)
    public boolean isDeleted() {
        return isDeleted;
    }

    @JsonProperty(JsonMessageConstants.IS_DRAFT)
    public boolean isDraft() {
        return isDraft;
    }

    @JsonProperty(JsonMessageConstants.IS_FLAGGED)
    public boolean isFlagged() {
        return isFlagged;
    }

    @JsonProperty(JsonMessageConstants.IS_RECENT)
    public boolean isRecent() {
        return isRecent;
    }

    @JsonProperty(JsonMessageConstants.IS_UNREAD)
    public boolean isUnRead() {
        return isUnRead;
    }

    @JsonProperty(JsonMessageConstants.MIME_MESSAGE_ID)
    public Optional<String> getMimeMessageID() {
        return mimeMessageID;
    }
}
