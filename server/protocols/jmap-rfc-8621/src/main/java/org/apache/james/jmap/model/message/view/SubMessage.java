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

package org.apache.james.jmap.model.message.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.james.jmap.model.Attachment;
import org.apache.james.jmap.model.BlobId;
import org.apache.james.jmap.model.Emailer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@JsonDeserialize(builder = SubMessage.Builder.class)
public class SubMessage {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private ImmutableMap<String, String> headers;
        private Emailer from;
        private final ImmutableList.Builder<Emailer> to;
        private final ImmutableList.Builder<Emailer> cc;
        private final ImmutableList.Builder<Emailer> bcc;
        private final ImmutableList.Builder<Emailer> replyTo;
        private String subject;
        private Instant date;
        private Optional<String> textBody = Optional.empty();
        private Optional<String> htmlBody = Optional.empty();
        private final ImmutableList.Builder<Attachment> attachments;
        private final ImmutableMap.Builder<BlobId, SubMessage> attachedMessages;

        private Builder() {
            to = ImmutableList.builder();
            cc = ImmutableList.builder();
            bcc = ImmutableList.builder();
            replyTo = ImmutableList.builder();
            attachments = ImmutableList.builder();
            attachedMessages = ImmutableMap.builder();
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

        public SubMessage build() {
            Preconditions.checkState(headers != null, "'headers' is mandatory");
            Preconditions.checkState(!Strings.isNullOrEmpty(subject), "'subject' is mandatory");
            Preconditions.checkState(date != null, "'date' is mandatory");
            ImmutableList<Attachment> attachments = this.attachments.build();
            ImmutableMap<BlobId, SubMessage> attachedMessages = this.attachedMessages.build();
            Preconditions.checkState(MessageFullView.areAttachedMessagesKeysInAttachments(attachments, attachedMessages), "'attachedMessages' keys must be in 'attachements'");
            return new SubMessage(headers, Optional.ofNullable(from), to.build(), cc.build(), bcc.build(),
                    replyTo.build(), subject, date, textBody, htmlBody,
                    attachments, attachedMessages
                    );
        }
    }

    private final ImmutableMap<String, String> headers;
    private final Optional<Emailer> from;
    private final ImmutableList<Emailer> to;
    private final ImmutableList<Emailer> cc;
    private final ImmutableList<Emailer> bcc;
    private final ImmutableList<Emailer> replyTo;
    private final String subject;
    private final Instant date;
    private final Optional<String> textBody;
    private final Optional<String> htmlBody;
    private final ImmutableList<Attachment> attachments;
    private final ImmutableMap<BlobId, SubMessage> attachedMessages;

    @VisibleForTesting SubMessage(ImmutableMap<String, String> headers,
                                  Optional<Emailer> from,
                                  ImmutableList<Emailer> to,
                                  ImmutableList<Emailer> cc,
                                  ImmutableList<Emailer> bcc,
                                  ImmutableList<Emailer> replyTo,
                                  String subject,
                                  Instant date,
                                  Optional<String> textBody,
                                  Optional<String> htmlBody,
                                  ImmutableList<Attachment> attachments,
                                  ImmutableMap<BlobId, SubMessage> attachedMessages) {
        super();
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


}
