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
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class EmailChange {
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
            RequireIsDelegated date(ZonedDateTime date);
        }

        @FunctionalInterface
        public interface RequireIsDelegated {
            Builder isDelegated(boolean isDelegated);
        }

        private final AccountId accountId;
        private final State state;
        private final ZonedDateTime date;
        private final boolean isDelegated;
        private final ImmutableList.Builder<MessageId> created;
        private final ImmutableList.Builder<MessageId> updated;
        private final ImmutableList.Builder<MessageId> destroyed;

        private Builder(AccountId accountId, State state, ZonedDateTime date, boolean isDelegated) {
            Preconditions.checkNotNull(accountId, "'accountId' should not be null");
            Preconditions.checkNotNull(state, "'state' should not be null");
            Preconditions.checkNotNull(date, "'date' should not be null");

            this.accountId = accountId;
            this.state = state;
            this.date = date;
            this.isDelegated = isDelegated;
            this.destroyed = ImmutableList.builder();
            this.updated = ImmutableList.builder();
            this.created = ImmutableList.builder();
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

        public EmailChange build() {
            return new EmailChange(accountId, state, date, isDelegated, created.build(), updated.build(), destroyed.build());
        }
    }

    public static Builder.RequireAccountId builder() {
        return accountId -> state -> date -> isDelegated -> new Builder(accountId, state, date, isDelegated);
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

        public List<EmailChange> fromEvent(Event event) {
            ZonedDateTime now = ZonedDateTime.now(clock);

            if (event instanceof MailboxListener.Added) {
                MailboxListener.Added messageAdded = (MailboxListener.Added) event;

                EmailChange ownerChange = EmailChange.builder()
                    .accountId(AccountId.fromUsername(messageAdded.getUsername()))
                    .state(stateFactory.generate())
                    .date(now)
                    .isDelegated(false)
                    .created(messageAdded.getMessageIds())
                    .build();

                Stream<EmailChange> shareeChanges = getSharees(messageAdded.getMailboxPath(), messageAdded.getUsername(), mailboxManager)
                    .map(name -> EmailChange.builder()
                        .accountId(AccountId.fromString(name))
                        .state(stateFactory.generate())
                        .date(now)
                        .isDelegated(true)
                        .created(messageAdded.getMessageIds())
                        .build());

                return Stream.concat(Stream.of(ownerChange), shareeChanges)
                    .collect(Guavate.toImmutableList());
            }
            if (event instanceof MailboxListener.FlagsUpdated) {
                MailboxListener.FlagsUpdated messageFlagUpdated = (MailboxListener.FlagsUpdated) event;

                EmailChange ownerChange = EmailChange.builder()
                    .accountId(AccountId.fromUsername(messageFlagUpdated.getUsername()))
                    .state(stateFactory.generate())
                    .date(now)
                    .isDelegated(false)
                    .updated(messageFlagUpdated.getMessageIds())
                    .build();

                Stream<EmailChange> shareeChanges = getSharees(messageFlagUpdated.getMailboxPath(), messageFlagUpdated.getUsername(), mailboxManager)
                    .map(name -> EmailChange.builder()
                        .accountId(AccountId.fromString(name))
                        .state(stateFactory.generate())
                        .date(now)
                        .isDelegated(true)
                        .updated(messageFlagUpdated.getMessageIds())
                        .build());

                return Stream.concat(Stream.of(ownerChange), shareeChanges)
                    .collect(Guavate.toImmutableList());
            }
            if (event instanceof MailboxListener.Expunged) {
                MailboxListener.Expunged expunged = (MailboxListener.Expunged) event;

                EmailChange ownerChange = EmailChange.builder()
                    .accountId(AccountId.fromUsername(expunged.getUsername()))
                    .state(stateFactory.generate())
                    .date(now)
                    .isDelegated(false)
                    .destroyed(expunged.getMessageIds())
                    .build();

                Stream<EmailChange> shareeChanges = getSharees(expunged.getMailboxPath(), expunged.getUsername(), mailboxManager)
                    .map(name -> EmailChange.builder()
                        .accountId(AccountId.fromString(name))
                        .state(stateFactory.generate())
                        .date(now)
                        .isDelegated(true)
                        .destroyed(expunged.getMessageIds())
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
    private final boolean isDelegated;
    private final ImmutableList<MessageId> created;
    private final ImmutableList<MessageId> updated;
    private final ImmutableList<MessageId> destroyed;

    private EmailChange(AccountId accountId, State state, ZonedDateTime date, boolean isDelegated, ImmutableList<MessageId> created, ImmutableList<MessageId> updated, ImmutableList<MessageId> destroyed) {
        this.accountId = accountId;
        this.state = state;
        this.date = date;
        this.isDelegated = isDelegated;
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

    public List<MessageId> getCreated() {
        return created;
    }

    public List<MessageId> getUpdated() {
        return updated;
    }

    public List<MessageId> getDestroyed() {
        return destroyed;
    }

    public boolean isDelegated() {
        return isDelegated;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof EmailChange) {
            EmailChange that = (EmailChange) o;
            return Objects.equals(accountId, that.accountId)
                && Objects.equals(state, that.state)
                && Objects.equals(date, that.date)
                && Objects.equals(isDelegated, that.isDelegated)
                && Objects.equals(created, that.created)
                && Objects.equals(updated, that.updated)
                && Objects.equals(destroyed, that.destroyed);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(accountId, state, date, isDelegated, created, updated, destroyed);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("accountId", accountId)
            .add("state", state)
            .add("date", date)
            .add("isDelegated", isDelegated)
            .add("created", created)
            .add("updated", updated)
            .add("destroyed", destroyed)
            .toString();
    }
}
