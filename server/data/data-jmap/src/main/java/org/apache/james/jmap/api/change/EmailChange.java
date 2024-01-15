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

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.mailbox.model.MessageId;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class EmailChange implements JmapChange {
    public static class Builder {
        @FunctionalInterface
        public interface RequireAccountId {
            RequireState accountId(AccountId accountId);
        }

        @FunctionalInterface
        public interface RequireState {
            RequireDate state(State state);
        }

        @FunctionalInterface
        public interface RequireDate {
            RequireIsShared date(ZonedDateTime date);
        }

        @FunctionalInterface
        public interface RequireIsShared {
            Builder isShared(boolean isShared);
        }

        private final AccountId accountId;
        private final State state;
        private final ZonedDateTime date;
        private final boolean shared;
        private final ImmutableList.Builder<MessageId> created;
        private final ImmutableList.Builder<MessageId> updated;
        private final ImmutableList.Builder<MessageId> destroyed;
        private Optional<Boolean> isDelivery;

        private Builder(AccountId accountId, State state, ZonedDateTime date, boolean shared) {
            Preconditions.checkNotNull(accountId, "'accountId' should not be null");
            Preconditions.checkNotNull(state, "'state' should not be null");
            Preconditions.checkNotNull(date, "'date' should not be null");

            this.accountId = accountId;
            this.state = state;
            this.date = date;
            this.shared = shared;
            this.destroyed = ImmutableList.builder();
            this.updated = ImmutableList.builder();
            this.created = ImmutableList.builder();
            this.isDelivery = Optional.empty();
        }

        public Builder updated(MessageId... messageId) {
            updated.add(messageId);
            return this;
        }

        public Builder destroyed(MessageId... messageId) {
            destroyed.add(messageId);
            return this;
        }

        public Builder created(MessageId... messageId) {
            created.add(messageId);
            return this;
        }

        public Builder created(Collection<MessageId> messageIds) {
            created.addAll(messageIds);
            return this;
        }

        public Builder destroyed(Collection<MessageId> messageIds) {
            destroyed.addAll(messageIds);
            return this;
        }

        public Builder updated(Collection<MessageId> messageIds) {
            updated.addAll(messageIds);
            return this;
        }

        public Builder isDelivery(boolean isDelivery) {
            this.isDelivery = Optional.of(isDelivery);
            return this;
        }

        public EmailChange build() {
            return new EmailChange(accountId, state, date, shared, created.build(), updated.build(), destroyed.build(), isDelivery.orElse(false));
        }
    }

    public static Builder.RequireAccountId builder() {
        return accountId -> state -> date -> isShared -> new Builder(accountId, state, date, isShared);
    }

    private final AccountId accountId;
    private final State state;
    private final ZonedDateTime date;
    private final boolean shared;
    private final ImmutableList<MessageId> created;
    private final ImmutableList<MessageId> updated;
    private final ImmutableList<MessageId> destroyed;
    private final boolean isDelivery;

    private EmailChange(AccountId accountId, State state, ZonedDateTime date, boolean shared, ImmutableList<MessageId> created, ImmutableList<MessageId> updated, ImmutableList<MessageId> destroyed, boolean isDelivery) {
        this.accountId = accountId;
        this.state = state;
        this.date = date;
        this.shared = shared;
        this.created = created;
        this.updated = updated;
        this.destroyed = destroyed;
        this.isDelivery = isDelivery;
    }

    public AccountId getAccountId() {
        return accountId;
    }

    public State getState() {
        return state;
    }

    public ZonedDateTime getDate() {
        return date;
    }

    public List<MessageId> getCreated() {
        return created;
    }

    public List<MessageId> getUpdated() {
        return updated;
    }

    public List<MessageId> getDestroyed() {
        return destroyed;
    }

    public boolean isShared() {
        return shared;
    }

    public boolean isDelivery() {
        return isDelivery;
    }

    public EmailChange forSharee(AccountId accountId, Supplier<State> state) {
        return EmailChange.builder()
            .accountId(accountId)
            .state(state.get())
            .date(date)
            .isShared(true)
            .created(created)
            .updated(updated)
            .destroyed(destroyed)
            .build();
    }

    @Override
    public boolean isNoop() {
        return created.isEmpty()
            && updated.isEmpty()
            && destroyed.isEmpty();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof EmailChange) {
            EmailChange that = (EmailChange) o;
            return Objects.equals(accountId, that.accountId)
                && Objects.equals(state, that.state)
                && Objects.equals(date, that.date)
                && Objects.equals(shared, that.shared)
                && Objects.equals(created, that.created)
                && Objects.equals(updated, that.updated)
                && Objects.equals(destroyed, that.destroyed)
                && Objects.equals(isDelivery, that.isDelivery);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(accountId, state, date, shared, created, updated, destroyed, isDelivery);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("accountId", accountId)
            .add("state", state)
            .add("date", date)
            .add("isShared", shared)
            .add("created", created)
            .add("updated", updated)
            .add("destroyed", destroyed)
            .add("isDelivery", isDelivery)
            .toString();
    }
}
