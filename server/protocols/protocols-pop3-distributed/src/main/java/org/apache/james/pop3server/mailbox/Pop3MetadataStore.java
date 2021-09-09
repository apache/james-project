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

package org.apache.james.pop3server.mailbox;

import java.util.Objects;

import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.reactivestreams.Publisher;

import com.google.common.base.MoreObjects;


public interface Pop3MetadataStore {
    class StatMetadata {
        private final MessageId messageId;
        private final long size;

        public StatMetadata(MessageId messageId, long size) {
            this.messageId = messageId;
            this.size = size;
        }

        public MessageId getMessageId() {
            return messageId;
        }

        public long getSize() {
            return size;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof StatMetadata) {
                StatMetadata that = (StatMetadata) o;

                return this.size == that.size
                    && Objects.equals(this.messageId, that.messageId);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(messageId, size);
        }

        @Override
        public String toString() {
            return "StatMetadata{" +
                "messageId=" + messageId +
                ", size=" + size +
                '}';
        }
    }

    class FullMetadata extends StatMetadata {
        private final MailboxId mailboxId;

        public FullMetadata(MailboxId mailboxId, MessageId messageId, long size) {
            super(messageId, size);
            this.mailboxId = mailboxId;
        }

        public FullMetadata(MailboxId mailboxId, StatMetadata statMetadata) {
            super(statMetadata.getMessageId(), statMetadata.getSize());
            this.mailboxId = mailboxId;
        }

        public MailboxId getMailboxId() {
            return mailboxId;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof FullMetadata) {
                FullMetadata that = (FullMetadata) o;

                return this.getSize() == that.getSize()
                    && Objects.equals(this.getMessageId(), that.getMessageId())
                    && Objects.equals(this.mailboxId, that.mailboxId);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(mailboxId, this.getMessageId(), this.getSize());
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("mailboxId", mailboxId)
                .add("messageId", this.getMessageId())
                .add("size", this.getSize())
                .toString();
        }
    }

    Publisher<StatMetadata> stat(MailboxId mailboxId);

    Publisher<FullMetadata> listAllEntries();

    Publisher<FullMetadata> retrieve(MailboxId mailboxId, MessageId messageId);

    Publisher<Void> add(MailboxId mailboxId, StatMetadata statMetadata);

    Publisher<Void> remove(MailboxId mailboxId, MessageId messageId);

    Publisher<Void> clear(MailboxId mailboxId);
}
