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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public class OldKeyword {
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

    public OldKeyword(Optional<Boolean> isUnread, Optional<Boolean> isFlagged, Optional<Boolean> isAnswered,
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
