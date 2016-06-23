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
import java.util.Set;

import javax.mail.Flags;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.NotImplementedException;
import org.apache.james.jmap.methods.ValidationResult;
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
        private Set<ValidationResult> validationResult = ImmutableSet.of();

        public Builder mailboxIds(Optional<List<String>> mailboxIds) {
            if (mailboxIds.isPresent()) {
                throw new NotImplementedException("moving a message is not supported");
            }
            return this;
        }

        public Builder isFlagged(Boolean isFlagged) {
            this.isFlagged = Optional.of(isFlagged);
            return this;
        }

        public Builder isUnread(Boolean isUnread) {
            this.isUnread = Optional.of(isUnread);
            return this;
        }

        public Builder isAnswered(Boolean isAnswered) {
            this.isAnswered = Optional.of(isAnswered);
            return this;
        }

        public Builder validationResult(Set<ValidationResult> validationResult) {
            this.validationResult = ImmutableSet.copyOf(validationResult);
            return this;
        }

        public UpdateMessagePatch build() {
            return new UpdateMessagePatch(mailboxIds.build(), isUnread, isFlagged, isAnswered, ImmutableList.copyOf(validationResult));
        }
    }

    private final List<String> mailboxIds;
    private final Optional<Boolean> isUnread;
    private final Optional<Boolean> isFlagged;
    private final Optional<Boolean> isAnswered;

    private final ImmutableList<ValidationResult> validationErrors;

    @VisibleForTesting
    UpdateMessagePatch(List<String> mailboxIds,
                       Optional<Boolean> isUnread,
                       Optional<Boolean> isFlagged,
                       Optional<Boolean> isAnswered,
                       ImmutableList<ValidationResult> validationResults) {

        this.mailboxIds = mailboxIds;
        this.isUnread = isUnread;
        this.isFlagged = isFlagged;
        this.isAnswered = isAnswered;
        this.validationErrors = validationResults;
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

    public ImmutableList<ValidationResult> getValidationErrors() {
        return validationErrors;
    }

    public boolean isValid() {
        return getValidationErrors().isEmpty();
    }

    public Flags applyToState(boolean isSeen, boolean isAnswered, boolean isFlagged) {
        Flags newStateFlags = new Flags();

        boolean shouldMessageBeFlagged = isFlagged().isPresent() && isFlagged().get() || (!isFlagged().isPresent() && isFlagged);
        if (shouldMessageBeFlagged) {
            newStateFlags.add(Flags.Flag.FLAGGED);
        }
        boolean shouldMessageBeMarkAnswered = isAnswered().isPresent() && isAnswered().get() || (!isAnswered().isPresent() && isAnswered);
        if (shouldMessageBeMarkAnswered) {
            newStateFlags.add(Flags.Flag.ANSWERED);
        }
        boolean shouldMessageBeMarkSeen = isUnread().isPresent() && !isUnread().get() || (!isUnread().isPresent() && isSeen);
        if (shouldMessageBeMarkSeen) {
            newStateFlags.add(Flags.Flag.SEEN);
        }
        return newStateFlags;
    }

}
