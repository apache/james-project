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

package org.apache.james.mailbox.elasticsearch.json;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.james.mailbox.MailboxSession.User;
import org.apache.james.mailbox.elasticsearch.IndexAttachments;
import org.apache.james.mailbox.elasticsearch.query.DateResolutionFormater;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleProperty;
import org.apache.james.mailbox.store.search.SearchUtil;
import org.apache.james.mime4j.MimeException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;

public class IndexableMessage {

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
        private MailboxMessage message;
        private TextExtractor textExtractor;
        private List<User> users;

        private ZoneId zoneId;

        private Builder() {
        }

        public IndexableMessage build() {
            Preconditions.checkNotNull(message.getMailboxId());
            Preconditions.checkNotNull(users);
            Preconditions.checkNotNull(textExtractor);
            Preconditions.checkNotNull(indexAttachments);
            Preconditions.checkNotNull(zoneId);
            Preconditions.checkState(!users.isEmpty());

            try {
                return instanciateIndexedMessage();
            } catch (IOException | MimeException e) {
                throw Throwables.propagate(e);
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

        public Builder message(MailboxMessage message) {
            this.message = message;
            return this;
        }

        public Builder users(List<User> users) {
            this.users = users;
            return this;
        }

        public Builder zoneId(ZoneId zoneId) {
            this.zoneId = zoneId;
            return this;
        }

        private boolean computeHasAttachment(MailboxMessage message) {
            return message.getProperties()
                    .stream()
                    .anyMatch(property -> property.equals(HAS_ATTACHMENT_PROPERTY));
        }

        private IndexableMessage instanciateIndexedMessage() throws IOException, MimeException {
            String messageId = SearchUtil.getSerializedMessageIdIfSupportedByUnderlyingStorageOrNull(message);
            MimePart parsingResult = new MimePartParser(message, textExtractor).parse();

            List<String> stringifiedUsers = users.stream()
                    .map(User::getUserName)
                    .collect(Guavate.toImmutableList());

            Optional<String> bodyText = parsingResult.locateFirstTextBody();
            Optional<String> bodyHtml = parsingResult.locateFirstHtmlBody();

            boolean hasAttachment = computeHasAttachment(message);
            List<MimePart> attachments = setFlattenedAttachments(parsingResult, indexAttachments);

            HeaderCollection headerCollection = parsingResult.getHeaderCollection();
            ZonedDateTime internalDate = getSanitizedInternalDate(message, zoneId);

            Multimap<String, String> headers = headerCollection.getHeaders();
            Subjects subjects = Subjects.from(headerCollection.getSubjectSet());
            EMailers from = EMailers.from(headerCollection.getFromAddressSet());
            EMailers to = EMailers.from(headerCollection.getToAddressSet());
            EMailers replyTo = EMailers.from(headerCollection.getReplyToAddressSet());
            EMailers cc = EMailers.from(headerCollection.getCcAddressSet());
            EMailers bcc = EMailers.from(headerCollection.getBccAddressSet());
            String sentDate = DateResolutionFormater.DATE_TIME_FOMATTER.format(headerCollection.getSentDate().orElse(internalDate));

            String text = Stream.of(from.serialize(),
                        to.serialize(),
                        cc.serialize(),
                        bcc.serialize(),
                        subjects.serialize(),
                        bodyText.orElse(null),
                        bodyHtml.orElse(null))
                    .filter(str -> !Strings.isNullOrEmpty(str))
                    .collect(Collectors.joining(" "));

            long uid = message.getUid().asLong();
            String mailboxId = message.getMailboxId().serialize();
            long modSeq = message.getModSeq();
            long size = message.getFullContentOctets();
            String date = DateResolutionFormater.DATE_TIME_FOMATTER.format(getSanitizedInternalDate(message, zoneId));
            String mediaType = message.getMediaType();
            String subType = message.getSubType();
            boolean isAnswered = message.isAnswered();
            boolean isDeleted = message.isDeleted();
            boolean isDraft = message.isDraft();
            boolean isFlagged = message.isFlagged();
            boolean isRecent = message.isRecent();
            boolean isUnRead = !message.isSeen();
            String[] userFlags = message.createFlags().getUserFlags();
            List<Property> properties = message.getProperties();

            return new IndexableMessage(
                    attachments,
                    bcc,
                    bodyHtml,
                    bodyText,
                    cc,
                    date,
                    from,
                    hasAttachment,
                    headers,
                    isAnswered,
                    isDeleted,
                    isDraft,
                    isFlagged,
                    isRecent,
                    isUnRead,
                    mailboxId,
                    mediaType,
                    messageId,
                    modSeq,
                    properties,
                    replyTo,
                    sentDate,
                    size,
                    subjects,
                    subType,
                    text,
                    to,
                    uid,
                    userFlags,
                    stringifiedUsers);
        }

        private List<MimePart> setFlattenedAttachments(MimePart parsingResult, IndexAttachments indexAttachments) {
            if (IndexAttachments.YES.equals(indexAttachments)) {
                return parsingResult.getAttachmentsStream()
                    .collect(Guavate.toImmutableList());
            } else {
                return ImmutableList.of();
            }
        }
    }

    public static final SimpleProperty HAS_ATTACHMENT_PROPERTY = new SimpleProperty(PropertyBuilder.JAMES_INTERNALS, PropertyBuilder.HAS_ATTACHMENT, "true");

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
    private final Multimap<String, String> headers;
    private final boolean isAnswered;
    private final boolean isDeleted;
    private final boolean isDraft;
    private final boolean isFlagged;
    private final boolean isRecent;
    private final boolean isUnRead;
    private final String mailboxId;
    private final String mediaType;
    private final String messageId;
    private final long modSeq;
    private final List<Property> properties;
    private final EMailers replyTo;
    private final String sentDate;
    private final long size;
    private final Subjects subjects;
    private final String subType;
    private final String text;
    private final EMailers to;
    private final long uid;
    private final String[] userFlags;
    private final List<String> users;

    private IndexableMessage(
            List<MimePart> attachments,
            EMailers bcc,
            Optional<String> bodyHtml,
            Optional<String> bodyText,
            EMailers cc,
            String date,
            EMailers from,
            boolean hasAttachment,
            Multimap<String, String> headers,
            boolean isAnswered,
            boolean isDeleted,
            boolean isDraft,
            boolean isFlagged,
            boolean isRecent,
            boolean isUnRead,
            String mailboxId,
            String mediaType,
            String messageId,
            long modSeq,
            List<Property> properties,
            EMailers replyTo,
            String sentDate,
            long size,
            Subjects subjects,
            String subType,
            String text,
            EMailers to,
            long uid,
            String[] userFlags,
            List<String> users) {
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
        this.modSeq = modSeq;
        this.properties = properties;
        this.replyTo = replyTo;
        this.sentDate = sentDate;
        this.size = size;
        this.subjects = subjects;
        this.subType = subType;
        this.text = text;
        this.to = to;
        this.uid = uid;
        this.userFlags = userFlags;
        this.users = users;
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
    public Multimap<String, String> getHeaders() {
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

    @JsonProperty(JsonMessageConstants.MODSEQ)
    public long getModSeq() {
        return modSeq;
    }

    @JsonProperty(JsonMessageConstants.PROPERTIES)
    public List<Property> getProperties() {
        return properties;
    }

    @JsonProperty(JsonMessageConstants.REPLY_TO)
    public EMailers getReplyTo() {
        return replyTo;
    }

    @JsonProperty(JsonMessageConstants.SENT_DATE)
    public String getSentDate() {
        return sentDate;
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

    @JsonProperty(JsonMessageConstants.TEXT)
    public String getText() {
        return text;
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

    @JsonProperty(JsonMessageConstants.USERS)
    public List<String> getUsers() {
        return users;
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
}
