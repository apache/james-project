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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.mail.Flags;

import org.apache.james.jmap.methods.ValidationResult;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

@JsonDeserialize(builder = UpdateMessagePatch.Builder.class)
public class UpdateMessagePatch {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private Optional<List<String>> mailboxIds = Optional.empty();
        private Optional<Boolean> isFlagged = Optional.empty();
        private Optional<Boolean> isUnread = Optional.empty();
        private Optional<Boolean> isAnswered = Optional.empty();
        private Optional<Map<String, Boolean>> keywords = Optional.empty();
        private Set<ValidationResult> validationResult = Sets.newHashSet();

        public Builder mailboxIds(List<String> mailboxIds) {
            this.mailboxIds = Optional.of(ImmutableList.copyOf(mailboxIds));
            return this;
        }

        public Builder keywords(Map<String, Boolean> keywords) {
            this.keywords = Optional.of(ImmutableMap.copyOf(keywords));
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
            this.validationResult.addAll(validationResult);
            return this;
        }

        public UpdateMessagePatch build() {
            if (mailboxIds.isPresent() && mailboxIds.get().isEmpty()) {
                validationResult(ImmutableSet.of(ValidationResult.builder()
                    .property("mailboxIds")
                    .message("mailboxIds property is not supposed to be empty")
                    .build()));
            }

            Optional<Keywords> updateKeywords = Keywords.factory()
                .throwOnImapNonExposedKeywords()
                .fromMapOrOldKeyword(keywords, getOldKeywords());

            return new UpdateMessagePatch(mailboxIds, updateKeywords, ImmutableList.copyOf(validationResult));
        }

        private Optional<OldKeyword> getOldKeywords() {
            if (isAnswered.isPresent() || isFlagged.isPresent() || isUnread.isPresent()) {
                Optional<Boolean> isDraft = Optional.empty();
                return Optional.of(new OldKeyword(isUnread, isFlagged, isAnswered, isDraft));
            }
            return Optional.empty();
        }

    }

    private final Optional<List<String>> mailboxIds;
    private final Optional<Keywords> keywords;

    private final ImmutableList<ValidationResult> validationErrors;

    @VisibleForTesting
    UpdateMessagePatch(Optional<List<String>> mailboxIds,
                       Optional<Keywords> keywords,
                       ImmutableList<ValidationResult> validationResults) {

        this.mailboxIds = mailboxIds;
        this.keywords = keywords;
        this.validationErrors = validationResults;
    }

    public Optional<List<String>> getMailboxIds() {
        return mailboxIds;
    }

    public Optional<Keywords> getKeywords() {
        return keywords;
    }

    public boolean isFlagsIdentity() {
        return !keywords.isPresent();
    }

    public ImmutableList<ValidationResult> getValidationErrors() {
        return validationErrors;
    }

    public boolean isValid() {
        return getValidationErrors().isEmpty();
    }

    public Flags applyToState(Flags currentFlags) {
        return keywords.map(keyword -> {
            if (currentFlags.contains(Flags.Flag.DRAFT) != keyword.getKeywords().contains(Keyword.DRAFT)) {
                throw new IllegalArgumentException("Cannot add or remove draft flag");
            }
            return keyword.asFlagsWithRecentAndDeletedFrom(currentFlags);
        }).orElse(new Flags());
    }

}
