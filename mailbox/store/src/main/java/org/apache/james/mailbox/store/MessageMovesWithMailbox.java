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
package org.apache.james.mailbox.store;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageMoves;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class MessageMovesWithMailbox {
    public static class Builder {
        private final ImmutableSet.Builder<Mailbox> previousMailboxes;
        private final ImmutableSet.Builder<Mailbox> targetMailboxes;

        private Builder() {
            previousMailboxes = ImmutableSet.builder();
            targetMailboxes = ImmutableSet.builder();
        }

        public Builder previousMailboxes(Iterable<Mailbox> mailboxes) {
            previousMailboxes.addAll(mailboxes);
            return this;
        }

        public Builder previousMailboxes(Mailbox... mailboxes) {
            previousMailboxes.addAll(Arrays.asList(mailboxes));
            return this;
        }

        public Builder targetMailboxes(Iterable<Mailbox> mailboxes) {
            targetMailboxes.addAll(mailboxes);
            return this;
        }

        public Builder targetMailboxes(Mailbox... mailboxes) {
            targetMailboxes.addAll(Arrays.asList(mailboxes));
            return this;
        }

        public MessageMovesWithMailbox build() {
            return new MessageMovesWithMailbox(previousMailboxes.build(), targetMailboxes.build());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final ImmutableSet<Mailbox> previousMailboxes;
    private final ImmutableSet<Mailbox> targetMailboxes;

    private MessageMovesWithMailbox(Collection<Mailbox> previousMailboxes, Collection<Mailbox> targetMailboxes) {
        this.previousMailboxes = ImmutableSet.copyOf(previousMailboxes);
        this.targetMailboxes = ImmutableSet.copyOf(targetMailboxes);
    }

    public boolean isChange() {
        return !previousMailboxes.equals(targetMailboxes);
    }

    public Set<Mailbox> addedMailboxes() {
        return Sets.difference(targetMailboxes, previousMailboxes);
    }

    public Set<Mailbox> removedMailboxes() {
        return Sets.difference(previousMailboxes, targetMailboxes);
    }

    public ImmutableSet<Mailbox> getPreviousMailboxes() {
        return previousMailboxes;
    }

    public ImmutableSet<Mailbox> getTargetMailboxes() {
        return targetMailboxes;
    }

    public Stream<Mailbox> impactedMailboxes() {
        return Stream.concat(
            addedMailboxes().stream(),
            removedMailboxes().stream());
    }

    public MessageMovesWithMailbox filterPrevious(Predicate<Mailbox> predicate) {
        return builder()
            .targetMailboxes(targetMailboxes)
            .previousMailboxes(previousMailboxes.stream()
                .filter(predicate)
                .collect(Guavate.toImmutableSet()))
            .build();
    }

    public MessageMovesWithMailbox filterTargets(Predicate<Mailbox> predicate) {
        return builder()
            .previousMailboxes(previousMailboxes)
            .targetMailboxes(targetMailboxes.stream()
                .filter(predicate)
                .collect(Guavate.toImmutableSet()))
            .build();
    }

    public MessageMoves asMessageMoves() {
        return MessageMoves.builder()
            .previousMailboxIds(previousMailboxes.stream()
                .map(Mailbox::getMailboxId)
                .collect(Guavate.toImmutableSet()))
            .targetMailboxIds(targetMailboxes.stream()
                .map(Mailbox::getMailboxId)
                .collect(Guavate.toImmutableSet()))
            .build();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MessageMovesWithMailbox) {
            MessageMovesWithMailbox that = (MessageMovesWithMailbox) o;

            return Objects.equals(this.previousMailboxes, that.previousMailboxes)
                && Objects.equals(this.targetMailboxes, that.targetMailboxes);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(previousMailboxes, targetMailboxes);
    }
}