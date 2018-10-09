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

package org.apache.mailbox.tools.indexer.events;

import java.util.Objects;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MailboxPath;

public interface ImpactingMessageEvent extends ImpactingEvent {

    MessageUid getUid();

    Optional<Flags> newFlags();

    boolean wasDeleted();

    class FlagsMessageEvent implements ImpactingMessageEvent {

        private final MailboxPath mailboxPath;
        private final MessageUid uid;
        private final Flags flags;

        public FlagsMessageEvent(MailboxPath mailboxPath, MessageUid uid, Flags flags) {
            this.mailboxPath = mailboxPath;
            this.uid = uid;
            this.flags = flags;
        }

        @Override
        public MessageUid getUid() {
            return uid;
        }

        @Override
        public MailboxPath getMailboxPath() {
            return mailboxPath;
        }

        @Override
        public ImpactingEventType getType() {
            return ImpactingEventType.FlagsUpdate;
        }

        @Override
        public Optional<Flags> newFlags() {
            return Optional.of(flags);
        }

        @Override
        public boolean wasDeleted() {
            return false;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof FlagsMessageEvent) {
                FlagsMessageEvent that = (FlagsMessageEvent) o;

                return Objects.equals(this.mailboxPath, that.mailboxPath)
                    && Objects.equals(this.uid, that.uid)
                    && Objects.equals(this.flags, that.flags);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(mailboxPath, uid, flags);
        }
    }

    class MessageDeletedEvent implements ImpactingMessageEvent {

        private final MailboxPath mailboxPath;
        private final MessageUid uid;

        public MessageDeletedEvent(MailboxPath mailboxPath, MessageUid uid) {
            this.mailboxPath = mailboxPath;
            this.uid = uid;
        }

        @Override
        public MessageUid getUid() {
            return uid;
        }

        @Override
        public MailboxPath getMailboxPath() {
            return mailboxPath;
        }

        @Override
        public ImpactingEventType getType() {
            return ImpactingEventType.Deletion;
        }

        @Override
        public Optional<Flags> newFlags() {
            return Optional.empty();
        }

        @Override
        public boolean wasDeleted() {
            return true;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof MessageDeletedEvent) {
                MessageDeletedEvent that = (MessageDeletedEvent) o;

                return Objects.equals(this.mailboxPath, that.mailboxPath)
                    && Objects.equals(this.uid, that.uid);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(mailboxPath, uid);
        }
    }
}
