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

import java.util.List;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.commons.lang.NotImplementedException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

@JsonDeserialize(builder = UpdateMessagePatch.Builder.class)
public class UpdateMessagePatch {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private ImmutableList.Builder<String> mailboxIds = ImmutableList.builder();
        private Optional<Boolean> isFlagged = Optional.empty();
        private Optional<Boolean> isUnread = Optional.empty();
        private Optional<Boolean> isAnswered = Optional.empty();

        public Builder mailboxIds(Optional<List<String>> mailboxIds) {
            if (mailboxIds.isPresent()) {
                throw new NotImplementedException();
            }
            return this;
        }

        public Builder isFlagged(Optional<Boolean> isFlagged) {
            this.isFlagged = isFlagged;
            return this;
        }

        public Builder isUnread(Optional<Boolean> isUnread) {
            this.isUnread = isUnread;
            return this;
        }

        public Builder isAnswered(Optional<Boolean> isAnswered) {
            this.isAnswered = isAnswered;
            return this;
        }

        public UpdateMessagePatch build() {

            return new UpdateMessagePatch(mailboxIds.build(), isUnread, isFlagged, isAnswered);
        }
    }

    private final List<String> mailboxIds;
    private final Optional<Boolean> isUnread;
    private final Optional<Boolean> isFlagged;
    private final Optional<Boolean> isAnswered;

    @VisibleForTesting
    UpdateMessagePatch(List<String> mailboxIds,
                       Optional<Boolean> isUnread,
                       Optional<Boolean> isFlagged,
                       Optional<Boolean> isAnswered) {

        this.mailboxIds = mailboxIds;
        this.isUnread = isUnread;
        this.isFlagged = isFlagged;
        this.isAnswered = isAnswered;
    }

    public List<String> getMailboxIds() {
        return mailboxIds;
    }

    public Optional<Boolean> isUnread() {
        return isUnread;
    }

    public Optional<Boolean> isFlagged() {
        return isFlagged;
    }

    public Optional<Boolean> isAnswered() {
        return isAnswered;
    }

    public boolean isValid() {
        return true; // to be implemented when UpdateMessagePatch would allow any message property to be set
    }

    public Flags applyToState(boolean isSeen, boolean isAnswered, boolean isFlagged) {
        Flags newStateFlags = new Flags();
        if (!isSeen && isUnread().isPresent() && !isUnread().get()) {
            newStateFlags.add(Flags.Flag.SEEN);
        }
        if (!isAnswered && isAnswered().isPresent() && isAnswered().get()) {
            newStateFlags.add(Flags.Flag.ANSWERED);
        }
        if (!isFlagged && isFlagged().isPresent() && isFlagged().get()) {
            newStateFlags.add(Flags.Flag.FLAGGED);
        }
        return newStateFlags;
    }
}
