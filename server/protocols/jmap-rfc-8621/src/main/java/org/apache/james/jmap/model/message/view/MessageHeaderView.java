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
import java.util.Optional;

import org.apache.james.jmap.model.BlobId;
import org.apache.james.jmap.model.Emailer;
import org.apache.james.jmap.model.Keywords;
import org.apache.james.jmap.model.Number;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class MessageHeaderView extends MessageMetadataView {

    public static MessageHeaderView.Builder<? extends MessageHeaderView.Builder> messageHeaderBuilder() {
        return new Builder();
    }

    public static class Builder<S extends MessageHeaderView.Builder<S>> extends MessageMetadataView.Builder<S> {
        protected String inReplyToMessageId;
        protected ImmutableMap<String, String> headers;
        protected Optional<Emailer> from;
        protected final ImmutableList.Builder<Emailer> to;
        protected final ImmutableList.Builder<Emailer> cc;
        protected final ImmutableList.Builder<Emailer> bcc;
        protected final ImmutableList.Builder<Emailer> replyTo;
        protected String subject;
        protected Instant date;

        protected Builder() {
            super();
            from = Optional.empty();
            to = ImmutableList.builder();
            cc = ImmutableList.builder();
            bcc = ImmutableList.builder();
            replyTo = ImmutableList.builder();
        }

        public S inReplyToMessageId(String inReplyToMessageId) {
            this.inReplyToMessageId = inReplyToMessageId;
            return (S) this;
        }

        public S headers(ImmutableMap<String, String> headers) {
            this.headers = headers;
            return (S) this;
        }

        public S from(Emailer from) {
            this.from = Optional.of(from);
            return (S) this;
        }

        public S from(Optional<Emailer> from) {
            this.from = from;
            return (S) this;
        }

        public S to(List<Emailer> to) {
            this.to.addAll(to);
            return (S) this;
        }

        public S cc(List<Emailer> cc) {
            this.cc.addAll(cc);
            return (S) this;
        }

        public S bcc(List<Emailer> bcc) {
            this.bcc.addAll(bcc);
            return (S) this;
        }

        public S replyTo(List<Emailer> replyTo) {
            this.replyTo.addAll(replyTo);
            return (S) this;
        }

        public S subject(String subject) {
            this.subject = subject;
            return (S) this;
        }

        public S date(Instant date) {
            this.date = date;
            return (S) this;
        }

        public MessageHeaderView build() {
            checkState();

            return new MessageHeaderView(id, blobId, threadId, mailboxIds, Optional.ofNullable(inReplyToMessageId),
                headers, from,
                to.build(), cc.build(), bcc.build(), replyTo.build(), subject, date, size,
                keywords.orElse(Keywords.DEFAULT_VALUE));
        }

        @Override
        protected void checkState() {
            super.checkState();
            Preconditions.checkState(headers != null, "'headers' is mandatory");
            Preconditions.checkState(date != null, "'date' is mandatory");
        }
    }

    private final Optional<String> inReplyToMessageId;
    @JsonFilter("headersFilter")
    private final ImmutableMap<String, String> headers;
    private final Optional<Emailer> from;
    private final ImmutableList<Emailer> to;
    private final ImmutableList<Emailer> cc;
    private final ImmutableList<Emailer> bcc;
    private final ImmutableList<Emailer> replyTo;
    private final String subject;
    private final Instant date;

    @VisibleForTesting
    MessageHeaderView(MessageId id,
                      BlobId blobId,
                      String threadId,
                      ImmutableSet<MailboxId> mailboxIds,
                      Optional<String> inReplyToMessageId,
                      ImmutableMap<String, String> headers,
                      Optional<Emailer> from,
                      ImmutableList<Emailer> to,
                      ImmutableList<Emailer> cc,
                      ImmutableList<Emailer> bcc,
                      ImmutableList<Emailer> replyTo,
                      String subject,
                      Instant date,
                      Number size,
                      Keywords keywords) {
        super(id, blobId, threadId, mailboxIds, size, keywords);
        this.inReplyToMessageId = inReplyToMessageId;
        this.headers = headers;
        this.from = from;
        this.to = to;
        this.cc = cc;
        this.bcc = bcc;
        this.replyTo = replyTo;
        this.subject = subject;
        this.date = date;
    }

    public Optional<String> getInReplyToMessageId() {
        return inReplyToMessageId;
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
}
