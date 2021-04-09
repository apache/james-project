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

package org.apache.james.jmap.api.change;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.mailbox.model.MessageId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class EmailChanges {

    public static class Builder {

        public static class EmailChangeCollector implements Collector<EmailChange, Builder, EmailChanges> {
            private final Limit limit;
            private final State state;

            public EmailChangeCollector(State state, Limit limit) {
                this.limit = limit;
                this.state = state;
            }

            @Override
            public Supplier<Builder> supplier() {
                return () -> new Builder(state, limit);
            }

            public BiConsumer<Builder, EmailChange> accumulator() {
                return Builder::add;
            }

            @Override
            public BinaryOperator<Builder> combiner() {
                throw new NotImplementedException("Not supported");
            }

            @Override
            public Function<Builder, EmailChanges> finisher() {
                return Builder::build;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Sets.immutableEnumSet(Characteristics.UNORDERED);
            }
        }

        private State state;
        private boolean hasMoreChanges;
        private boolean canAddMoreItem;
        private Limit limit;
        private Set<MessageId> created;
        private Set<MessageId> updated;
        private Set<MessageId> destroyed;

        public Builder(State state, Limit limit) {
            this.limit = limit;
            this.state = state;
            this.hasMoreChanges = false;
            this.canAddMoreItem = true;
            this.created = new HashSet<>();
            this.updated = new HashSet<>();
            this.destroyed = new HashSet<>();
        }

        public Builder add(EmailChange change) {
            if (!canAddMoreItem) {
                return this;
            }

            Set<MessageId> destroyedTemp = new HashSet<>(destroyed);

            Set<MessageId> createdTemp = Sets.difference(
                ImmutableSet.<MessageId>builder()
                    .addAll(created)
                    .addAll(change.getCreated())
                    .build(),
                ImmutableSet.copyOf(change.getDestroyed()));
            Set<MessageId> updatedTemp = Sets.difference(
                ImmutableSet.<MessageId>builder()
                    .addAll(updated)
                    .addAll(ImmutableSet.copyOf(change.getUpdated()))
                    .build(),
                ImmutableSet.copyOf(change.getDestroyed()));
            destroyedTemp.addAll(Sets.difference(
                ImmutableSet.copyOf(change.getDestroyed()),
                created));

            if (createdTemp.size() + updatedTemp.size() + destroyedTemp.size() > limit.getValue()) {
                hasMoreChanges = true;
                canAddMoreItem = false;
                return this;
            }

            state = change.getState();
            created = createdTemp;
            updated = updatedTemp;
            destroyed = destroyedTemp;

            return this;
        }

        public EmailChanges build() {
            return new EmailChanges(state, hasMoreChanges, created, updated, destroyed);
        }
    }

    private State newState;
    private final boolean hasMoreChanges;
    private final Set<MessageId> created;
    private final Set<MessageId> updated;
    private final Set<MessageId> destroyed;

    private EmailChanges(State newState, boolean hasMoreChanges, Set<MessageId> created, Set<MessageId> updated, Set<MessageId> destroyed) {
        this.newState = newState;
        this.hasMoreChanges = hasMoreChanges;
        this.created = created;
        this.updated = updated;
        this.destroyed = destroyed;
    }

    public State getNewState() {
        return newState;
    }

    public boolean hasMoreChanges() {
        return hasMoreChanges;
    }

    public Set<MessageId> getCreated() {
        return created;
    }

    public Set<MessageId> getUpdated() {
        return updated;
    }

    public Set<MessageId> getDestroyed() {
        return destroyed;
    }

    public List<MessageId> getAllChanges() {
        return ImmutableList.<MessageId>builder()
            .addAll(created)
            .addAll(updated)
            .addAll(destroyed)
            .build();
    }
}
