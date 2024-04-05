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

package org.apache.james.jmap.draft.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jakarta.mail.Flags;

import org.apache.james.jmap.draft.methods.ValidationResult;
import org.apache.james.jmap.model.Keywords;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
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
        private OldKeyword.Builder oldKeyworkBuilder = OldKeyword.builder();
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
            oldKeyworkBuilder.isFlagged(isFlagged);
            return this;
        }

        public Builder isUnread(Boolean isUnread) {
            oldKeyworkBuilder.isUnread(isUnread);
            return this;
        }

        public Builder isAnswered(Boolean isAnswered) {
            oldKeyworkBuilder.isAnswered(isAnswered);
            return this;
        }

        public Builder isDraft(Boolean isDraft) {
            oldKeyworkBuilder.isDraft(isDraft);
            return this;
        }

        public Builder isForwarded(Boolean isForwarded) {
            oldKeyworkBuilder.isForwarded(isForwarded);
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

            Optional<Keywords> mayBeKeywords = creationKeywords();
            Optional<OldKeyword> oldKeywords = oldKeyworkBuilder.computeOldKeyword();
            Preconditions.checkArgument(!(mayBeKeywords.isPresent() && oldKeywords.isPresent()), "Does not support keyword and is* at the same time");

            return new UpdateMessagePatch(mailboxIds, mayBeKeywords, oldKeywords, ImmutableList.copyOf(validationResult));
        }

        public Optional<Keywords> creationKeywords() {
            return keywords.map(map -> Keywords.strictFactory()
                    .fromMap(map));
        }

    }

    private final Optional<List<String>> mailboxIds;
    private final Optional<Keywords> keywords;
    private final Optional<OldKeyword> oldKeywords;
    private final ImmutableList<ValidationResult> validationErrors;

    @VisibleForTesting
    UpdateMessagePatch(Optional<List<String>> mailboxIds,
                       Optional<Keywords> keywords,
                       Optional<OldKeyword> oldKeywords,
                       ImmutableList<ValidationResult> validationResults) {

        this.mailboxIds = mailboxIds;
        this.keywords = keywords;
        this.oldKeywords = oldKeywords;
        this.validationErrors = validationResults;
    }

    public Optional<List<String>> getMailboxIds() {
        return mailboxIds;
    }

    public boolean isFlagsIdentity() {
        return !oldKeywords.isPresent() && !keywords.isPresent();
    }

    public boolean isOnlyAFlagUpdate() {
        return !mailboxIds.isPresent() && (oldKeywords.isPresent() || keywords.isPresent());
    }

    public boolean isOnlyAMove() {
        return mailboxIds.map(list -> list.size() == 1).orElse(false)
            && oldKeywords.isEmpty()
            && keywords.isEmpty();
    }

    public ImmutableList<ValidationResult> getValidationErrors() {
        return validationErrors;
    }

    public boolean isValid() {
        return getValidationErrors().isEmpty();
    }

    public Flags applyToState(Flags currentFlags) {
        return oldKeywords
            .map(oldKeyword -> oldKeyword.applyToState(currentFlags))
            .orElse(keywords
                .map(keyword -> keyword.asFlagsWithRecentAndDeletedFrom(currentFlags))
                .orElse(currentFlags));
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof UpdateMessagePatch) {
            UpdateMessagePatch that = (UpdateMessagePatch) o;

            return Objects.equals(this.mailboxIds, that.mailboxIds)
                && Objects.equals(this.keywords, that.keywords)
                && Objects.equals(this.oldKeywords, that.oldKeywords)
                && Objects.equals(this.validationErrors, that.validationErrors);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(mailboxIds, keywords, oldKeywords, validationErrors);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("mailboxIds", mailboxIds)
            .add("keywords", keywords)
            .add("oldKeywords", oldKeywords)
            .add("validationErrors", validationErrors)
            .toString();
    }
}
