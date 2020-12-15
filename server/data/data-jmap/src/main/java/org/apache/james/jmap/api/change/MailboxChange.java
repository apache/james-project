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

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import javax.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.MailboxListener.Added;
import org.apache.james.mailbox.events.MailboxListener.Expunged;
import org.apache.james.mailbox.events.MailboxListener.FlagsUpdated;
import org.apache.james.mailbox.events.MailboxListener.MailboxACLUpdated;
import org.apache.james.mailbox.events.MailboxListener.MailboxAdded;
import org.apache.james.mailbox.events.MailboxListener.MailboxDeletion;
import org.apache.james.mailbox.events.MailboxListener.MailboxRenamed;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class MailboxChange {

    public static class State {
        public static State INITIAL = of(UUID.fromString("2c9f1b12-b35a-43e6-9af2-0106fb53a943"));

        public static State of(UUID value) {
            return new State(value);
        }

        private final UUID value;

        private State(UUID value) {
            this.value = value;
        }

        public UUID getValue() {
            return value;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof State) {
                State state = (State) o;

                return Objects.equals(this.value, state.value);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(value);
        }
    }

    public static class Limit {

        public static Limit of(int value) {
            return new Limit(value);
        }

        private final int value;

        private Limit(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    @FunctionalInterface
    interface RequiredAccountId {
        RequiredState accountId(AccountId accountId);
    }

    @FunctionalInterface
    interface RequiredState {
        RequiredDate state(State state);
    }

    @FunctionalInterface
    interface RequiredDate {
        Builder date(ZonedDateTime date);
    }

    public static class Builder {
        private final AccountId accountId;
        private final State state;
        private final ZonedDateTime date;
        private boolean delegated;
        private Optional<List<MailboxId>> created;
        private Optional<List<MailboxId>> updated;
        private Optional<List<MailboxId>> destroyed;

        private Builder(AccountId accountId, State state, ZonedDateTime date) {
            this.accountId = accountId;
            this.state = state;
            this.date = date;
            this.created = Optional.empty();
            this.updated = Optional.empty();
            this.destroyed = Optional.empty();
        }

        public Builder delegated() {
            this.delegated = true;
            return this;
        }

        public Builder created(List<MailboxId> created) {
            this.created = Optional.of(created);
            return this;
        }

        public Builder updated(List<MailboxId> updated) {
            this.updated = Optional.of(updated);
            return this;
        }

        public Builder destroyed(List<MailboxId> destroyed) {
            this.destroyed = Optional.of(destroyed);
            return this;
        }

        public MailboxChange build() {
            return new MailboxChange(accountId, state, date, delegated, created.orElse(ImmutableList.of()), updated.orElse(ImmutableList.of()), destroyed.orElse(ImmutableList.of()));
        }
    }

    public static RequiredAccountId builder() {
        return accountId -> state -> date -> new Builder(accountId, state, date);
    }

    public static Builder created(AccountId accountId, State state, ZonedDateTime date, List<MailboxId> created) {
        return MailboxChange.builder()
            .accountId(accountId)
            .state(state)
            .date(date)
            .created(created);
    }

    public static Builder updated(AccountId accountId, State state, ZonedDateTime date, List<MailboxId> updated) {
        return MailboxChange.builder()
            .accountId(accountId)
            .state(state)
            .date(date)
            .updated(updated);
    }

    public static Builder destroyed(AccountId accountId, State state, ZonedDateTime date, List<MailboxId> destroyed) {
        return MailboxChange.builder()
            .accountId(accountId)
            .state(state)
            .date(date)
            .destroyed(destroyed);
    }

    public static Optional<List<MailboxChange>> fromEvent(Event event, MailboxManager mailboxManager) {
        ZonedDateTime now = ZonedDateTime.now(Clock.systemUTC());
        if (event instanceof MailboxAdded) {
            MailboxAdded mailboxAdded = (MailboxAdded) event;
            return Optional.of(ImmutableList.of(MailboxChange.created(AccountId.fromUsername(mailboxAdded.getUsername()), State.of(UUID.randomUUID()), now, ImmutableList.of(mailboxAdded.getMailboxId())).build()));
        }
        if (event instanceof MailboxRenamed) {
            MailboxRenamed mailboxRenamed = (MailboxRenamed) event;

            MailboxChange ownerChange = MailboxChange.updated(AccountId.fromUsername(mailboxRenamed.getUsername()), State.of(UUID.randomUUID()), now, ImmutableList.of(mailboxRenamed.getMailboxId())).build();
            Stream<MailboxChange> shareeChanges = getSharees(mailboxRenamed.getNewPath(), mailboxRenamed.getUsername(), mailboxManager)
                .map(name -> MailboxChange.updated(AccountId.fromString(name), State.of(UUID.randomUUID()), now, ImmutableList.of(mailboxRenamed.getMailboxId()))
                    .delegated()
                    .build());

            List<MailboxChange> blah = Stream.concat(Stream.of(ownerChange), shareeChanges).collect(Guavate.toImmutableList());
            return Optional.of(blah);
        }
        if (event instanceof MailboxACLUpdated) {
            MailboxACLUpdated mailboxACLUpdated = (MailboxACLUpdated) event;

            MailboxChange ownerChange = MailboxChange.updated(AccountId.fromUsername(mailboxACLUpdated.getUsername()), State.of(UUID.randomUUID()), now, ImmutableList.of(mailboxACLUpdated.getMailboxId())).build();
            Stream<MailboxChange> shareeChanges = getSharees(mailboxACLUpdated.getMailboxPath(), mailboxACLUpdated.getUsername(), mailboxManager)
                .map(name -> MailboxChange.updated(AccountId.fromString(name), State.of(UUID.randomUUID()), now, ImmutableList.of(mailboxACLUpdated.getMailboxId()))
                    .delegated()
                    .build());

            return Optional.of(
                Stream.concat(Stream.of(ownerChange), shareeChanges)
                    .collect(Guavate.toImmutableList()));
        }
        if (event instanceof MailboxDeletion) {
            MailboxDeletion mailboxDeletion = (MailboxDeletion) event;
            return Optional.of(ImmutableList.of(MailboxChange.destroyed(AccountId.fromUsername(mailboxDeletion.getUsername()), State.of(UUID.randomUUID()), now, ImmutableList.of(mailboxDeletion.getMailboxId())).build()));
        }
        if (event instanceof Added) {
            Added messageAdded = (Added) event;

            MailboxChange ownerChange = MailboxChange.updated(AccountId.fromUsername(messageAdded.getUsername()), State.of(UUID.randomUUID()), now, ImmutableList.of(messageAdded.getMailboxId())).build();
            Stream<MailboxChange> shareeChanges = getSharees(messageAdded.getMailboxPath(), messageAdded.getUsername(), mailboxManager)
                .map(name -> MailboxChange.updated(AccountId.fromString(name), State.of(UUID.randomUUID()), now, ImmutableList.of(messageAdded.getMailboxId()))
                    .delegated()
                    .build());

            return Optional.of(
                Stream.concat(Stream.of(ownerChange), shareeChanges)
                    .collect(Guavate.toImmutableList()));
        }
        if (event instanceof FlagsUpdated) {
            FlagsUpdated messageFlagUpdated = (FlagsUpdated) event;
            boolean isSeenChanged = messageFlagUpdated.getUpdatedFlags()
                .stream()
                .anyMatch(flags -> flags.isChanged(Flags.Flag.SEEN));
            if (isSeenChanged) {

                MailboxChange ownerChange = MailboxChange.updated(AccountId.fromUsername(messageFlagUpdated.getUsername()), State.of(UUID.randomUUID()), now, ImmutableList.of(messageFlagUpdated.getMailboxId())).build();
                Stream<MailboxChange> shareeChanges = getSharees(messageFlagUpdated.getMailboxPath(), messageFlagUpdated.getUsername(), mailboxManager)
                    .map(name -> MailboxChange.updated(AccountId.fromString(name), State.of(UUID.randomUUID()), now, ImmutableList.of(messageFlagUpdated.getMailboxId()))
                        .delegated()
                        .build());

                return Optional.of(
                    Stream.concat(Stream.of(ownerChange), shareeChanges)
                        .collect(Guavate.toImmutableList()));
            }
        }
        if (event instanceof Expunged) {
            Expunged expunged = (Expunged) event;

            MailboxChange ownerChange = MailboxChange.updated(AccountId.fromUsername(expunged.getUsername()), State.of(UUID.randomUUID()), now, ImmutableList.of(expunged.getMailboxId())).build();
            Stream<MailboxChange> shareeChanges = getSharees(expunged.getMailboxPath(), expunged.getUsername(), mailboxManager)
                .map(name -> MailboxChange.updated(AccountId.fromString(name), State.of(UUID.randomUUID()), now, ImmutableList.of(expunged.getMailboxId()))
                    .delegated()
                    .build());

            return Optional.of(
                Stream.concat(Stream.of(ownerChange), shareeChanges)
                    .collect(Guavate.toImmutableList()));
        }

        return Optional.empty();
    }

    private static Stream<String> getSharees(MailboxPath path, Username username, MailboxManager mailboxManager) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        try {
            MailboxACL mailboxACL = mailboxManager.listRights(path, mailboxSession);
            return mailboxACL.getEntries().keySet()
                .stream()
                .filter(rfc4314Rights -> !rfc4314Rights.isNegative())
                .map(MailboxACL.EntryKey::getName);
        } catch (MailboxException e) {
            return Stream.of();
        }
    }

    private final AccountId accountId;
    private final State state;
    private final ZonedDateTime date;
    private final boolean delegated;
    private final List<MailboxId> created;
    private final List<MailboxId> updated;
    private final List<MailboxId> destroyed;

    private MailboxChange(AccountId accountId, State state, ZonedDateTime date, boolean delegated, List<MailboxId> created, List<MailboxId> updated, List<MailboxId> destroyed) {
        this.accountId = accountId;
        this.state = state;
        this.date = date;
        this.delegated = delegated;
        this.created = created;
        this.updated = updated;
        this.destroyed = destroyed;
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

    public boolean isDelegated() {
        return delegated;
    }

    public List<MailboxId> getCreated() {
        return created;
    }

    public List<MailboxId> getUpdated() {
        return updated;
    }

    public List<MailboxId> getDestroyed() {
        return destroyed;
    }
}
