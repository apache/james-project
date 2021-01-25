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
package org.apache.james.events;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMoves;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class MessageMoveEvent implements Event {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ImmutableList.Builder<MessageId> messageIds;
        private Username username;
        private MessageMoves messageMoves;
        private Optional<EventId> eventId;

        private Builder() {
            messageIds = ImmutableList.builder();
            eventId = Optional.empty();
        }

        public Builder session(MailboxSession session) {
            this.username = session.getUser();
            return this;
        }

        public Builder user(Username username) {
            this.username = username;
            return this;
        }

        public Builder messageMoves(MessageMoves messageMoves) {
            this.messageMoves = messageMoves;
            return this;
        }

        public Builder messageId(MessageId messageId) {
            this.messageIds.add(messageId);
            return this;
        }

        public Builder eventId(EventId eventId) {
            this.eventId = Optional.of(eventId);
            return this;
        }

        public Builder messageId(Iterable<MessageId> messageIds) {
            this.messageIds.addAll(messageIds);
            return this;
        }

        public MessageMoveEvent build() {
            Preconditions.checkNotNull(username, "'user' is mandatory");
            Preconditions.checkNotNull(messageMoves, "'messageMoves' is mandatory");

            return new MessageMoveEvent(eventId.orElse(EventId.random()), username, messageMoves, messageIds.build());
        }
    }

    private final EventId eventId;
    private final Username username;
    private final MessageMoves messageMoves;
    private final Collection<MessageId> messageIds;

    @VisibleForTesting
    MessageMoveEvent(EventId eventId, Username username, MessageMoves messageMoves, Collection<MessageId> messageIds) {
        this.eventId = eventId;
        this.username = username;
        this.messageMoves = messageMoves;
        this.messageIds = messageIds;
    }

    @Override
    public boolean isNoop() {
        return messageIds.isEmpty();
    }

    public Collection<MessageId> getMessageIds() {
        return messageIds;
    }

    @Override
    public EventId getEventId() {
        return eventId;
    }

    @Override
    public Username getUsername() {
        return username;
    }

    public MessageMoves getMessageMoves() {
        return messageMoves;
    }

    public boolean isMoveTo(MailboxId mailboxId) {
        return messageMoves.addedMailboxIds()
                .contains(mailboxId);
    }

    public boolean isMoveFrom(MailboxId mailboxId) {
        return messageMoves.removedMailboxIds()
                .contains(mailboxId);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MessageMoveEvent) {
            MessageMoveEvent that = (MessageMoveEvent) o;

            return Objects.equals(this.eventId, that.eventId)
                && Objects.equals(this.username, that.username)
                && Objects.equals(this.messageMoves, that.messageMoves)
                && Objects.equals(this.messageIds, that.messageIds);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(eventId, username, messageMoves, messageIds);
    }
}
