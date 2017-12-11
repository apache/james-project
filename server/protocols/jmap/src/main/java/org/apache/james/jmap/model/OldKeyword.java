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

import java.util.Optional;

import javax.mail.Flags;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public class OldKeyword {

    public static class Builder {
        private Optional<Boolean> isUnread;
        private Optional<Boolean> isFlagged;
        private Optional<Boolean> isAnswered;
        private Optional<Boolean> isDraft;
        private Optional<Boolean> isForwarded;

        private Builder() {
            isUnread = Optional.empty();
            isFlagged = Optional.empty();
            isAnswered = Optional.empty();
            isDraft = Optional.empty();
            isForwarded = Optional.empty();
        }

        public Builder isFlagged(Optional<Boolean> isFlagged) {
            this.isFlagged = isFlagged;
            return this;
        }

        public Builder isFlagged(boolean isFlagged) {
            return isFlagged(Optional.of(isFlagged));
        }

        public Builder isUnread(Optional<Boolean> isUnread) {
            this.isUnread = isUnread;
            return this;
        }

        public Builder isUnread(boolean isUnread) {
            return isUnread(Optional.of(isUnread));
        }

        public Builder isAnswered(Optional<Boolean> isAnswered) {
            this.isAnswered = isAnswered;
            return this;
        }

        public Builder isAnswered(boolean isAnswered) {
            return isAnswered(Optional.of(isAnswered));
        }

        public Builder isDraft(Optional<Boolean> isDraft) {
            this.isDraft = isDraft;
            return this;
        }

        public Builder isDraft(boolean isDraft) {
            return isDraft(Optional.of(isDraft));
        }

        public Builder isForwarded(Optional<Boolean> isForwarded) {
            this.isForwarded = isForwarded;
            return this;
        }

        public Builder isForwarded(boolean isForwarded) {
            return isForwarded(Optional.of(isForwarded));
        }

        public Optional<OldKeyword> computeOldKeyword() {
            if (isAnswered.isPresent() || isFlagged.isPresent() || isUnread.isPresent() || isForwarded.isPresent() || isDraft.isPresent()) {
                return Optional.of(new OldKeyword(isUnread, isFlagged, isAnswered, isDraft, isForwarded));
            }

            return Optional.empty();
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    private final Optional<Boolean> isUnread;
    private final Optional<Boolean> isFlagged;
    private final Optional<Boolean> isAnswered;
    private final Optional<Boolean> isDraft;
    private final Optional<Boolean> isForwarded;

    @VisibleForTesting
    OldKeyword(boolean isUnread, boolean isFlagged, boolean isAnswered, boolean isDraft, boolean isForwarded) {
        this.isUnread = Optional.of(isUnread);
        this.isFlagged = Optional.of(isFlagged);
        this.isAnswered = Optional.of(isAnswered);
        this.isDraft = Optional.of(isDraft);
        this.isForwarded = Optional.of(isForwarded);
    }

    private OldKeyword(Optional<Boolean> isUnread, Optional<Boolean> isFlagged, Optional<Boolean> isAnswered,
                      Optional<Boolean> isDraft, Optional<Boolean> isForwarded) {
        this.isUnread = isUnread;
        this.isFlagged = isFlagged;
        this.isAnswered = isAnswered;
        this.isDraft = isDraft;
        this.isForwarded = isForwarded;
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

    public Optional<Boolean> isDraft() {
        return isDraft;
    }

    public Optional<Boolean> isForwarded() {
        return isForwarded;
    }

    public Flags applyToState(Flags currentFlags) {
        Flags newStateFlags = new Flags();

        if (isFlagged().orElse(currentFlags.contains(Flags.Flag.FLAGGED))) {
            newStateFlags.add(Flags.Flag.FLAGGED);
        }
        if (isAnswered().orElse(currentFlags.contains(Flags.Flag.ANSWERED))) {
            newStateFlags.add(Flags.Flag.ANSWERED);
        }
        if (isDraft().orElse(currentFlags.contains(Flags.Flag.DRAFT))) {
            newStateFlags.add(Flags.Flag.DRAFT);
        }
        if (isForwarded().orElse(currentFlags.contains(new Flags("$Forwarded")))) {
            newStateFlags.add(new Flags("$Forwarded"));
        }
        boolean shouldMessageBeMarkSeen = isUnread().map(b -> !b).orElse(currentFlags.contains(Flags.Flag.SEEN));
        if (shouldMessageBeMarkSeen) {
            newStateFlags.add(Flags.Flag.SEEN);
        }
        return newStateFlags;
    }

    public Flags asFlags() {
        Flags newStateFlags = new Flags();

        if (isFlagged().orElse(false)) {
            newStateFlags.add(Flags.Flag.FLAGGED);
        }
        if (isAnswered().orElse(false)) {
            newStateFlags.add(Flags.Flag.ANSWERED);
        }
        if (isDraft().orElse(false)) {
            newStateFlags.add(Flags.Flag.DRAFT);
        }
        if (isForwarded().orElse(false)) {
            newStateFlags.add(new Flags("$Forwarded"));
        }
        boolean shouldMessageBeMarkSeen = isUnread().map(b -> !b).orElse(false);
        if (shouldMessageBeMarkSeen) {
            newStateFlags.add(Flags.Flag.SEEN);
        }
        return newStateFlags;
    }

    @Override
    public final boolean equals(Object other) {
        if (other instanceof OldKeyword) {
            OldKeyword oldKeyword = (OldKeyword) other;
            return Objects.equal(isUnread, oldKeyword.isUnread)
                && Objects.equal(isFlagged, oldKeyword.isFlagged)
                && Objects.equal(isAnswered, oldKeyword.isAnswered)
                && Objects.equal(isDraft, oldKeyword.isDraft)
                && Objects.equal(isForwarded, oldKeyword.isForwarded);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(isUnread, isFlagged, isAnswered, isDraft, isForwarded);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("isUnread", isUnread)
                .add("isFlagged", isFlagged)
                .add("isAnswered", isAnswered)
                .add("isDraft", isDraft)
                .add("isForwarded", isForwarded)
                .toString();
    }

}
