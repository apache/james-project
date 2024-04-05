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

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import org.apache.james.jmap.model.BlobId;
import org.apache.james.jmap.model.Keyword;
import org.apache.james.jmap.model.Keywords;
import org.apache.james.jmap.model.Number;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class MessageMetadataView implements MessageView {

    public static Builder messageMetadataBuilder() {
        return new Builder();
    }

    public static class Builder<S extends Builder<S>> {
        protected MessageId id;
        protected BlobId blobId;
        protected String threadId;
        protected ImmutableSet<MailboxId> mailboxIds;
        protected Number size;
        protected Optional<Keywords> keywords = Optional.empty();

        public S id(MessageId id) {
            this.id = id;
            return (S) this;
        }

        public S blobId(BlobId blobId) {
            this.blobId = blobId;
            return (S) this;
        }

        public S threadId(String threadId) {
            this.threadId = threadId;
            return (S) this;
        }

        @JsonIgnore
        public S mailboxId(MailboxId mailboxId) {
            return this.fluentMailboxIds(mailboxId);
        }

        @JsonIgnore
        public S fluentMailboxIds(MailboxId... mailboxIds) {
            return this.mailboxIds(Arrays.asList((mailboxIds)));
        }

        public S mailboxIds(Collection<MailboxId> mailboxIds) {
            this.mailboxIds = ImmutableSet.copyOf(mailboxIds);
            return (S) this;
        }

        public S keywords(Keywords keywords) {
            this.keywords = Optional.ofNullable(keywords);
            return (S) this;
        }

        public S size(long size) {
            this.size = Number.BOUND_SANITIZING_FACTORY.from(size);
            return (S) this;
        }

        public MessageMetadataView build() {
            checkState();

            return new MessageMetadataView(id, blobId, threadId, mailboxIds, size, keywords.orElse(Keywords.DEFAULT_VALUE));
        }

        protected void checkState() {
            Preconditions.checkState(id != null, "'id' is mandatory");
            Preconditions.checkState(blobId != null, "'blobId' is mandatory");
            Preconditions.checkState(!Strings.isNullOrEmpty(threadId), "'threadId' is mandatory");
            Preconditions.checkState(mailboxIds != null, "'mailboxIds' is mandatory");
            Preconditions.checkState(size != null, "'size' is mandatory");
        }
    }

    private final MessageId id;
    private final BlobId blobId;
    private final String threadId;
    private final ImmutableSet<MailboxId> mailboxIds;
    private final Number size;
    private final Keywords keywords;

    @VisibleForTesting
    MessageMetadataView(MessageId id, BlobId blobId, String threadId, ImmutableSet<MailboxId> mailboxIds, Number size, Keywords keywords) {
        this.id = id;
        this.blobId = blobId;
        this.threadId = threadId;
        this.mailboxIds = mailboxIds;
        this.size = size;
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

    public ImmutableSet<MailboxId> getMailboxIds() {
        return mailboxIds;
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
        return keywords.contains(Keyword.FORWARDED)
            || keywords.contains(Keyword.of("$forwarded"));
    }

    public Number getSize() {
        return size;
    }

    public ImmutableMap<String, Boolean> getKeywords() {
        return keywords.asMap();
    }
}
