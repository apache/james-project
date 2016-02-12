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

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.james.jmap.methods.GetMessagesMethod;
import org.apache.james.jmap.methods.JmapResponseWriterImpl;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@JsonDeserialize(builder = CreationMessage.Builder.class)
public class CreationMessage {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private ImmutableList<String> mailboxIds;
        private String inReplyToMessageId;
        private boolean isUnread;
        private boolean isFlagged;
        private boolean isAnswered;
        private boolean isDraft;
        private final ImmutableMap.Builder<String, String> headers;
        private Emailer from;
        private final ImmutableList.Builder<Emailer> to;
        private final ImmutableList.Builder<Emailer> cc;
        private final ImmutableList.Builder<Emailer> bcc;
        private final ImmutableList.Builder<Emailer> replyTo;
        private String subject;
        private ZonedDateTime date;
        private String textBody;
        private String htmlBody;
        private final ImmutableList.Builder<Attachment> attachments;
        private final ImmutableMap.Builder<String, SubMessage> attachedMessages;

        private Builder() {
            to = ImmutableList.builder();
            cc = ImmutableList.builder();
            bcc = ImmutableList.builder();
            replyTo = ImmutableList.builder();
            attachments = ImmutableList.builder();
            attachedMessages = ImmutableMap.builder();
            headers = ImmutableMap.builder();
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

        public Builder headers(ImmutableMap<String, String> headers) {
            this.headers.putAll(headers);
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

        private static boolean areAttachedMessagesKeysInAttachments(ImmutableList<Attachment> attachments, ImmutableMap<String, SubMessage> attachedMessages) {
            return attachments.stream()
                    .map(Attachment::getBlobId)
                    .allMatch(attachedMessages::containsKey);
        }

        public CreationMessage build() {
            Preconditions.checkState(mailboxIds != null, "'mailboxIds' is mandatory");
            Preconditions.checkState(headers != null, "'headers' is mandatory");
            Preconditions.checkState(!Strings.isNullOrEmpty(subject), "'subject' is mandatory");
            ImmutableList<Attachment> attachments = this.attachments.build();
            ImmutableMap<String, SubMessage> attachedMessages = this.attachedMessages.build();
            Preconditions.checkState(areAttachedMessagesKeysInAttachments(attachments, attachedMessages), "'attachedMessages' keys must be in 'attachments'");

            if (date == null) {
                date = ZonedDateTime.now();
            }

            return new CreationMessage(mailboxIds, Optional.ofNullable(inReplyToMessageId), isUnread, isFlagged, isAnswered, isDraft, headers.build(), Optional.ofNullable(from),
                    to.build(), cc.build(), bcc.build(), replyTo.build(), subject, date, Optional.ofNullable(textBody), Optional.ofNullable(htmlBody), attachments, attachedMessages);
        }
    }

    private final ImmutableList<String> mailboxIds;
    private final Optional<String> inReplyToMessageId;
    private final boolean isUnread;
    private final boolean isFlagged;
    private final boolean isAnswered;
    private final boolean isDraft;
    private final ImmutableMap<String, String> headers;
    private final Optional<Emailer> from;
    private final ImmutableList<Emailer> to;
    private final ImmutableList<Emailer> cc;
    private final ImmutableList<Emailer> bcc;
    private final ImmutableList<Emailer> replyTo;
    private final String subject;
    private final ZonedDateTime date;
    private final Optional<String> textBody;
    private final Optional<String> htmlBody;
    private final ImmutableList<Attachment> attachments;
    private final ImmutableMap<String, SubMessage> attachedMessages;

    @VisibleForTesting
    CreationMessage(ImmutableList<String> mailboxIds, Optional<String> inReplyToMessageId, boolean isUnread, boolean isFlagged, boolean isAnswered, boolean isDraft, ImmutableMap<String, String> headers, Optional<Emailer> from,
                    ImmutableList<Emailer> to, ImmutableList<Emailer> cc, ImmutableList<Emailer> bcc, ImmutableList<Emailer> replyTo, String subject, ZonedDateTime date, Optional<String> textBody, Optional<String> htmlBody, ImmutableList<Attachment> attachments,
                    ImmutableMap<String, SubMessage> attachedMessages) {
        this.mailboxIds = mailboxIds;
        this.inReplyToMessageId = inReplyToMessageId;
        this.isUnread = isUnread;
        this.isFlagged = isFlagged;
        this.isAnswered = isAnswered;
        this.isDraft = isDraft;
        this.headers = headers;
        this.from = from;
        this.to = to;
        this.cc = cc;
        this.bcc = bcc;
        this.replyTo = replyTo;
        this.subject = subject;
        this.date = date;
        this.textBody = textBody;
        this.htmlBody = htmlBody;
        this.attachments = attachments;
        this.attachedMessages = attachedMessages;
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
