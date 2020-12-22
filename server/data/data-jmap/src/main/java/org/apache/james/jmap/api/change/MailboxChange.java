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
import java.util.stream.Stream;

import javax.inject.Inject;
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
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class MailboxChange {
    @FunctionalInterface
    public interface RequiredAccountId {
        RequiredState accountId(AccountId accountId);
    }

    @FunctionalInterface
    public interface RequiredState {
        RequiredDate state(State state);
    }

    @FunctionalInterface
    public interface RequiredDate {
        RequiredIsCountChange date(ZonedDateTime date);
    }

    @FunctionalInterface
    public interface RequiredIsCountChange {
        Builder isCountChange(boolean isCountChange);
    }

    public static class Builder {
        private final AccountId accountId;
        private final State state;
        private final ZonedDateTime date;
        private boolean delegated;
        private boolean isCountChange;
        private Optional<List<MailboxId>> created;
        private Optional<List<MailboxId>> updated;
        private Optional<List<MailboxId>> destroyed;

        private Builder(AccountId accountId, State state, ZonedDateTime date, boolean isCountChange) {
            Preconditions.checkNotNull(accountId, "'accountId' cannot be null");
            Preconditions.checkNotNull(state, "'state' cannot be null");
            Preconditions.checkNotNull(date, "'date' cannot be null");

            this.accountId = accountId;
            this.state = state;
            this.date = date;
            this.isCountChange = isCountChange;
            this.created = Optional.empty();
            this.updated = Optional.empty();
            this.destroyed = Optional.empty();
        }

        public Builder delegated() {
            this.delegated = true;
            return this;
        }

        public Builder isCountChange(boolean isCountChange) {
            this.isCountChange = isCountChange;
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
            return new MailboxChange(accountId, state, date, delegated, isCountChange, created.orElse(ImmutableList.of()), updated.orElse(ImmutableList.of()), destroyed.orElse(ImmutableList.of()));
        }
    }

    public static RequiredAccountId builder() {
        return accountId -> state -> date -> isCountChange -> new Builder(accountId, state, date, isCountChange);
    }

    public static class Factory {
        private final Clock clock;
        private final MailboxManager mailboxManager;
        private final State.Factory stateFactory;

        @Inject
        public Factory(Clock clock, MailboxManager mailboxManager, State.Factory stateFactory) {
            this.clock = clock;
            this.mailboxManager = mailboxManager;
            this.stateFactory = stateFactory;
        }

        public List<MailboxChange> fromEvent(Event event) {
            ZonedDateTime now = ZonedDateTime.now(clock);
            if (event instanceof MailboxAdded) {
                MailboxAdded mailboxAdded = (MailboxAdded) event;

                return ImmutableList.of(MailboxChange.builder()
                    .accountId(AccountId.fromUsername(mailboxAdded.getUsername()))
                    .state(stateFactory.generate())
                    .date(now)
                    .isCountChange(false)
                    .created(ImmutableList.of(mailboxAdded.getMailboxId()))
                    .build());
            }
            if (event instanceof MailboxRenamed) {
                MailboxRenamed mailboxRenamed = (MailboxRenamed) event;

                MailboxChange ownerChange = MailboxChange.builder()
                    .accountId(AccountId.fromUsername(mailboxRenamed.getUsername()))
                    .state(stateFactory.generate())
                    .date(now)
                    .isCountChange(false)
                    .updated(ImmutableList.of(mailboxRenamed.getMailboxId()))
                    .build();

                Stream<MailboxChange> shareeChanges = getSharees(mailboxRenamed.getNewPath(), mailboxRenamed.getUsername(), mailboxManager)
                    .map(name -> MailboxChange.builder()
                        .accountId(AccountId.fromString(name))
                        .state(stateFactory.generate())
                        .date(now)
                        .isCountChange(false)
                        .updated(ImmutableList.of(mailboxRenamed.getMailboxId()))
                        .delegated()
                        .build());

                return Stream.concat(Stream.of(ownerChange), shareeChanges)
                    .collect(Guavate.toImmutableList());
            }
            if (event instanceof MailboxACLUpdated) {
                MailboxACLUpdated mailboxACLUpdated = (MailboxACLUpdated) event;

                MailboxChange ownerChange = MailboxChange.builder()
                    .accountId(AccountId.fromUsername(mailboxACLUpdated.getUsername()))
                    .state(stateFactory.generate())
                    .date(now)
                    .isCountChange(false)
                    .updated(ImmutableList.of(mailboxACLUpdated.getMailboxId()))
                    .build();

                Stream<MailboxChange> shareeChanges = getSharees(mailboxACLUpdated.getMailboxPath(), mailboxACLUpdated.getUsername(), mailboxManager)
                    .map(name -> MailboxChange.builder()
                        .accountId(AccountId.fromString(name))
                        .state(stateFactory.generate())
                        .date(now)
                        .isCountChange(false)
                        .updated(ImmutableList.of(mailboxACLUpdated.getMailboxId()))
                        .delegated()
                        .build());

                return Stream.concat(Stream.of(ownerChange), shareeChanges)
                    .collect(Guavate.toImmutableList());
            }
            if (event instanceof MailboxDeletion) {
                MailboxDeletion mailboxDeletion = (MailboxDeletion) event;

                MailboxChange ownerChange = MailboxChange.builder()
                    .accountId(AccountId.fromUsername(mailboxDeletion.getUsername()))
                    .state(stateFactory.generate())
                    .date(now)
                    .isCountChange(false)
                    .destroyed(ImmutableList.of(mailboxDeletion.getMailboxId()))
                    .build();

                Stream<MailboxChange> shareeChanges = mailboxDeletion.getMailboxACL()
                    .getEntries().keySet()
                    .stream()
                    .filter(rfc4314Rights -> !rfc4314Rights.isNegative())
                    .filter(rfc4314Rights -> rfc4314Rights.getNameType().equals(MailboxACL.NameType.user))
                    .map(MailboxACL.EntryKey::getName)
                    .map(name -> MailboxChange.builder()
                        .accountId(AccountId.fromString(name))
                        .state(stateFactory.generate())
                        .date(now)
                        .isCountChange(false)
                        .destroyed(ImmutableList.of(mailboxDeletion.getMailboxId()))
                        .delegated()
                        .build());

                return Stream.concat(Stream.of(ownerChange), shareeChanges)
                    .collect(Guavate.toImmutableList());
            }
            if (event instanceof Added) {
                Added messageAdded = (Added) event;

                MailboxChange ownerChange = MailboxChange.builder()
                    .accountId(AccountId.fromUsername(messageAdded.getUsername()))
                    .state(stateFactory.generate())
                    .date(now)
                    .isCountChange(true)
                    .updated(ImmutableList.of(messageAdded.getMailboxId()))
                    .build();

                Stream<MailboxChange> shareeChanges = getSharees(messageAdded.getMailboxPath(), messageAdded.getUsername(), mailboxManager)
                    .map(name -> MailboxChange.builder()
                        .accountId(AccountId.fromString(name))
                        .state(stateFactory.generate())
                        .date(now)
                        .isCountChange(true)
                        .updated(ImmutableList.of(messageAdded.getMailboxId()))
                        .delegated()
                        .build());

                return Stream.concat(Stream.of(ownerChange), shareeChanges)
                    .collect(Guavate.toImmutableList());
            }
            if (event instanceof FlagsUpdated) {
                FlagsUpdated messageFlagUpdated = (FlagsUpdated) event;
                boolean isSeenChanged = messageFlagUpdated.getUpdatedFlags()
                    .stream()
                    .anyMatch(flags -> flags.isChanged(Flags.Flag.SEEN));
                if (isSeenChanged) {
                    MailboxChange ownerChange = MailboxChange.builder()
                        .accountId(AccountId.fromUsername(messageFlagUpdated.getUsername()))
                        .state(stateFactory.generate())
                        .date(now)
                        .isCountChange(true)
                        .updated(ImmutableList.of(messageFlagUpdated.getMailboxId()))
                        .build();

                    Stream<MailboxChange> shareeChanges = getSharees(messageFlagUpdated.getMailboxPath(), messageFlagUpdated.getUsername(), mailboxManager)
                        .map(name -> MailboxChange.builder()
                            .accountId(AccountId.fromString(name))
                            .state(stateFactory.generate())
                            .date(now)
                            .isCountChange(true)
                            .updated(ImmutableList.of(messageFlagUpdated.getMailboxId()))
                            .delegated()
                            .build());

                    return Stream.concat(Stream.of(ownerChange), shareeChanges)
                        .collect(Guavate.toImmutableList());
                }
            }
            if (event instanceof Expunged) {
                Expunged expunged = (Expunged) event;
                MailboxChange ownerChange = MailboxChange.builder()
                    .accountId(AccountId.fromUsername(expunged.getUsername()))
                    .state(stateFactory.generate())
                    .date(now)
                    .isCountChange(true)
                    .updated(ImmutableList.of(expunged.getMailboxId()))
                    .build();

                Stream<MailboxChange> shareeChanges = getSharees(expunged.getMailboxPath(), expunged.getUsername(), mailboxManager)
                    .map(name -> MailboxChange.builder()
                        .accountId(AccountId.fromString(name))
                        .state(stateFactory.generate())
                        .date(now)
                        .isCountChange(true)
                        .updated(ImmutableList.of(expunged.getMailboxId()))
                        .delegated()
                        .build());

                return Stream.concat(Stream.of(ownerChange), shareeChanges)
                    .collect(Guavate.toImmutableList());
            }

            return ImmutableList.of();
        }
    }

    private static Stream<String> getSharees(MailboxPath path, Username username, MailboxManager mailboxManager) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        try {
            MailboxACL mailboxACL = mailboxManager.listRights(path, mailboxSession);
            return mailboxACL.getEntries().keySet()
                .stream()
                .filter(rfc4314Rights -> !rfc4314Rights.isNegative())
                .filter(rfc4314Rights -> rfc4314Rights.getNameType().equals(MailboxACL.NameType.user))
                .map(MailboxACL.EntryKey::getName);
        } catch (MailboxException e) {
            return Stream.of();
        }
    }

    private final AccountId accountId;
    private final State state;
    private final ZonedDateTime date;
    private final boolean delegated;
    private final boolean isCountChange;
    private final List<MailboxId> created;
    private final List<MailboxId> updated;
    private final List<MailboxId> destroyed;

    private MailboxChange(AccountId accountId, State state, ZonedDateTime date, boolean delegated, boolean isCountChange, List<MailboxId> created, List<MailboxId> updated, List<MailboxId> destroyed) {
        this.accountId = accountId;
        this.state = state;
        this.date = date;
        this.delegated = delegated;
        this.isCountChange = isCountChange;
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

    public boolean isCountChange() {
        return isCountChange;
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

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MailboxChange) {
            MailboxChange that = (MailboxChange) o;
            return Objects.equals(accountId, that.accountId)
                && Objects.equals(state, that.state)
                && Objects.equals(date, that.date)
                && Objects.equals(delegated, that.delegated)
                && Objects.equals(created, that.created)
                && Objects.equals(updated, that.updated)
                && Objects.equals(destroyed, that.destroyed);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(accountId, state, date, delegated, created, updated, destroyed);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("accountId", accountId)
            .add("state", state)
            .add("date", date)
            .add("isDelegated", delegated)
            .add("created", created)
            .add("updated", updated)
            .add("destroyed", destroyed)
            .toString();
    }
}
