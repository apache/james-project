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
package org.apache.james.mailbox.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class MessageMoves {

    public static class Builder {
        private final ImmutableSet.Builder<MailboxId> previousMailboxIds;
        private final ImmutableSet.Builder<MailboxId> targetMailboxIds;

        private Builder() {
            previousMailboxIds = ImmutableSet.builder();
            targetMailboxIds = ImmutableSet.builder();
        }

        public Builder previousMailboxIds(Iterable<MailboxId> mailboxIds) {
            previousMailboxIds.addAll(mailboxIds);
            return this;
        }

        public Builder previousMailboxIds(MailboxId... mailboxIds) {
            previousMailboxIds.addAll(Arrays.asList(mailboxIds));
            return this;
        }

        public Builder targetMailboxIds(Iterable<MailboxId> mailboxIds) {
            targetMailboxIds.addAll(mailboxIds);
            return this;
        }

        public Builder targetMailboxIds(MailboxId... mailboxIds) {
            targetMailboxIds.addAll(Arrays.asList(mailboxIds));
            return this;
        }

        public MessageMoves build() {
            return new MessageMoves(previousMailboxIds.build(), targetMailboxIds.build());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final ImmutableSet<MailboxId> previousMailboxIds;
    private final ImmutableSet<MailboxId> targetMailboxIds;

    private MessageMoves(Collection<MailboxId> previousMailboxIds, Collection<MailboxId> targetMailboxIds) {
        this.previousMailboxIds = ImmutableSet.copyOf(previousMailboxIds);
        this.targetMailboxIds = ImmutableSet.copyOf(targetMailboxIds);
    }

    public boolean isChange() {
        return !previousMailboxIds.equals(targetMailboxIds);
    }

    public Set<MailboxId> addedMailboxIds() {
        return Sets.difference(targetMailboxIds, previousMailboxIds);
    }

    public Set<MailboxId> removedMailboxIds() {
        return Sets.difference(previousMailboxIds, targetMailboxIds);
    }

    public ImmutableSet<MailboxId> getPreviousMailboxIds() {
        return previousMailboxIds;
    }

    public ImmutableSet<MailboxId> getTargetMailboxIds() {
        return targetMailboxIds;
    }

    public Stream<MailboxId> impactedMailboxIds() {
        return Stream.concat(
            addedMailboxIds().stream(),
            removedMailboxIds().stream());
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MessageMoves) {
            MessageMoves that = (MessageMoves) o;

            return Objects.equals(this.previousMailboxIds, that.previousMailboxIds)
                && Objects.equals(this.targetMailboxIds, that.targetMailboxIds);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(previousMailboxIds, targetMailboxIds);
    }
}