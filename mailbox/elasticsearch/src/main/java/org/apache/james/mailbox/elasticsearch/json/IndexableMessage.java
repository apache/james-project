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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Multimap;
import org.apache.james.mailbox.elasticsearch.query.DateResolutionFormater;
import org.apache.james.mailbox.store.extractor.TextExtractor;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.james.mime4j.MimeException;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class IndexableMessage {

    public static IndexableMessage from(Message<? extends MailboxId> message, TextExtractor textExtractor, ZoneId zoneId) {
        Preconditions.checkNotNull(message.getMailboxId());
        IndexableMessage indexableMessage = new IndexableMessage();
        try {
            MimePart parsingResult = new MimePartParser(message, textExtractor).parse();
            indexableMessage.bodyText = parsingResult.locateFirstTextualBody();
            indexableMessage.setFlattenedAttachments(parsingResult);
            indexableMessage.copyHeaderFields(parsingResult.getHeaderCollection(), getSanitizedInternalDate(message, zoneId));
        } catch (IOException | MimeException e) {
            throw Throwables.propagate(e);
        }
        indexableMessage.copyMessageFields(message, zoneId);
        return indexableMessage;
    }

    private void setFlattenedAttachments(MimePart parsingResult) {
        attachments = parsingResult.getAttachmentsStream()
            .collect(Collectors.toList());
    }

    private void copyHeaderFields(HeaderCollection headerCollection, ZonedDateTime internalDate) {
        this.headers = headerCollection.getHeaders();
        this.subjects = headerCollection.getSubjectSet();
        this.from = headerCollection.getFromAddressSet();
        this.to = headerCollection.getToAddressSet();
        this.cc = headerCollection.getCcAddressSet();
        this.bcc = headerCollection.getBccAddressSet();
        this.sentDate = DateResolutionFormater.DATE_TIME_FOMATTER.format(headerCollection.getSentDate().orElse(internalDate));
    }

    private void copyMessageFields(Message<? extends MailboxId> message, ZoneId zoneId) {
        this.id = message.getUid();
        this.mailboxId = message.getMailboxId().serialize();
        this.modSeq = message.getModSeq();
        this.size = message.getFullContentOctets();
        this.date = DateResolutionFormater.DATE_TIME_FOMATTER.format(getSanitizedInternalDate(message, zoneId));
        this.mediaType = message.getMediaType();
        this.subType = message.getSubType();
        this.isAnswered = message.isAnswered();
        this.isDeleted = message.isDeleted();
        this.isDraft = message.isDraft();
        this.isFlagged = message.isFlagged();
        this.isRecent = message.isRecent();
        this.isUnRead = ! message.isSeen();
        this.userFlags = message.createFlags().getUserFlags();
        this.properties = message.getProperties();
    }

    private static ZonedDateTime getSanitizedInternalDate(Message<? extends MailboxId> message, ZoneId zoneId) {
        if (message.getInternalDate() == null) {
            return ZonedDateTime.now();
        }
        return ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(message.getInternalDate().getTime()),
            zoneId);
    }

    private Long id;
    private String mailboxId;
    private long modSeq;
    private long size;
    private String date;
    private String mediaType;
    private String subType;
    private boolean isUnRead;
    private boolean isRecent;
    private boolean isFlagged;
    private boolean isDeleted;
    private boolean isDraft;
    private boolean isAnswered;
    private String[] userFlags;
    private Multimap<String, String> headers;
    private Set<EMailer> from;
    private Set<EMailer> to;
    private Set<EMailer> cc;
    private Set<EMailer> bcc;
    private Set<String> subjects;
    private String sentDate;
    private List<Property> properties;
    private List<MimePart> attachments;
    private Optional<String> bodyText;

    @JsonProperty(JsonMessageConstants.ID)
    public Long getId() {
        return id;
    }

    @JsonProperty(JsonMessageConstants.MAILBOX_ID)
    public String getMailboxId() {
        return mailboxId;
    }

    @JsonProperty(JsonMessageConstants.MODSEQ)
    public long getModSeq() {
        return modSeq;
    }

    @JsonProperty(JsonMessageConstants.SIZE)
    public long getSize() {
        return size;
    }

    @JsonProperty(JsonMessageConstants.DATE)
    public String getDate() {
        return date;
    }

    @JsonProperty(JsonMessageConstants.MEDIA_TYPE)
    public String getMediaType() {
        return mediaType;
    }

    @JsonProperty(JsonMessageConstants.SUBTYPE)
    public String getSubType() {
        return subType;
    }

    @JsonProperty(JsonMessageConstants.IS_UNREAD)
    public boolean isUnRead() {
        return isUnRead;
    }

    @JsonProperty(JsonMessageConstants.IS_RECENT)
    public boolean isRecent() {
        return isRecent;
    }

    @JsonProperty(JsonMessageConstants.IS_FLAGGED)
    public boolean isFlagged() {
        return isFlagged;
    }

    @JsonProperty(JsonMessageConstants.IS_DELETED)
    public boolean isDeleted() {
        return isDeleted;
    }

    @JsonProperty(JsonMessageConstants.IS_DRAFT)
    public boolean isDraft() {
        return isDraft;
    }

    @JsonProperty(JsonMessageConstants.IS_ANSWERED)
    public boolean isAnswered() {
        return isAnswered;
    }

    @JsonProperty(JsonMessageConstants.USER_FLAGS)
    public String[] getUserFlags() {
        return userFlags;
    }

    @JsonProperty(JsonMessageConstants.HEADERS)
    public Multimap<String, String> getHeaders() {
        return headers;
    }

    @JsonProperty(JsonMessageConstants.SUBJECT)
    public Set<String> getSubjects() {
        return subjects;
    }

    @JsonProperty(JsonMessageConstants.FROM)
    public Set<EMailer> getFrom() {
        return from;
    }

    @JsonProperty(JsonMessageConstants.TO)
    public Set<EMailer> getTo() {
        return to;
    }

    @JsonProperty(JsonMessageConstants.CC)
    public Set<EMailer> getCc() {
        return cc;
    }

    @JsonProperty(JsonMessageConstants.BCC)
    public Set<EMailer> getBcc() {
        return bcc;
    }

    @JsonProperty(JsonMessageConstants.SENT_DATE)
    public String getSentDate() {
        return sentDate;
    }

    @JsonProperty(JsonMessageConstants.PROPERTIES)
    public List<Property> getProperties() {
        return properties;
    }

    @JsonProperty(JsonMessageConstants.ATTACHMENTS)
    public List<MimePart> getAttachments() {
        return attachments;
    }

    @JsonProperty(JsonMessageConstants.TEXT_BODY)
    public Optional<String> getBodyText() {
        return bodyText;
    }

    @JsonProperty(JsonMessageConstants.HAS_ATTACHMENT)
    public boolean getHasAttachment() {
        return attachments.size() > 0;
    }
}
