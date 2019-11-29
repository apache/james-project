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

package org.apache.james.jmap.draft.model.message.view;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.draft.methods.JmapResponseWriterImpl;
import org.apache.james.jmap.draft.model.BlobId;
import org.apache.james.jmap.draft.model.Emailer;
import org.apache.james.jmap.draft.model.Keywords;
import org.apache.james.jmap.draft.model.Number;
import org.apache.james.jmap.draft.model.PreviewDTO;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * This message view holds all Message properties expected to be fast.
 */
@JsonDeserialize(builder = MessageFastView.Builder.class)
@JsonFilter(JmapResponseWriterImpl.PROPERTIES_FILTER)
public class MessageFastView extends MessageHeaderView {

    public static MessageFastView.Builder<? extends MessageFastView.Builder> builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder<S extends MessageFastView.Builder<S>> extends MessageHeaderView.Builder<S> {
        protected Optional<Preview> preview;

        protected Builder() {
            super();
        }

        public S preview(Preview preview) {
            this.preview = Optional.of(preview);
            return (S) this;
        }

        public S preview(Optional<Preview> preview) {
            this.preview = preview;
            return (S) this;
        }

        public MessageFastView build() {
            checkState();
            return new MessageFastView(id, blobId, threadId, mailboxIds, Optional.ofNullable(inReplyToMessageId),
                headers, Optional.ofNullable(from),
                to.build(), cc.build(), bcc.build(), replyTo.build(), subject, date, size, PreviewDTO.from(preview),
                keywords.orElse(Keywords.DEFAULT_VALUE));
        }

        public void checkState() {
            super.checkState();
            Preconditions.checkState(preview != null, "'preview' is mandatory");
        }
    }


    private final PreviewDTO preview;

    @VisibleForTesting
    MessageFastView(MessageId id,
                    BlobId blobId,
                    String threadId,
                    ImmutableList<MailboxId> mailboxIds,
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
                    PreviewDTO preview,
                    Keywords keywords) {
        super(id, blobId, threadId, mailboxIds, inReplyToMessageId, headers, from, to, cc, bcc, replyTo, subject, date, size, keywords);
        this.preview = preview;
    }

    public PreviewDTO getPreview() {
        return preview;
    }
}
