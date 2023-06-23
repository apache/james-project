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

package org.apache.james.mailbox.store.mail.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class ListMessageAssert {
    private final List<MailboxMessage> actual;

    private List<InnerMessage> messageToInnerMessage(List<MailboxMessage> messages) {
        return messages.stream()
            .map(message -> getInnerMessage(message))
            .collect(ImmutableList.toImmutableList());
    }

    private InnerMessage getInnerMessage(MailboxMessage message) {
        try {
            return new InnerMessage(message.getMessageId(), message.getUid(), message.getMailboxId(), message.getInternalDate(), message.getBodyOctets(),
                    message.getFullContentOctets(), message.getMediaType(), message.getSubType(), IOUtils.toString(message.getFullContent(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ListMessageAssert(List<MailboxMessage> actual) {
        this.actual = actual;
    }

    public static ListMessageAssert assertMessages(List<MailboxMessage> actual) {
        return new ListMessageAssert(actual);
    }

    public void containOnly(MailboxMessage... expecteds) {
        assertThat(messageToInnerMessage(actual)).containsOnly(messageToInnerMessage(Lists.newArrayList(expecteds)).toArray(new InnerMessage[0]));
    }

    private final class InnerMessage {
        private final MessageUid uid;
        private final MailboxId mailboxId;
        private final Date internalDate;
        private final long bodyOctets;
        private final long fullContentOctets;
        private final String mediaType;
        private final String subType;
        private final String content;

        public InnerMessage(MessageId id, MessageUid uid, MailboxId mailboxId, Date internalDate, long bodyOctets,
                long fullContentOctets, String mediaType, String subType, String content) {
            this.uid = uid;
            this.mailboxId = mailboxId;
            this.internalDate = internalDate;
            this.bodyOctets = bodyOctets;
            this.fullContentOctets = fullContentOctets;
            this.mediaType = mediaType;
            this.subType = subType;
            this.content = content;
        }

        public MessageUid getUid() {
            return uid;
        }

        public MailboxId getMailboxId() {
            return mailboxId;
        }

        public Date getInternalDate() {
            return internalDate;
        }

        public long getBodyOctets() {
            return bodyOctets;
        }

        public long getFullContentOctets() {
            return fullContentOctets;
        }

        public String getMediaType() {
            return mediaType;
        }

        public String getSubType() {
            return subType;
        }

        public String getContent() {
            return content;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(uid, mailboxId, internalDate, bodyOctets, fullContentOctets, mediaType, subType, content);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof InnerMessage) {
                InnerMessage o = (InnerMessage)obj;
                return Objects.equal(uid, o.getUid())
                    && Objects.equal(mailboxId, o.getMailboxId())
                    && Objects.equal(internalDate, o.getInternalDate())
                    && Objects.equal(bodyOctets, o.getBodyOctets())
                    && Objects.equal(fullContentOctets, o.getFullContentOctets())
                    && Objects.equal(mediaType, o.getMediaType())
                    && Objects.equal(subType, o.getSubType())
                    && Objects.equal(content, o.getContent());
            }
            return false;
        }
    }
}
